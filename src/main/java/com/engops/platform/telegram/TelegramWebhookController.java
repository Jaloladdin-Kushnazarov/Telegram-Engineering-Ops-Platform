package com.engops.platform.telegram;

import com.engops.platform.infrastructure.web.ApiErrorResponse;
import com.engops.platform.intake.TelegramBotCommandService;
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
 * <p><strong>Historical scope note:</strong> Phase 171/173 doirasidan
 * tashqarida bo'lgan {@code answerCallbackQuery} va {@code editMessageText}
 * keyinroq Phase 175/177/179 da joriy etildi. {@code parse_mode} va
 * {@code setWebhook} automation hozir ham mahsulot doirasidan tashqarida
 * — operator {@code setWebhook}'ni qo'lda chaqiradi (
 * {@code telegram-outbound-gateway-runbook.md} ga qarang). Bu controller
 * thin bo'lib qoladi va outbound operatsiyalarni o'zi bajarmaydi.</p>
 *
 * <p><strong>Logging hygiene:</strong> token qiymati (configured yoki
 * incoming) hech qachon log qilinmaydi; full update payload log'ga
 * chiqarilmaydi; faqat bounded metadata yoziladi.</p>
 *
 * <p><strong>Phase 189 — webhook rejection audit (intentionally
 * deferred).</strong> Operatorlar webhook secret rejection'ini audit
 * qatori orqali kuzatish iltimosini ko'targan. Joriy
 * {@code audit_event} schema'da {@code entity_id} {@code NOT NULL}
 * va loyihada nil UUID pattern ishlatilmaydi. Yangi migration qo'shish
 * Phase 189 doirasidan tashqari. Shu sababdan
 * {@code TELEGRAM_WEBHOOK_REJECTED} audit qatori ataylab
 * <strong>YOZILMAYDI</strong>; rejection signal'i shu klassdagi
 * bounded {@code log.warn(...)} qatori orqali qoladi (rejection sababi:
 * MISSING_SECRET_CONFIG / MISSING_HEADER / INVALID_HEADER). Audit
 * darajasidagi rejection trail keyingi phase'da schema constraint
 * yumshatilganda yoki schema-safe nil UUID convention joriy etilganda
 * qo'shiladi (Phase 190+ nomzodi).</p>
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

    /**
     * Phase 189 — webhook rejection sabablari bounded log uchun.
     * Bu enum audit qatoriga aylanmaydi (yuqoridagi class-level javadoc'ga
     * qarang) — faqat bounded {@code log.warn(...)} ichida tag sifatida
     * ishlatiladi.
     */
    enum WebhookRejectionReason {
        /** Configured secret token bo'sh — fail-closed. */
        MISSING_SECRET_CONFIG,
        /** So'rovda {@code X-Telegram-Bot-Api-Secret-Token} header yo'q. */
        MISSING_HEADER,
        /** Header mavjud, lekin configured qiymat bilan teng emas. */
        INVALID_HEADER
    }

    private final TelegramWebhookProperties properties;
    private final TelegramCallbackQueryService callbackQueryService;
    private final TelegramCallbackActionExecutionService executionService;
    private final TelegramBotCommandService botCommandService;

    public TelegramWebhookController(TelegramWebhookProperties properties,
                                      TelegramCallbackQueryService callbackQueryService,
                                      TelegramCallbackActionExecutionService executionService,
                                      TelegramBotCommandService botCommandService) {
        this.properties = properties;
        this.callbackQueryService = callbackQueryService;
        this.executionService = executionService;
        this.botCommandService = botCommandService;
    }

    @PostMapping
    public ResponseEntity<?> receive(
            @RequestHeader(name = SECRET_TOKEN_HEADER, required = false) String incomingSecret,
            @RequestBody(required = false) TelegramUpdateRequest update,
            HttpServletRequest httpRequest) {

        WebhookRejectionReason rejectionReason = classifyRejection(incomingSecret);
        if (rejectionReason != null) {
            log.warn("Telegram webhook rejected reason={} hasCallbackQuery={}",
                    rejectionReason,
                    update == null ? "null" : Boolean.toString(update.callbackQuery() != null));
            return unauthorized(httpRequest);
        }

        // Phase 200: bot command (text message starting with "/") branch.
        // callback_query bilan o'zaro eksklyuziv (Telegram bir update'da
        // yo callback_query yo message yuboradi). Fail-soft — har qanday
        // RuntimeException webhook handler'da 200 OK ga aylanadi: harakat
        // Telegram retry loop'larini chiqarib qo'ymaslik uchun.
        if (update != null
                && update.callbackQuery() == null
                && update.message() != null
                && update.message().text() != null
                && update.message().text().startsWith("/")) {
            try {
                botCommandService.handle(update);
            } catch (RuntimeException ex) {
                log.warn("Telegram bot command dispatch threw (fail-soft): updateId={} exceptionType={}",
                        update.updateId(), ex.getClass().getSimpleName());
            }
            return ResponseEntity.ok().build();
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
     * Phase 189 — webhook secret tekshirish va rejection sababini aniqlash.
     *
     * <p>Match holatda {@code null} qaytaradi. Aks holda bounded
     * {@link WebhookRejectionReason} qaytaradi: configured secret bo'sh
     * bo'lsa {@code MISSING_SECRET_CONFIG}, header yo'q bo'lsa
     * {@code MISSING_HEADER}, header configured bilan teng emas bo'lsa
     * {@code INVALID_HEADER}. Token qiymatlari (configured yoki incoming)
     * hech qachon log'ga chiqarilmaydi.</p>
     */
    private WebhookRejectionReason classifyRejection(String incoming) {
        String configured = properties.getSecretToken();
        if (configured.isEmpty()) {
            // Fail-closed: konfiguratsiya yo'q bo'lsa hech kim o'tmaydi.
            return WebhookRejectionReason.MISSING_SECRET_CONFIG;
        }
        if (incoming == null) {
            return WebhookRejectionReason.MISSING_HEADER;
        }
        byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
        byte[] actual = incoming.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? null : WebhookRejectionReason.INVALID_HEADER;
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
