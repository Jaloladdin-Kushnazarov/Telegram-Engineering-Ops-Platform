package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.intake.PreparedDeliveryTarget;
import com.engops.platform.intake.ProjectionAssembler;
import com.engops.platform.intake.ProjectionPayload;
import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramCardDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import com.engops.platform.telegram.TelegramCardViewService;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.workflow.model.WorkItemTransition;
import com.engops.platform.workflow.repository.WorkItemTransitionRepository;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Workflow o'tish (transition) servisi.
 *
 * Cross-module bog'lanishlar:
 * - WorkItemQueryService — work item o'qish uchun (public API)
 * - WorkItemCommandService — work item saqlash uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 * - AuditService — audit yozish uchun (public API)
 * - RoutingDecisionService — Phase 161, transition'dan keyin Telegram
 *   notification target'ini qayta resolve qilish uchun (WorkItem'da
 *   routing target field'i saqlanmaydi — har transition uchun joriy
 *   konfiguratsiyaga ko'ra qayta hisoblanadi, intake bilan bir xil pattern)
 * - ProjectionAssembler — Phase 161, intake module'ning public DTO
 *   assembler'i (PreparedDeliveryTarget → ProjectionPayload)
 * - TelegramCardViewService — Phase 161, telegram module'ning public API'si
 * - TelegramCardDispatchService — Phase 161, telegram module'ning public
 *   dispatch facade'i (render + outbound + persisted attempt)
 *
 * Bu generic BPM engine EMAS — aniq domain servisi.
 *
 * <p><strong>Phase 161 — Telegram update card dispatch:</strong> work item
 * status muvaffaqiyatli o'tkazilib, audit yozilgandan keyin, agar joriy
 * routing konfiguratsiyasi target taklif qilsa, Telegram dispatch zanjiri
 * ishga tushadi: {@code workItem (yangi status) → RoutingDecisionService
 * → PreparedDeliveryTarget → ProjectionPayload → TelegramCardView →
 * TelegramCardDispatchService.dispatchAttempt()}. Bu YANGI sendMessage
 * attempt sifatida amalga oshiriladi (editMessageText emas — Telegram
 * message_id work item'ga bog'lanmagan). Append-only delivery attempt
 * jadvalida transition uchun yangi row paydo bo'ladi.</p>
 *
 * <p><strong>Fail-soft kontrakt:</strong> work item status transition =
 * manba-haqiqat. Telegram notification = projection/side-effect. Routing
 * resolution failure, dispatch reject/failure yoki kutilmagan
 * {@link RuntimeException} transition natijasini buzmaydi va rollback
 * qilmaydi. Faqat bounded metadata ({@code tenantId}, {@code workItemId},
 * {@code targetStatusCode}, {@code exceptionType}) log qilinadi —
 * exception message log'ga chiqarilmaydi (Phase 160 mini-fix bilan
 * o'rnatilgan token-leak hardening pattern).</p>
 *
 * <p><strong>Out of scope (Phase 161 da TEGILMAYDI):</strong> editMessageText
 * (Telegram message_id work item modelida saqlanmaydi); retry/backoff;
 * inbound webhook/callback handling; parse_mode rendering; async/event
 * pattern; Telegram gateway o'zgarishi.</p>
 */
