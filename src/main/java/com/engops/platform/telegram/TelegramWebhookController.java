package com.engops.platform.telegram;

import com.engops.platform.infrastructure.web.ApiErrorResponse;
import com.engops.platform.intake.TelegramCallbackActionExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Phase 171 — Telegram inbound webhook endpoint'i.
 *
 * <p><strong>Endpoint:</strong> {@code POST /api/telegram/webhook}.</p>
 *
 * <p><strong>Authentication:</strong> Telegram Bot API'ning
 * <em>Secret Token</em> mexanizmiga asoslangan. Operator
 * {@code setWebhook} chaqiruvida {@code secret_token} qiymatini bersa,
 * Telegram har bir webhook so'rovida shuni
 * {@code X-Telegram-Bot-Api-Secret-Token} HTTP header'i orqali qaytaradi.
 * Controller {@link MessageDigest#isEqual(byte[], byte[]) constant-time}
 * taqqoslab kelganini tasdiqlaydi.</p>
 *
 * <p><strong>Fail-closed:</strong> {@link TelegramWebhookProperties#getSecretToken()}
 * bo'sh string qaytarsa (configured emas), har qanday so'rov
 * {@code 401 Unauthorized} + Phase 148 envelope qaytadi. Production
 * deployment'lar tokenni env / secret manager orqali majburiy o'rnatishi
 * shart.</p>
 *
 * <p><strong>Response semantics:</strong></p>
 * <ul>
 *   <li>Missing/wrong secret → {@code 401} + envelope, service chaqirilmaydi.</li>
 *   <li>Body parse fail (invalid JSON) → {@code 400} (Spring/{@code GlobalExceptionHandler}
 *       default).</li>
 *   <li>Valid secret + non-callback update (ya'ni {@code callback_query}
 *       yo'q) → {@code 200 OK}, service chaqirilmaydi.</li>
 *   <li>Valid secret + callback_query (har qanday outcome) →
 *       {@code 200 OK}. Telegram retry'ni keltirib chiqarmaslik uchun
 *       unknown/malformed data ham 4xx EMAS, 200 + log.</li>
 * </ul>
 *
 * <p><strong>Security routing:</strong> bu endpoint
 * {@link com.engops.platform.infrastructure.security.SecurityConfig} ichida
 * {@code permitAll} bilan belgilanadi (Telegram JWT yubormaydi). JWT
 * {@code @CurrentActor} resolver bu endpoint'da ishlatilmaydi.</p>
 *
 * <p><strong>Phase 173 — authorized workflow transition wiring:</strong>
 * parser endi {@link TelegramCallbackParseResult} qaytaradi. Parser
 * outcome {@link TelegramCallbackQueryService.CallbackOutcome#ACCEPTED}
 * bo'lsa, controller {@link TelegramCallbackActionExecutionService}'ga
 * delegate qiladi. Orchestrator AppUser resolve, tenantId derive, ACTIVE
 * membership va {@code WORK_ITEM_TRANSITION} permission tekshiruvini
 * bajaradi va keyin {@code WorkflowTransitionService.transition(...)}'ni
 * chaqiradi. Controller {@code WorkflowTransitionService} yoki
 * {@code OperationalAuthorizationService}'ni ataylab IMPORT QILMAYDI —
 * thin controller invariant'ini saqlaydi.</p>
 *
 * <p><strong>Out of scope (Phase 171/173):</strong> {@code answerCallbackQuery}
 * outbound chaqiruvi; {@code editMessageText}; {@code parse_mode};
 * {@code setWebhook} automation. Operator {@code setWebhook}'ni qo'lda
 * chaqiradi (telegram-outbound-gateway-runbook.md ga qarang).</p>
 *
 * <p><strong>Logging hygiene:</strong> token qiymati (configured yoki
 * incoming) hech qachon log qilinmaydi; full update payload log'ga
 * chiqarilmaydi; faqat bounded metadata yoziladi.</p>
 */
@RestController
@RequestMapping("/api/telegram/webhook")
@EnableConfigurationProperties(TelegramWebhookProperties.class)
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    /**
     * Telegram Bot API <em>secret_token</em> mexanizmining HTTP header'i.
     */
    static final String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    static final String UNAUTHORIZED_ERROR_CODE = "UNAUTHORIZED";

    private final TelegramWebhookProperties properties;
    private final TelegramCallbackQueryService callbackQueryService;
    private final TelegramCallbackActionExecutionService executionService;

    public TelegramWebhookController(TelegramWebhookProperties properties,
                                      TelegramCallbackQueryService callbackQueryService,
                                      TelegramCallbackActionExecutionService executionService) {
        this.properties = properties;
        this.callbackQueryService = callbackQueryService;
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<?> receive(
            @RequestHeader(name = SECRET_TOKEN_HEADER, required = false) String incomingSecret,
            @RequestBody(required = false) TelegramUpdateRequest update,
            HttpServletRequest httpRequest) {

        if (!secretMatches(incomingSecret)) {
            return unauthorized(httpRequest);
        }

        if (update == null || update.callbackQuery() == null) {
            log.info("Telegram webhook ignored (non-callback update) updateId={}",
                    update == null ? null : update.updateId());
            return ResponseEntity.ok().build();
        }

        // Phase 173: parser parsed workItemId + actionCode'ni qaytaradi.
        // ACCEPTED bo'lsa, orchestrator authorized workflow transition'ni
        // bajaradi. Boshqa parser/business/auth outcomelar uchun ham
        // controller har doim 200 OK qaytaradi — Telegram retry loop'larining
        // oldini olish uchun. Faqat invalid secret 401 qaytaradi.
        TelegramCallbackParseResult parseResult =
                callbackQueryService.process(update.callbackQuery());
        if (parseResult.outcome() == TelegramCallbackQueryService.CallbackOutcome.ACCEPTED) {
            executionService.execute(update.callbackQuery(),
                    parseResult.workItemId(), parseResult.actionCode());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Constant-time secret tasdiqlash. {@code configured} bo'sh bo'lsa
     * yoki {@code incoming} null bo'lsa, {@code false}. Token qiymatlari
     * hech qaerga log qilinmaydi.
     */
    private boolean secretMatches(String incoming) {
        String configured = properties.getSecretToken();
        if (configured.isEmpty()) {
            // Fail-closed: konfiguratsiya yo'q bo'lsa hech kim o'tmaydi.
            return false;
        }
        if (incoming == null) {
            return false;
        }
        byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
        byte[] actual = incoming.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private ResponseEntity<ApiErrorResponse> unauthorized(HttpServletRequest httpRequest) {
        ApiErrorResponse body = ApiErrorResponse.of(
                UNAUTHORIZED_ERROR_CODE,
                "Telegram webhook secret token noto'g'ri yoki yo'q",
                MDC.get("correlationId"),
                httpRequest.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
