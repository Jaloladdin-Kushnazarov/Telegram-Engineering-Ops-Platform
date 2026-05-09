package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 158 — real Telegram Bot API outbound gateway implementatsiyasi.
 *
 * <p>Bu klass {@link StubTelegramOutboundGateway} o'rnini bosadi
 * (token mavjud bo'lganda — {@link TelegramOutboundGatewayConfiguration}
 * orqali). HTTP {@code POST /bot{token}/sendMessage} chaqiruvini amalga
 * oshiradi.</p>
 *
 * <p><strong>Xulq:</strong></p>
 * <ul>
 *   <li>{@link #execute(TelegramSendMessageRequest)} — yagona ishlatilayotgan
 *       yo'l. {@code targetChatBindingId}'ni Telegram {@code chat_id} (Long)
 *       ga {@link TenantConfigQueryService#findChatBindingById(java.util.UUID, java.util.UUID)}
 *       orqali resolve qiladi (cross-module public API access — modular
 *       monolith boundary saqlanadi).</li>
 *   <li>{@link #dispatch(TelegramDeliveryCommand)} — eski yo'l. Defensiv
 *       failure qaytaradi: production'da {@link TelegramOutboundDispatchService}
 *       allaqachon faqat {@code execute(request)} ni chaqiradi.</li>
 * </ul>
 *
 * <p><strong>Bot API payload (omit-null'lar bilan):</strong></p>
 * <pre>
 * {
 *   "chat_id": &lt;long&gt;,
 *   "message_thread_id": &lt;long, optional&gt;,
 *   "text": "...",
 *   "reply_markup": {  // faqat keyboard non-empty bo'lganda
 *     "inline_keyboard": [[{"text":"...","callback_data":"..."}]]
 *   }
 * }
 * </pre>
 * <p>{@code parse_mode} ataylab kiritilmaydi — Phase 158 plain text
 * outbound. MarkdownV2 / HTML rendering keyingi phase.</p>
 *
 * <p><strong>Error mapping (mavjud {@link TelegramGatewayError} enum):</strong></p>
 * <ul>
 *   <li>HTTP 200 + {@code ok=true} + {@code result.message_id} → SUCCESS(messageId)</li>
 *   <li>HTTP 200 + {@code ok=false} → REJECTED({@link TelegramGatewayError#INVALID_REQUEST})</li>
 *   <li>HTTP 400/401/403/404 → REJECTED(INVALID_REQUEST)</li>
 *   <li>HTTP 429 → FAILED({@link TelegramGatewayError#RATE_LIMIT})</li>
 *   <li>HTTP 5xx → FAILED({@link TelegramGatewayError#NETWORK_ERROR})</li>
 *   <li>{@link ResourceAccessException} (timeout / IO) → FAILED(NETWORK_ERROR)</li>
 *   <li>Boshqa kutilmagan exception → FAILED({@link TelegramGatewayError#UNKNOWN_ERROR})</li>
 * </ul>
 *
 * <p>Retry yo'q — Phase 158 single attempt. Append-only delivery attempt
 * persistence Phase 158 ham retry mantig'iga muhtoj emas (har bir
 * attempt alohida row).</p>
 *
 * <p><strong>Token xavfsizligi:</strong></p>
 * <ul>
 *   <li>Token logga yozilmaydi (URL log qilinmaydi).</li>
 *   <li>Exception/error message'larida token sub-string'i bo'lsa
 *       {@link #sanitize(String)} orqali {@code ***} bilan almashtiriladi.</li>
 *   <li>Token persisted {@code failure_reason} maydoniga ham
 *       sanitize qilinib o'tadi.</li>
 * </ul>
 */
public class HttpTelegramOutboundGateway implements TelegramOutboundGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpTelegramOutboundGateway.class);
    private static final String TOKEN_REDACTED = "***";

    private final TelegramProperties properties;
    private final RestClient restClient;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final ObjectMapper objectMapper;

    public HttpTelegramOutboundGateway(TelegramProperties properties,
                                        RestClient restClient,
                                        TenantConfigQueryService tenantConfigQueryService,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Eski {@link TelegramOutboundGateway#dispatch(TelegramDeliveryCommand)}
     * yo'li. Production'da {@link TelegramOutboundDispatchService} faqat
     * {@code execute(request)} ni chaqiradi — bu metod defensiv failure
     * qaytaradi va silent misuse'ni oldini oladi.
     */
    @Override
    public TelegramDeliveryResult dispatch(TelegramDeliveryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TelegramDeliveryCommand null bo'lishi mumkin emas");
        }
        return TelegramDeliveryResult.failed(command,
                "DISPATCH_NOT_SUPPORTED",
                "HttpTelegramOutboundGateway.dispatch(command) eski yo'l — "
                        + "TelegramOutboundDispatchService orqali execute(request) ishlatilsin");
    }

    @Override
    public TelegramGatewayResult execute(TelegramSendMessageRequest request) {
        if (request == null) {
            return TelegramGatewayResult.failed(TelegramGatewayError.UNKNOWN_ERROR,
                    "TelegramSendMessageRequest null");
        }

        // 1. Tenant-scoped chat binding lookup → real Telegram chat_id.
        Optional<TelegramChatBinding> bindingOpt = tenantConfigQueryService
                .findChatBindingById(request.getTenantId(), request.getTargetChatBindingId());
        if (bindingOpt.isEmpty()) {
            return TelegramGatewayResult.rejected(TelegramGatewayError.INVALID_REQUEST,
                    "Telegram chat binding topilmadi: targetChatBindingId="
                            + request.getTargetChatBindingId());
        }
        long chatId = bindingOpt.get().getChatId();

        // 2. Build payload (omit-null + omit parse_mode).
        Map<String, Object> payload = buildPayload(chatId, request);

        // 3. POST {baseUrl}/bot{token}/sendMessage. URL token o'z ichiga
        //    oladi — log qilinmaydi.
        String url = buildSendMessageUrl();

        try {
            String body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return mapSuccessfulResponse(body);
        } catch (HttpClientErrorException ex) {
            return mapClientError(ex);
        } catch (HttpServerErrorException ex) {
            return TelegramGatewayResult.failed(TelegramGatewayError.NETWORK_ERROR,
                    "Telegram server error: HTTP " + ex.getStatusCode().value());
        } catch (RestClientResponseException ex) {
            // Boshqa noma'lum HTTP holat — ehtiyot tomon classify qilamiz.
            int status = ex.getStatusCode().value();
            if (status == 429) {
                return TelegramGatewayResult.failed(TelegramGatewayError.RATE_LIMIT,
                        "Telegram rate limit: HTTP 429");
            }
            if (status >= 500) {
                return TelegramGatewayResult.failed(TelegramGatewayError.NETWORK_ERROR,
                        "Telegram server error: HTTP " + status);
            }
            return TelegramGatewayResult.rejected(TelegramGatewayError.INVALID_REQUEST,
                    "Telegram client error: HTTP " + status);
        } catch (ResourceAccessException ex) {
            return TelegramGatewayResult.failed(TelegramGatewayError.NETWORK_ERROR,
                    "Telegram network error: " + sanitize(ex.getMessage()));
        } catch (RuntimeException ex) {
            // Sanitize log message — token sub-string'i defensiv ravishda
            // olib tashlanadi.
            log.warn("Telegram gateway unexpected error tenantId={} workItemId={}: {}",
                    request.getTenantId(), request.getWorkItemId(),
                    sanitize(ex.getMessage()));
            return TelegramGatewayResult.failed(TelegramGatewayError.UNKNOWN_ERROR,
                    "Telegram unexpected: " + sanitize(ex.getMessage()));
        }
    }

    private Map<String, Object> buildPayload(long chatId, TelegramSendMessageRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        if (request.getTargetTopicId() != null) {
            payload.put("message_thread_id", request.getTargetTopicId());
        }
        payload.put("text", request.getText());
        if (request.hasKeyboard()) {
            List<List<Map<String, String>>> inlineKeyboard = new ArrayList<>();
            for (TelegramInlineKeyboardRow row : request.getKeyboard()) {
                List<Map<String, String>> rowList = new ArrayList<>();
                for (TelegramInlineKeyboardButton btn : row.getButtons()) {
                    Map<String, String> btnMap = new LinkedHashMap<>();
                    btnMap.put("text", btn.getText());
                    btnMap.put("callback_data", btn.getCallbackData());
                    rowList.add(btnMap);
                }
                inlineKeyboard.add(rowList);
            }
            Map<String, Object> replyMarkup = new LinkedHashMap<>();
            replyMarkup.put("inline_keyboard", inlineKeyboard);
            payload.put("reply_markup", replyMarkup);
        }
        return payload;
    }

    private String buildSendMessageUrl() {
        String base = properties.getApiBaseUrl();
        if (base == null) {
            base = "";
        }
        // Trim trailing slash agar mavjud bo'lsa.
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/bot" + properties.getBotToken() + "/sendMessage";
    }

    private TelegramGatewayResult mapSuccessfulResponse(String body) {
        if (body == null || body.isBlank()) {
            return TelegramGatewayResult.failed(TelegramGatewayError.UNKNOWN_ERROR,
                    "Telegram empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            boolean ok = root.path("ok").asBoolean(false);
            if (ok) {
                JsonNode result = root.path("result");
                Long messageId = null;
                if (result.has("message_id")) {
                    messageId = result.path("message_id").asLong();
                }
                return TelegramGatewayResult.success(messageId);
            }
            int errorCode = root.path("error_code").asInt(0);
            String description = root.path("description").asText("rejected");
            return TelegramGatewayResult.rejected(TelegramGatewayError.INVALID_REQUEST,
                    "Telegram error_code=" + errorCode + " description=" + sanitize(description));
        } catch (RuntimeException ex) {
            return TelegramGatewayResult.failed(TelegramGatewayError.UNKNOWN_ERROR,
                    "Telegram response handling failed: " + sanitize(ex.getMessage()));
        } catch (Exception ex) {
            // Jackson IOException / JsonProcessingException — checked.
            return TelegramGatewayResult.failed(TelegramGatewayError.UNKNOWN_ERROR,
                    "Telegram response parse failed: " + sanitize(ex.getMessage()));
        }
    }

    private TelegramGatewayResult mapClientError(HttpClientErrorException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return TelegramGatewayResult.failed(TelegramGatewayError.RATE_LIMIT,
                    "Telegram rate limit: HTTP 429");
        }
        // 400/401/403/404 va boshqa 4xx → permanent reject.
        return TelegramGatewayResult.rejected(TelegramGatewayError.INVALID_REQUEST,
                "Telegram client error: HTTP " + status);
    }

    /**
     * Bot token sub-string'ini berilgan matn ichida {@value #TOKEN_REDACTED}
     * bilan almashtiradi. Token bo'sh bo'lsa hech narsa qilmaydi.
     */
    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String token = properties.getBotToken();
        if (StringUtils.hasText(token)) {
            return input.replace(token, TOKEN_REDACTED);
        }
        return input;
    }
}
