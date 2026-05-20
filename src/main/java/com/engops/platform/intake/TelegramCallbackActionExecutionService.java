package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramCallbackAcknowledgementService;
import com.engops.platform.telegram.TelegramCallbackQueryRequest;
import com.engops.platform.workflow.WorkflowTransitionService;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemQueryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 173 — Telegram inbound callback_query uchun
 * <em>authorized workflow transition</em> orchestrator'i.
 *
 * <p><strong>Modul joylashuvi:</strong> bu service ataylab
 * {@code com.engops.platform.intake} paketida joylashgan. Telegram moduli
 * {@code workflow} moduliga to'g'ridan-to'g'ri bog'lana olmaydi
 * ({@code ModuleBoundaryTest} arxitektura qoidasi). Intake moduli esa
 * Telegram outbound dispatch'ni coordinate qiluvchi mavjud
 * boundary-crosser (Phase 164 {@code TelegramCardDispatchEventListener}
 * bilan bir xil pattern). Inbound callback execution shu moduldagi tabiiy
 * davom — intake adapter-coordination module sifatida ishlatiladi.</p>
 *
 * <p><strong>Mas'uliyat:</strong> Telegram parser
 * ({@link com.engops.platform.telegram.TelegramCallbackQueryService})
 * tomonidan {@code ACCEPTED} deb belgilangan callback uchun quyidagi
 * qadamlar zanjirini bajaradi:</p>
 * <ol>
 *   <li>Telegram {@code callback_query.from.id} bo'yicha platforma
 *       {@link AppUser}'ini hal qilish ({@link IdentityQueryService#findUserByTelegramUserId(Long)}).</li>
 *   <li>{@code workItemId} bo'yicha tenantId'ni backend'dan derive qilish
 *       ({@link WorkItemQueryService#findTenantIdByWorkItemId(UUID)}).
 *       <em>callback_data hech qachon authoritative tenantId tashimaydi</em>.</li>
 *   <li>Derive qilingan tenantda foydalanuvchining ACTIVE membership'ini
 *       majburiy tekshirish ({@link IdentityQueryService#hasActiveMembership(UUID, UUID)}).</li>
 *   <li>Server-side {@code WORK_ITEM_TRANSITION} permission'ini majburiy
 *       qilish ({@link OperationalAuthorizationService#authorizeTransition(UUID, UUID)}).</li>
 *   <li>{@code actionCode → targetStatusCode} mapping'i (MVP bug flow).</li>
 *   <li>{@link WorkflowTransitionService#transition(UUID, UUID, String, UUID, String, String)}
 *       chaqirish ({@code actionSource = "TELEGRAM_CALLBACK"},
 *       {@code reason = null}).</li>
 * </ol>
 *
 * <p><strong>Trust model — diqqat:</strong> Telegram inline button
 * ko'rinishi <strong>authorization belgisi emas</strong>. Server har
 * doim mustaqil ravishda membership + permission'ni tekshiradi. Tenant
 * faqat backend'dan derive qilinadi; callback_data, chat id, yoki
 * Telegram username hech qachon authority sifatida qabul qilinmaydi.</p>
 *
 * <p><strong>HTTP kontrakt:</strong> bu service hech qanday kutilgan
 * business/auth failure uchun exception tashlamaydi — har bir holat
 * {@link ExecutionOutcome} enum qiymatiga aylantiriladi. Webhook
 * controller bu outcome'lar uchun har doim 200 OK qaytaradi va Telegram
 * retry loop'larining oldini oladi. Invalid webhook secret 401'ligicha
 * qoladi va bu service'gacha umuman yetib kelmaydi.</p>
 *
 * <p><strong>Transaction boundary:</strong> bu service class-level
 * {@code @Transactional} EMAS. Har bir downstream collaborator o'z tx
 * chegarasini saqlaydi:</p>
 * <ul>
 *   <li>{@link IdentityQueryService} — {@code @Transactional(readOnly=true)}.</li>
 *   <li>{@link WorkItemQueryService} — {@code @Transactional(readOnly=true)}.</li>
 *   <li>{@link OperationalAuthorizationService} — read tx
 *       {@code resolvePermissionCodes(...)} ichida.</li>
 *   <li>{@link WorkflowTransitionService} — o'z {@code @Transactional}
 *       write tx'i.</li>
 * </ul>
 * <p>Telegram outbound HTTP hech qachon shu zanjir ichida chaqirilmaydi —
 * Phase 164/168 AFTER_COMMIT pattern dispatch'i o'zgarmaydi.</p>
 *
 * <p><strong>Audit:</strong> muvaffaqiyatli transition uchun mavjud
 * {@code STATUS_TRANSITION} audit eventi
 * {@link WorkflowTransitionService} ichida yoziladi
 * ({@code actionSource = "TELEGRAM_CALLBACK"}).
 *
 * <p>Phase 185 — denial/failure outcome'lar uchun
 * {@code TELEGRAM_CALLBACK_DENIED} audit qatori
 * {@link AuditService#recordEventInNewTransaction} orqali alohida
 * {@code REQUIRES_NEW} transactionda yoziladi. Audit yozuvi quyidagi
 * outcome'lar uchun yaratiladi: {@code NOT_A_MEMBER},
 * {@code PERMISSION_DENIED}, {@code INVALID_TRANSITION},
 * {@code UNEXPECTED_FAILURE}. {@code USER_NOT_FOUND} (resolved actor
 * yo'q) va {@code WORK_ITEM_NOT_FOUND} (derived tenant yo'q) uchun
 * audit yozilmaydi — entity_id/tenant_id majburiy maydonlarini
 * to'ldirib bo'lmaydi. Audit yozish har qanday holatda fail-soft:
 * persistence xatosi callback outcome'iga ta'sir qilmaydi va
 * acknowledgement baribir chaqiriladi.</p>
 *
 * <p><strong>Logging hygiene:</strong> har bir execute chaqiruvi uchun
 * bitta bounded log yoziladi. Hech qachon log qilinmaydi: webhook secret,
 * inbound header qiymati, to'liq payload, to'liq callback_data, exception
 * message, kutilgan failure'lar uchun stack trace.</p>
 *
 * <p><strong>Out of scope (Phase 173):</strong> {@code answerCallbackQuery},
 * {@code editMessageText}, {@code parse_mode}, {@code setWebhook}
 * automation, async, scheduler, outbox, idempotency table, optimistic
 * locking.</p>
 */
@Service
public class TelegramCallbackActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCallbackActionExecutionService.class);

    static final String ACTION_SOURCE = "TELEGRAM_CALLBACK";

    /**
     * Phase 185 — denial audit event'lar uchun eventType.
     */
    static final String DENIED_EVENT_TYPE = "TELEGRAM_CALLBACK_DENIED";

    /**
     * Phase 185 — denial audit event'lar uchun entityType.
     */
    static final String DENIED_ENTITY_TYPE = "WORK_ITEM";

    /**
     * Phase 189 — callback execution outcome counter nomi (low-cardinality).
     */
    static final String CALLBACK_OUTCOMES_METER = "engops.telegram.callback.execution.outcomes";

    /**
     * Action code → target status code mapping (MVP bug flow).
     *
     * <p>Bu mapping {@code TelegramActionAssembler} (outbound) va
     * {@code TelegramCallbackQueryService.KNOWN_ACTION_CODES} (inbound
     * parser) bilan sinxron bo'lishi shart. Kelajakda alohida phase
     * shu uchta joyni umumiy katalogga birlashtirishi mumkin — Phase 173
     * surgical doirasida qasddan duplicate qilinadi.</p>
     */
    private static final Map<String, String> ACTION_TO_TARGET_STATUS = Map.of(
            "START_PROCESSING", "PROCESSING",
            "SEND_TO_TESTING", "TESTING",
            "MARK_FIXED", "FIXED",
            "RETURN_TO_BUGS", "BUGS",
            "REOPEN", "BUGS");

    /**
     * Phase 175 — per-outcome bounded acknowledgement text.
     *
     * <p>Har bir text Telegram cheklovi (<= 200 simvol) ichida. Tenant
     * nomi, work item identifikatori, exception detail, stack — kiritilmaydi.
     * Foydalanuvchi-ko'rinmas internal kontekst yo'q.</p>
     */
    private static final Map<ExecutionOutcome, String> OUTCOME_ACKNOWLEDGE_TEXT = buildOutcomeText();

    private static Map<ExecutionOutcome, String> buildOutcomeText() {
        EnumMap<ExecutionOutcome, String> map = new EnumMap<>(ExecutionOutcome.class);
        map.put(ExecutionOutcome.EXECUTED,
                "Action applied.");
        map.put(ExecutionOutcome.USER_NOT_FOUND,
                "Telegram user is not linked to a platform account.");
        map.put(ExecutionOutcome.WORK_ITEM_NOT_FOUND,
                "Work item was not found.");
        map.put(ExecutionOutcome.NOT_A_MEMBER,
                "You are not an active member of this tenant.");
        map.put(ExecutionOutcome.PERMISSION_DENIED,
                "You do not have permission to change this work item.");
        map.put(ExecutionOutcome.INVALID_TRANSITION,
                "This action is no longer valid for the current status.");
        map.put(ExecutionOutcome.UNEXPECTED_FAILURE,
                "Could not process the action. Please try again later.");
        return map;
    }

    /**
     * Phase 173 execution outcomes. Har bir holat HTTP 200 javobiga
     * mos keladi; controller faqat invalid webhook secret uchun 401
     * qaytaradi.
     */
    public enum ExecutionOutcome {
        /** Transition muvaffaqiyatli bajarildi. */
        EXECUTED,
        /** callback_query.from null edi yoki {@code telegram_user_id} platformada AppUser bilan bog'lanmagan. */
        USER_NOT_FOUND,
        /** workItemId bo'yicha tenant derive qilinmadi (work item mavjud emas). */
        WORK_ITEM_NOT_FOUND,
        /** AppUser hal qilindi, lekin derive qilingan tenantda ACTIVE membership yo'q. */
        NOT_A_MEMBER,
        /** ACTIVE member, lekin {@code WORK_ITEM_TRANSITION} ruxsati yo'q. */
        PERMISSION_DENIED,
        /** Workflow state machine transition'ni rad etdi (mismatched rule, SAME_STATUS, va h.k.). */
        INVALID_TRANSITION,
        /** Kutilmagan runtime xato (log'da faqat exceptionType qoldiriladi). */
        UNEXPECTED_FAILURE
    }

    private final IdentityQueryService identityQueryService;
    private final WorkItemQueryService workItemQueryService;
    private final OperationalAuthorizationService operationalAuthorizationService;
    private final WorkflowTransitionService workflowTransitionService;
    private final TelegramCallbackAcknowledgementService acknowledgementService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public TelegramCallbackActionExecutionService(
            IdentityQueryService identityQueryService,
            WorkItemQueryService workItemQueryService,
            OperationalAuthorizationService operationalAuthorizationService,
            WorkflowTransitionService workflowTransitionService,
            TelegramCallbackAcknowledgementService acknowledgementService,
            AuditService auditService,
            MeterRegistry meterRegistry) {
        this.identityQueryService = identityQueryService;
        this.workItemQueryService = workItemQueryService;
        this.operationalAuthorizationService = operationalAuthorizationService;
        this.workflowTransitionService = workflowTransitionService;
        this.acknowledgementService = acknowledgementService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Parser tomonidan ACCEPTED deb belgilangan callback'ni authorized
     * workflow transition sifatida bajaradi.
     *
     * <p>Hech qanday kutilgan business/auth failure uchun exception
     * tashlamaydi — har bir holat {@link ExecutionOutcome} qiymati
     * sifatida qaytariladi. Faqat parser bosqichidan keyin chaqirilishi
     * mo'ljallangan (controller hozirgi bog'lanishida shu invariantni
     * ta'minlaydi).</p>
     *
     * @param callbackQuery Telegram'dan kelgan callback_query (from.id log
     *                      attribution + AppUser resolve uchun)
     * @param workItemId    parser tomonidan ajratilgan UUID
     * @param actionCode    parser tomonidan ajratilgan known action code
     * @return execution outcome
     */
    public ExecutionOutcome execute(TelegramCallbackQueryRequest callbackQuery,
                                     UUID workItemId,
                                     String actionCode) {
        Long telegramUserId = extractTelegramUserId(callbackQuery);
        if (telegramUserId == null) {
            logOutcome(ExecutionOutcome.USER_NOT_FOUND, callbackQuery, null, workItemId, actionCode,
                    null, null, null);
            return acknowledgeAndReturn(ExecutionOutcome.USER_NOT_FOUND, callbackQuery);
        }

        Optional<AppUser> userOpt = identityQueryService.findUserByTelegramUserId(telegramUserId);
        if (userOpt.isEmpty()) {
            logOutcome(ExecutionOutcome.USER_NOT_FOUND, callbackQuery, telegramUserId, workItemId,
                    actionCode, null, null, null);
            return acknowledgeAndReturn(ExecutionOutcome.USER_NOT_FOUND, callbackQuery);
        }
        UUID actorUserId = userOpt.get().getId();

        Optional<UUID> tenantIdOpt = workItemQueryService.findTenantIdByWorkItemId(workItemId);
        if (tenantIdOpt.isEmpty()) {
            logOutcome(ExecutionOutcome.WORK_ITEM_NOT_FOUND, callbackQuery, telegramUserId,
                    workItemId, actionCode, null, null, null);
            return acknowledgeAndReturn(ExecutionOutcome.WORK_ITEM_NOT_FOUND, callbackQuery);
        }
        UUID tenantId = tenantIdOpt.get();

        if (!identityQueryService.hasActiveMembership(tenantId, actorUserId)) {
            logOutcome(ExecutionOutcome.NOT_A_MEMBER, callbackQuery, telegramUserId, workItemId,
                    actionCode, tenantId, null, null);
            auditDenialOutcomeSafely(ExecutionOutcome.NOT_A_MEMBER, tenantId, actorUserId,
                    workItemId, actionCode, null);
            return acknowledgeAndReturn(ExecutionOutcome.NOT_A_MEMBER, callbackQuery);
        }

        try {
            operationalAuthorizationService.authorizeTransition(tenantId, actorUserId);
        } catch (AccessDeniedException ex) {
            logOutcome(ExecutionOutcome.PERMISSION_DENIED, callbackQuery, telegramUserId,
                    workItemId, actionCode, tenantId, null, null);
            auditDenialOutcomeSafely(ExecutionOutcome.PERMISSION_DENIED, tenantId, actorUserId,
                    workItemId, actionCode, null);
            return acknowledgeAndReturn(ExecutionOutcome.PERMISSION_DENIED, callbackQuery);
        }

        String targetStatusCode = ACTION_TO_TARGET_STATUS.get(actionCode);
        if (targetStatusCode == null) {
            // Parser ACCEPTED qaytargan bo'lsa ham mapping yo'q bo'lsa
            // (KNOWN_ACTION_CODES va ACTION_TO_TARGET_STATUS sinxron emas)
            // — qulay falback: INVALID_TRANSITION sifatida belgilab,
            // workflow transition'ni umuman chaqirmaymiz.
            logOutcome(ExecutionOutcome.INVALID_TRANSITION, callbackQuery, telegramUserId,
                    workItemId, actionCode, tenantId, null, null);
            auditDenialOutcomeSafely(ExecutionOutcome.INVALID_TRANSITION, tenantId, actorUserId,
                    workItemId, actionCode, null);
            return acknowledgeAndReturn(ExecutionOutcome.INVALID_TRANSITION, callbackQuery);
        }

        ExecutionOutcome outcome;
        try {
            workflowTransitionService.transition(tenantId, workItemId, targetStatusCode,
                    actorUserId, ACTION_SOURCE, null);
            logOutcome(ExecutionOutcome.EXECUTED, callbackQuery, telegramUserId, workItemId,
                    actionCode, tenantId, targetStatusCode, null);
            outcome = ExecutionOutcome.EXECUTED;
        } catch (BusinessRuleException ex) {
            logOutcome(ExecutionOutcome.INVALID_TRANSITION, callbackQuery, telegramUserId,
                    workItemId, actionCode, tenantId, targetStatusCode, null);
            auditDenialOutcomeSafely(ExecutionOutcome.INVALID_TRANSITION, tenantId, actorUserId,
                    workItemId, actionCode, targetStatusCode);
            outcome = ExecutionOutcome.INVALID_TRANSITION;
        } catch (ResourceNotFoundException ex) {
            logOutcome(ExecutionOutcome.WORK_ITEM_NOT_FOUND, callbackQuery, telegramUserId,
                    workItemId, actionCode, tenantId, targetStatusCode, null);
            outcome = ExecutionOutcome.WORK_ITEM_NOT_FOUND;
        } catch (RuntimeException ex) {
            logOutcome(ExecutionOutcome.UNEXPECTED_FAILURE, callbackQuery, telegramUserId,
                    workItemId, actionCode, tenantId, targetStatusCode,
                    ex.getClass().getSimpleName());
            auditDenialOutcomeSafely(ExecutionOutcome.UNEXPECTED_FAILURE, tenantId, actorUserId,
                    workItemId, actionCode, targetStatusCode);
            outcome = ExecutionOutcome.UNEXPECTED_FAILURE;
        }
        return acknowledgeAndReturn(outcome, callbackQuery);
    }

    /**
     * Phase 185 — denial/failure outcome'i uchun mustaqil audit qatori
     * yozadi. Fail-soft: har qanday {@link RuntimeException} swallow
     * qilinadi va bounded warning log'ga yoziladi; {@code execute}
     * tomonidan qaytariladigan outcome o'zgarmaydi, acknowledgement
     * baribir chaqiriladi.
     *
     * <p>Audit yozuvi {@link AuditService#recordEventInNewTransaction}
     * orqali alohida {@code REQUIRES_NEW} transactionda saqlanadi —
     * caller hech qanday ochiq business transaction ichida emas.</p>
     *
     * <p><strong>Payload tarkibi:</strong> faqat {@code outcome},
     * {@code actionCode}, {@code targetStatusCode}. Hech qachon
     * kiritilmaydi: raw callback_data, secret token, bot token,
     * Telegram update payload, exception message, from.username,
     * yoki rendered text.</p>
     */
    private void auditDenialOutcomeSafely(ExecutionOutcome outcome,
                                            UUID tenantId,
                                            UUID actorUserId,
                                            UUID workItemId,
                                            String actionCode,
                                            String targetStatusCode) {
        // Defense-in-depth — bu yo'l outcome'lar (NOT_A_MEMBER,
        // PERMISSION_DENIED, INVALID_TRANSITION, UNEXPECTED_FAILURE) uchun
        // hammada tenantId/actorUserId/workItemId mavjud bo'ladi. Agar
        // kelajakda chaqiruv joyi qo'shilsa va biror qiymat null bo'lsa,
        // audit jadvalining nullability constraint'iga (entity_id NOT NULL)
        // hurmat qilib jim ravishda skip qilamiz va warning yozamiz.
        if (tenantId == null || actorUserId == null || workItemId == null) {
            log.warn("Telegram callback denial audit skip outcome={} reason=missing-ids", outcome);
            return;
        }
        String payload = buildDenialAuditPayload(outcome, actionCode, targetStatusCode);
        try {
            auditService.recordEventInNewTransaction(tenantId,
                    DENIED_ENTITY_TYPE,
                    workItemId,
                    DENIED_EVENT_TYPE,
                    actorUserId,
                    ACTION_SOURCE,
                    null,
                    payload);
        } catch (RuntimeException ex) {
            // Fail-soft kontrakti: audit yozish callback outcome'iga ta'sir
            // qilmasligi shart. Exception message ataylab log'ga
            // chiqarilmaydi (token-leak guard pattern, Phase 158/160/161
            // bilan bir xil).
            log.warn("Telegram callback denial audit swallowed outcome={} exceptionType={}",
                    outcome, ex.getClass().getSimpleName());
        }
    }

    /**
     * Denial audit qatorining {@code newValueJson} maydoni uchun
     * bounded JSON-like payload quradi. Faqat outcome / actionCode /
     * targetStatusCode kiritiladi.
     */
    static String buildDenialAuditPayload(ExecutionOutcome outcome,
                                            String actionCode,
                                            String targetStatusCode) {
        return "{"
                + "\"outcome\":" + jsonStringOrNull(outcome == null ? null : outcome.name())
                + ",\"actionCode\":" + jsonStringOrNull(actionCode)
                + ",\"targetStatusCode\":" + jsonStringOrNull(targetStatusCode)
                + "}";
    }

    private static String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        // ActionCode va targetStatusCode bounded internal identifier'lar
        // (parser KNOWN_ACTION_CODES + workflow status code katalogi).
        // Bu escape defense-in-depth — kelajakda kengayish bo'lsa ham
        // payload'ning JSON struktura buzilmasligini ta'minlaydi.
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Phase 175 — har bir terminal outcome uchun best-effort
     * acknowledgement yuboradi va outcome'ni o'zgartirmasdan qaytaradi.
     *
     * <p>Acknowledgement service'ning o'zi fail-soft, lekin defense-in-depth
     * uchun shu yerda ham {@link RuntimeException} ushlanadi va swallow
     * qilinadi — orchestrator outcome'i hech qachon acknowledgement
     * muvaffaqiyatiga bog'lanmaydi. Workflow transition durability allaqachon
     * o'z {@code @Transactional} commit'idan keyin kafolatlangan.</p>
     *
     * <p>callbackQuery null yoki callback_query.id null/blank bo'lsa,
     * acknowledgement service ichida silent skip qilinadi.</p>
     */
    private ExecutionOutcome acknowledgeAndReturn(ExecutionOutcome outcome,
                                                    TelegramCallbackQueryRequest callbackQuery) {
        // Phase 189: terminal outcome metric increment'i acknowledgement va
        // controller return'idan oldin yoziladi. Counter har bir execute()
        // chaqiruvi uchun bir martagina hisoblanadi.
        recordOutcomeCounter(outcome);
        try {
            String callbackQueryId = callbackQuery == null ? null : callbackQuery.id();
            String text = OUTCOME_ACKNOWLEDGE_TEXT.get(outcome);
            acknowledgementService.acknowledge(callbackQueryId, text);
        } catch (RuntimeException ex) {
            // Acknowledgement service o'zining fail-soft kontrakti bilan
            // exception tashlamasligi shart — bu yo'l defense-in-depth.
            log.warn("Telegram callback acknowledgement defensive swallow exceptionType={}",
                    ex.getClass().getSimpleName());
        }
        return outcome;
    }

    /**
     * Phase 189 — callback execution outcome uchun bitta counter increment.
     *
     * <p>Faqat {@link ExecutionOutcome#name()} tag sifatida ishlatiladi.
     * tenantId, workItemId, telegramUserId, actionCode, callbackQueryId —
     * hech qaysisi tag bo'la olmaydi (low-cardinality cheklov).</p>
     */
    private void recordOutcomeCounter(ExecutionOutcome outcome) {
        if (meterRegistry == null || outcome == null) {
            return;
        }
        Counter.builder(CALLBACK_OUTCOMES_METER)
                .tag("outcome", outcome.name())
                .register(meterRegistry)
                .increment();
    }

    private static Long extractTelegramUserId(TelegramCallbackQueryRequest cb) {
        if (cb == null || cb.from() == null) {
            return null;
        }
        return cb.from().id();
    }

    /**
     * Bounded log: faqat xavfsiz metadata. callback_data sub-string,
     * exception message, secret token, full payload hech qachon
     * log'ga chiqarilmaydi. {@code exceptionType} faqat
     * {@link ExecutionOutcome#UNEXPECTED_FAILURE} uchun to'ldiriladi.
     */
    private void logOutcome(ExecutionOutcome outcome,
                             TelegramCallbackQueryRequest cb,
                             Long telegramUserId,
                             UUID workItemId,
                             String actionCode,
                             UUID tenantId,
                             String targetStatusCode,
                             String exceptionType) {
        log.info("Telegram callback execute outcome={} callbackQueryId={} telegramUserId={} workItemId={} actionCode={} tenantId={} targetStatusCode={} exceptionType={}",
                outcome,
                cb == null ? null : cb.id(),
                telegramUserId,
                workItemId,
                actionCode,
                tenantId,
                targetStatusCode,
                exceptionType);
    }
}
