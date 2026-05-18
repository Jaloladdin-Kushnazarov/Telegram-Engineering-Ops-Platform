package com.engops.platform.telegram;

import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Phase 158 — {@link HttpTelegramOutboundGateway} unit testlari
 * ({@link MockRestServiceServer} bilan, real network'siz).
 *
 * <p>Qoplamalar:</p>
 * <ul>
 *   <li>Happy path: HTTP 200 + ok=true → SUCCESS(messageId)</li>
 *   <li>Telegram-level reject: HTTP 200 + ok=false → REJECTED(INVALID_REQUEST)</li>
 *   <li>HTTP 400/403 → REJECTED(INVALID_REQUEST)</li>
 *   <li>HTTP 429 → FAILED(RATE_LIMIT)</li>
 *   <li>HTTP 500 → FAILED(NETWORK_ERROR)</li>
 *   <li>Network IO error → FAILED(NETWORK_ERROR)</li>
 *   <li>Token redaction: failure message ichida bot token sub-string'i bo'lmaydi</li>
 *   <li>Payload mapping: chat_id, message_thread_id, text, inline_keyboard;
 *       parse_mode YO'Q</li>
 *   <li>Chat binding resolution: gateway TenantConfigQueryService'ni
 *       (tenantId, chatBindingId) bilan chaqiradi va resolved chatId'ni
 *       payload'da ishlatadi</li>
 *   <li>Chat binding topilmadi → REJECTED, HTTP chaqiruvi yo'q</li>
 *   <li>Legacy dispatch(command) defensiv failure qaytaradi</li>
 * </ul>
 */
class HttpTelegramOutboundGatewayTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CHAT_BINDING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final long CHAT_ID = -1001234567890L;
    private static final long TOPIC_ID = 42L;
    private static final String TEST_TOKEN = "1234567890:TEST_BOT_TOKEN_NOT_REAL_aaa";
    private static final String BASE_URL = "https://api.telegram.example";
    private static final String EXPECTED_URL = BASE_URL + "/bot" + TEST_TOKEN + "/sendMessage";

    private TelegramProperties properties;
    private MockRestServiceServer server;
    private TenantConfigQueryService tenantConfigQueryService;
    private HttpTelegramOutboundGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new TelegramProperties();
        properties.setBotToken(TEST_TOKEN);
        properties.setApiBaseUrl(BASE_URL);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        tenantConfigQueryService = mock(TenantConfigQueryService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        gateway = new HttpTelegramOutboundGateway(
                properties, restClient, tenantConfigQueryService, objectMapper);

        TelegramChatBinding binding = mock(TelegramChatBinding.class);
        when(binding.getChatId()).thenReturn(CHAT_ID);
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, CHAT_BINDING_ID))
                .thenReturn(Optional.of(binding));
    }

    private TelegramSendMessageRequest sampleRequest(boolean withKeyboard) {
        List<TelegramInlineKeyboardRow> keyboard = withKeyboard
                ? List.of(new TelegramInlineKeyboardRow(List.of(
                        new TelegramInlineKeyboardButton("Approve", "do:approve"),
                        new TelegramInlineKeyboardButton("Reject", "do:reject"))))
                : List.of();
        return new TelegramSendMessageRequest(
                TENANT_ID, WORK_ITEM_ID, CHAT_BINDING_ID, TOPIC_ID,
                "Bug | BUG-1\nLogin xato", keyboard);
    }

    // ========== Happy path ==========

    @Test
    void okTrueResponseMapsToSuccessWithMessageId() {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":12345}}",
                        MediaType.APPLICATION_JSON));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.SUCCESS);
        assertThat(result.getTelegramMessageId()).isEqualTo(12345L);
        server.verify();
    }

    // ========== Telegram-level reject (200 + ok=false) ==========

    @Test
    void okFalseResponseMapsToRejectedInvalidRequest() {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}",
                        MediaType.APPLICATION_JSON));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("error_code=400");
        assertThat(result.getErrorMessage()).contains("Bad Request: chat not found");
        server.verify();
    }

    // ========== HTTP 4xx ==========

    @Test
    void http400MapsToRejectedInvalidRequest() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400}"));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("HTTP 400");
        server.verify();
    }

    @Test
    void http403MapsToRejectedInvalidRequest() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("HTTP 403");
        server.verify();
    }

    // ========== HTTP 429 ==========

    @Test
    void http429MapsToFailedRateLimit() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.RATE_LIMIT);
        assertThat(result.getErrorMessage()).contains("429");
        server.verify();
    }

    // ========== HTTP 5xx ==========

    @Test
    void http500MapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        assertThat(result.getErrorMessage()).contains("HTTP 500");
        server.verify();
    }

    // ========== Network / IO failure ==========

    @Test
    void networkExceptionMapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        server.verify();
    }

    // ========== Token redaction ==========

    @Test
    void failureMessageDoesNotContainBotToken() {
        // Telegram javobida token ko'rinmasligi kerak — lekin defensiv ravishda
        // sanitize ishlashini tasdiqlash uchun, javob ichida token sub-string'i
        // chiqsa ham gateway uni redact qiladi.
        String responseBodyWithToken = "{\"ok\":false,\"error_code\":400,"
                + "\"description\":\"Token leak attempt: " + TEST_TOKEN + "\"}";
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(responseBodyWithToken, MediaType.APPLICATION_JSON));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.getErrorMessage())
                .as("Error message must not contain bot token")
                .doesNotContain(TEST_TOKEN);
        assertThat(result.getErrorMessage())
                .as("Error message must contain redaction placeholder")
                .contains("***");
        server.verify();
    }

    // ========== Payload mapping ==========

    @Test
    void payloadContainsChatIdMessageThreadIdTextAndInlineKeyboardWithoutParseMode() {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chat_id").value(CHAT_ID))
                .andExpect(jsonPath("$.message_thread_id").value(TOPIC_ID))
                .andExpect(jsonPath("$.text").value("Bug | BUG-1\nLogin xato"))
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andExpect(jsonPath("$.reply_markup.inline_keyboard").exists())
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].text").value("Approve"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("do:approve"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].text").value("Reject"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].callback_data").value("do:reject"))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":1}}",
                        MediaType.APPLICATION_JSON));

        TelegramGatewayResult result = gateway.execute(sampleRequest(true));

        assertThat(result.isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void payloadOmitsReplyMarkupWhenKeyboardEmpty() {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(jsonPath("$.reply_markup").doesNotExist())
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":1}}",
                        MediaType.APPLICATION_JSON));

        TelegramGatewayResult result = gateway.execute(sampleRequest(false));

        assertThat(result.isSuccess()).isTrue();
        server.verify();
    }

    // ========== Chat binding resolution ==========

    @Test
    void executeResolvesChatIdFromTenantConfigQueryService() {
        server.expect(requestTo(EXPECTED_URL))
                .andExpect(jsonPath("$.chat_id").value(CHAT_ID))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":1}}",
                        MediaType.APPLICATION_JSON));

        gateway.execute(sampleRequest(false));

        verify(tenantConfigQueryService).findChatBindingById(TENANT_ID, CHAT_BINDING_ID);
        server.verify();
    }

    @Test
    void missingChatBindingMapsToRejectedAndDoesNotCallHttp() {
        UUID unknownBindingId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, unknownBindingId))
                .thenReturn(Optional.empty());
        // server.expect... yo'q — HTTP chaqirilmasligi kerak.

        TelegramSendMessageRequest req = new TelegramSendMessageRequest(
                TENANT_ID, WORK_ITEM_ID, unknownBindingId, TOPIC_ID,
                "any", List.of());

        TelegramGatewayResult result = gateway.execute(req);

        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("Telegram chat binding topilmadi");
        // verify() chaqirilmaydi — server'da hech qanday expectation yo'q,
        // shuning uchun har qanday HTTP chaqiruv test'ni buzgan bo'lardi.
    }

    // ========== Legacy dispatch(command) ==========

    // ==========================================================
    // Phase 175 — answerCallbackQuery
    // ==========================================================

    private static final String EXPECTED_ANSWER_URL =
            BASE_URL + "/bot" + TEST_TOKEN + "/answerCallbackQuery";

    private TelegramAcknowledgeCallbackRequest sampleAcknowledgeRequest() {
        return new TelegramAcknowledgeCallbackRequest("cb-id-1", "Action applied.");
    }

    @Test
    void acknowledgeCallback_okTrueResponseMapsToSuccess() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.callback_query_id").value("cb-id-1"))
                .andExpect(jsonPath("$.text").value("Action applied."))
                .andExpect(jsonPath("$.show_alert").doesNotExist())
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}",
                        MediaType.APPLICATION_JSON));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.SUCCESS);
        server.verify();
    }

    @Test
    void acknowledgeCallback_okFalseResponseMapsToRejected() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withSuccess(
                        "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: query is too old\"}",
                        MediaType.APPLICATION_JSON));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("error_code=400");
        server.verify();
    }

    @Test
    void acknowledgeCallback_http400MapsToRejected() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400}"));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("HTTP 400");
        server.verify();
    }

    @Test
    void acknowledgeCallback_http403MapsToRejected() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void acknowledgeCallback_http429MapsToFailedRateLimit() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.RATE_LIMIT);
        server.verify();
    }

    @Test
    void acknowledgeCallback_http500MapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        server.verify();
    }

    @Test
    void acknowledgeCallback_networkExceptionMapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        server.verify();
    }

    @Test
    void acknowledgeCallback_malformedResponseMapsToFailedUnknown() {
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withSuccess("not-json-at-all", MediaType.APPLICATION_JSON));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        server.verify();
    }

    @Test
    void acknowledgeCallback_nullRequestMapsToFailedUnknown() {
        TelegramAcknowledgeCallbackResult result = gateway.acknowledgeCallback(null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        // server.verify() chaqirilmaydi — HTTP chaqiruv bo'lmasligi shart.
    }

    @Test
    void acknowledgeCallback_failureMessageDoesNotContainBotToken() {
        String responseBodyWithToken = "{\"ok\":false,\"error_code\":400,"
                + "\"description\":\"Token leak attempt: " + TEST_TOKEN + "\"}";
        server.expect(requestTo(EXPECTED_ANSWER_URL))
                .andRespond(withSuccess(responseBodyWithToken, MediaType.APPLICATION_JSON));

        TelegramAcknowledgeCallbackResult result =
                gateway.acknowledgeCallback(sampleAcknowledgeRequest());

        assertThat(result.getErrorMessage()).doesNotContain(TEST_TOKEN);
        assertThat(result.getErrorMessage()).contains("***");
        server.verify();
    }

    // ==========================================================
    // Phase 177 — editMessageText
    // ==========================================================

    private static final String EXPECTED_EDIT_URL =
            BASE_URL + "/bot" + TEST_TOKEN + "/editMessageText";

    private TelegramEditMessageTextRequest sampleEditRequest(boolean withKeyboard) {
        List<TelegramInlineKeyboardRow> keyboard = withKeyboard
                ? List.of(new TelegramInlineKeyboardRow(List.of(
                        new TelegramInlineKeyboardButton("Mark Fixed", "uuid:MARK_FIXED"))))
                : List.of();
        return new TelegramEditMessageTextRequest(
                CHAT_ID, 555L, "Bug | BUG-1\nStatus: FIXED", keyboard);
    }

    @Test
    void editMessageText_okTrueResponseMapsToSuccess() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":555}}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.SUCCESS);
        assertThat(result.getTelegramMessageId()).isEqualTo(555L);
        server.verify();
    }

    @Test
    void editMessageText_okFalseResponseMapsToRejected() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withSuccess(
                        "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: message can't be edited\"}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("error_code=400");
        server.verify();
    }

    @Test
    void editMessageText_messageNotModifiedMapsToRejected() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withSuccess(
                        "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: message is not modified\"}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("message is not modified");
        server.verify();
    }

    @Test
    void editMessageText_http400MapsToRejected() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\":false,\"error_code\":400}"));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).contains("HTTP 400");
        server.verify();
    }

    @Test
    void editMessageText_http403MapsToRejected() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void editMessageText_http404MapsToRejected() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void editMessageText_http429MapsToFailedRateLimit() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.RATE_LIMIT);
        server.verify();
    }

    @Test
    void editMessageText_http500MapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        server.verify();
    }

    @Test
    void editMessageText_networkExceptionMapsToFailedNetworkError() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        server.verify();
    }

    @Test
    void editMessageText_malformedResponseMapsToFailedUnknown() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withSuccess("not-json-at-all", MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        server.verify();
    }

    @Test
    void editMessageText_nullRequestMapsToFailedUnknown() {
        TelegramEditMessageTextResult result = gateway.editMessageText(null);

        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        // HTTP chaqiruv bo'lmasligi shart — server'da hech qanday expectation yo'q.
    }

    @Test
    void editMessageText_bodyContainsChatIdMessageIdTextAndOmitsParseMode() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.chat_id").value(CHAT_ID))
                .andExpect(jsonPath("$.message_id").value(555))
                .andExpect(jsonPath("$.text").value("Bug | BUG-1\nStatus: FIXED"))
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andExpect(jsonPath("$.show_alert").doesNotExist())
                .andExpect(jsonPath("$.callback_query_id").doesNotExist())
                .andExpect(jsonPath("$.message_thread_id").doesNotExist())
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":555}}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void editMessageText_bodyContainsReplyMarkupWhenKeyboardPresent() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard").exists())
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].text").value("Mark Fixed"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data")
                        .value("uuid:MARK_FIXED"))
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":555}}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(true));

        assertThat(result.isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void editMessageText_bodyOmitsReplyMarkupWhenKeyboardEmpty() {
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andExpect(jsonPath("$.reply_markup").doesNotExist())
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andRespond(withSuccess(
                        "{\"ok\":true,\"result\":{\"message_id\":555}}",
                        MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.isSuccess()).isTrue();
        server.verify();
    }

    @Test
    void editMessageText_failureMessageDoesNotContainBotToken() {
        String responseBodyWithToken = "{\"ok\":false,\"error_code\":400,"
                + "\"description\":\"Token leak attempt: " + TEST_TOKEN + "\"}";
        server.expect(requestTo(EXPECTED_EDIT_URL))
                .andRespond(withSuccess(responseBodyWithToken, MediaType.APPLICATION_JSON));

        TelegramEditMessageTextResult result = gateway.editMessageText(sampleEditRequest(false));

        assertThat(result.getErrorMessage()).doesNotContain(TEST_TOKEN);
        assertThat(result.getErrorMessage()).contains("***");
        server.verify();
    }

    @Test
    void legacyDispatchReturnsDefensiveFailure() {
        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TENANT_ID, WORK_ITEM_ID, CHAT_BINDING_ID, TOPIC_ID,
                "any", List.of());

        TelegramDeliveryResult result = gateway.dispatch(command);

        assertThat(result.getDeliveryOutcome())
                .isEqualTo(TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("DISPATCH_NOT_SUPPORTED");
        // HTTP chaqiruvi bo'lmasligi server.verify() bilan emas — server hech
        // qanday expectation o'rnatmagan; har qanday HTTP chaqiruv bu testni
        // buzgan bo'lardi.
        verify(tenantConfigQueryService, never()).findChatBindingById(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