@Service
@Transactional
public class WorkflowTransitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTransitionService.class);

    private final WorkItemQueryService workItemQueryService;
    private final WorkItemCommandService workItemCommandService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final WorkItemTransitionRepository transitionRepository;
    private final AuditService auditService;
    private final OperationalAuthorizationService operationalAuthorizationService;
    private final RoutingDecisionService routingDecisionService;
    private final ProjectionAssembler projectionAssembler;
    private final TelegramCardViewService telegramCardViewService;
    private final TelegramCardDispatchService telegramCardDispatchService;

    public WorkflowTransitionService(WorkItemQueryService workItemQueryService,
                                      WorkItemCommandService workItemCommandService,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      WorkItemTransitionRepository transitionRepository,
                                      AuditService auditService,
                                      OperationalAuthorizationService operationalAuthorizationService,
                                      RoutingDecisionService routingDecisionService,
                                      ProjectionAssembler projectionAssembler,
                                      TelegramCardViewService telegramCardViewService,
                                      TelegramCardDispatchService telegramCardDispatchService) {
        this.workItemQueryService = workItemQueryService;
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.transitionRepository = transitionRepository;
        this.auditService = auditService;
        this.operationalAuthorizationService = operationalAuthorizationService;
        this.routingDecisionService = routingDecisionService;
        this.projectionAssembler = projectionAssembler;
        this.telegramCardViewService = telegramCardViewService;
        this.telegramCardDispatchService = telegramCardDispatchService;
    }

    /**
     * Work item holatini o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param targetStatusCode maqsad holat kodi
     * @param actorUserId amal bajaruvchi foydalanuvchi
     * @param actionSource amal manbai (MANUAL, SYSTEM, TELEGRAM va h.k.)
     * @param reason o'tish sababi (ixtiyoriy)
     * @return yangilangan work item
     * @throws BusinessRuleException agar o'tish ruxsat etilmagan bo'lsa
     */
    public WorkItem transition(UUID tenantId, UUID workItemId, String targetStatusCode,
                                UUID actorUserId, String actionSource, String reason) {
        WorkItem workItem = workItemQueryService.findByTenantAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));

        // Phase 139: tenant-safe lookup'dan keyin (404 noto'g'ri tenant-workItem
        // juftligi uchun saqlanadi), lekin transition validatsiya/mutation/audit'dan
        // OLDIN — actor WORK_ITEM_TRANSITION ruxsatiga ega bo'lishi shart.
        operationalAuthorizationService.authorizeTransition(tenantId, actorUserId);

        String fromStatus = workItem.getCurrentStatusCode();
        UUID definitionId = workItem.getWorkflowDefinitionId();

        if (fromStatus.equals(targetStatusCode)) {
            throw new BusinessRuleException("SAME_STATUS",
                    "Work item allaqachon '" + targetStatusCode + "' holatida");
        }

        // Workflow definition va transition rule'larni yuklash (facade orqali, tenant-safe)
        WorkflowDefinition definition = tenantConfigQueryService
                .findWorkflowDefinitionById(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", definitionId));

        // O'tish ruxsat etilganligini tekshirish
        validateTransition(definition, fromStatus, targetStatusCode);

        // Terminal holatga qaytish — reopen
        boolean isReopen = isReopenTransition(definition, fromStatus, targetStatusCode);

        // Status o'tkazish
        workItem.transitionTo(targetStatusCode);
        workItem.setUpdatedByUserId(actorUserId);

        // Terminal holatga o'tsa — resolved deb belgilash
        if (isTerminalStatus(definition, targetStatusCode)) {
            workItem.markResolved();
        }

        // Reopen bo'lsa — reopenedCount oshirish
        if (isReopen) {
            workItem.markReopened();
        }

        workItem = workItemCommandService.save(workItem);

        // Transition tarixini yozish
        WorkItemTransition transition = new WorkItemTransition(
                tenantId, workItemId, fromStatus, targetStatusCode, actorUserId, actionSource);
        transition.setTransitionReason(reason);
        transitionRepository.save(transition);

        // Audit yozish (actionSource bilan)
        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "STATUS_TRANSITION", actorUserId, actionSource, fromStatus, targetStatusCode);

        // Phase 161: Telegram update card outbound dispatch — fail-soft side-effect.
        // Status transition allaqachon yozilgan (manba-haqiqat). Dispatch
        // muvaffaqiyatli qaytsa (SUCCESS / REJECTED / FAILED outcome bilan
        // attempt qaytarsa), TelegramCardDispatchService ichidagi
        // attemptPersistence orqali telegram_delivery_attempt jadvaliga
        // yoziladi va observability admin endpoint'larida ko'rinadi.
        // Dispatch boundary'idan kutilmagan RuntimeException uchun persistence
        // KAFOLATLANMAYDI — exception qaysi qadamda tashlanganiga bog'liq;
        // transition baribir muvaffaqiyatli qaytadi (fail-soft).
        dispatchTelegramCardSafely(workItem, targetStatusCode);

        return workItem;
    }

    /**
     * Phase 161 — fail-soft Telegram update card dispatch boundary.
     *
     * <p>Routing target qayta resolve qilinadi (intake bilan bir xil pattern):
     * {@code workItem.getTenantId()} + {@code workItem.getTypeCode()} bo'yicha
     * {@link RoutingDecisionService}. Agar mos rule yo'q yoki target
     * yetishmagan bo'lsa, Telegram chaqiruvi umuman bo'lmaydi va transition
     * baribir muvaffaqiyatli qaytadi.</p>
     *
     * <p>Routing prepared bo'lsa: yangi {@link PreparedDeliveryTarget}
     * (tenantId + workItem identity + yangi statusCode + resolved chat/topic)
     * quriladi → {@link ProjectionAssembler#assemble(PreparedDeliveryTarget)}
     * → {@link TelegramCardViewService#buildCardView(ProjectionPayload)}
     * → {@link TelegramCardDispatchService#dispatchAttempt(TelegramCardView)}.
     * Append-only delivery attempt yangi row sifatida {@code telegram_delivery_attempt}
     * jadvaliga yoziladi.</p>
     *
     * <p>Kutilmagan {@link RuntimeException} (routing exception, rendering
     * xatosi, collaborator null qaytarishi va h.k.) ushlanadi va transition
     * natijasini buzmaydi. Log faqat <em>bounded metadata</em> chiqaradi:
     * {@code tenantId}, {@code workItemId}, {@code targetStatusCode},
     * {@code exceptionType} (exception simple class name).
     * {@code ex.getMessage()} ataylab log qilinmaydi — workflow boundary
     * Telegram bot token konfiguratsiyasi haqida hech narsa bilmaydi va
     * exception message ichidagi har qanday secret sub-string'ining log'ga
     * sizib chiqishini oldini oladi (Phase 160 mini-fix logging hardening
     * pattern).</p>
     *
     * <p><strong>Diqqat:</strong> bu metod work item save + transition
     * history + audit'dan KEYIN chaqiriladi. Telegram dispatch failure
     * outer transaction'ni rollback qilmasligi shart.</p>
     */
    private void dispatchTelegramCardSafely(WorkItem workItem, String targetStatusCode) {
        try {
            RoutingDecision routing = routingDecisionService.resolve(
                    workItem.getTenantId(), workItem.getTypeCode().name());
            if (!routing.isPrepared()) {
                return;
            }
            PreparedDeliveryTarget target = new PreparedDeliveryTarget(
                    workItem.getTenantId(),
                    workItem.getId(),
                    workItem.getWorkItemCode(),
                    workItem.getTypeCode().name(),
                    workItem.getTitle(),
                    workItem.getCurrentStatusCode(),
                    true,
                    routing.getTargetChatBindingId(),
                    routing.getTargetTopicId());
            ProjectionPayload payload = projectionAssembler.assemble(target);
            TelegramCardView cardView = telegramCardViewService.buildCardView(payload);
            telegramCardDispatchService.dispatchAttempt(cardView);
        } catch (RuntimeException ex) {
            log.warn("Telegram card dispatch failed (fail-soft) tenantId={} workItemId={} targetStatusCode={} exceptionType={}",
                    workItem.getTenantId(), workItem.getId(), targetStatusCode,
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Berilgan o'tish ruxsat etilganligini tekshiradi.
     */
    private void validateTransition(WorkflowDefinition definition,
                                     String fromStatusCode, String toStatusCode) {
        List<WorkflowTransitionRule> rules = definition.getTransitionRules();

        boolean allowed = rules.stream()
                .anyMatch(rule ->
                        rule.getFromStatus().getName().equals(fromStatusCode) &&
                        rule.getToStatus().getName().equals(toStatusCode));

        if (!allowed) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "'" + fromStatusCode + "' dan '" + toStatusCode + "' ga o'tish ruxsat etilmagan");
        }
    }

    private boolean isReopenTransition(WorkflowDefinition definition,
                                        String fromStatusCode, String toStatusCode) {
        boolean fromTerminal = isTerminalStatus(definition, fromStatusCode);
        boolean toTerminal = isTerminalStatus(definition, toStatusCode);
        return fromTerminal && !toTerminal;
    }

    private boolean isTerminalStatus(WorkflowDefinition definition, String statusCode) {
        return definition.getStatuses().stream()
                .anyMatch(s -> s.getName().equals(statusCode) && s.isTerminal());
    }

    @Transactional(readOnly = true)
    public List<WorkItemTransition> getTransitionHistory(UUID tenantId, UUID workItemId) {
        return transitionRepository.findByTenantIdAndWorkItemId(tenantId, workItemId);
    }
}
