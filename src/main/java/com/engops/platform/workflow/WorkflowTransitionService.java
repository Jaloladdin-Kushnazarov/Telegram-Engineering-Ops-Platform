package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.intake.PreparedDeliveryTarget;
import com.engops.platform.intake.TelegramCardDispatchRequested;
import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
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
import org.springframework.context.ApplicationEventPublisher;
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
 * - ApplicationEventPublisher — Phase 164, AFTER_COMMIT Telegram dispatch
 *   eventi publish qilish uchun (Spring infra)
 *
 * Bu generic BPM engine EMAS — aniq domain servisi.
 *
 * <p><strong>Phase 164 — Telegram dispatch AFTER_COMMIT'ga ko'chirildi:</strong>
 * work item status muvaffaqiyatli o'tkazilib, transition history va audit
 * yozilgandan keyin, agar joriy routing konfiguratsiyasi target taklif qilsa,
 * {@link TelegramCardDispatchRequested} eventi publish qilinadi.
 * {@link com.engops.platform.intake.TelegramCardDispatchEventListener}
 * eventni {@code @TransactionalEventListener(phase = AFTER_COMMIT)} bilan
 * iste'mol qiladi va Telegram render + outbound + delivery_attempt
 * persistence transaction commit'idan KEYIN bajaradi. Telegram HTTP I/O endi
 * workflow transaction'i ichida emas.</p>
 *
 * <p><strong>editMessageText IMPLEMENT QILINMAGAN:</strong> har transition
 * yangi {@code sendMessage} attempt sifatida amalga oshiriladi (Telegram
 * message_id work item'ga bog'lanmagan). Append-only delivery attempt
 * jadvalida transition uchun yangi row paydo bo'ladi.</p>
 *
 * <p><strong>Fail-soft kontrakt:</strong> work item status transition =
 * manba-haqiqat. Telegram notification = projection/side-effect. Routing
 * resolution failure yoki kutilmagan {@link RuntimeException} event publish
 * qadamida transition natijasini buzmaydi va rollback qilmaydi. Faqat
 * bounded metadata ({@code tenantId}, {@code workItemId},
 * {@code targetStatusCode}, {@code exceptionType}) log qilinadi —
 * exception message log'ga chiqarilmaydi (Phase 160 mini-fix
 * token-leak hardening pattern). Listener ichidagi dispatch xatolari
 * o'sha listener'ning fail-soft boundary'ida ushlanadi.</p>
 *
 * <p><strong>Out of scope (Phase 164 da TEGILMAYDI):</strong> retry/backoff;
 * inbound webhook/callback handling; parse_mode rendering;
 * {@code @Async}/scheduler/outbox; Telegram gateway o'zgarishi.</p>
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
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowTransitionService(WorkItemQueryService workItemQueryService,
                                      WorkItemCommandService workItemCommandService,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      WorkItemTransitionRepository transitionRepository,
                                      AuditService auditService,
                                      OperationalAuthorizationService operationalAuthorizationService,
                                      RoutingDecisionService routingDecisionService,
                                      ApplicationEventPublisher eventPublisher) {
        this.workItemQueryService = workItemQueryService;
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.transitionRepository = transitionRepository;
        this.auditService = auditService;
        this.operationalAuthorizationService = operationalAuthorizationService;
        this.routingDecisionService = routingDecisionService;
        this.eventPublisher = eventPublisher;
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

        // Phase 164: Telegram dispatch endi AFTER_COMMIT listener orqali —
        // bu yerda routing resolve qilinadi va event publish qilinadi.
        // Listener Telegram HTTP'ga workflow transaction commit'idan KEYIN
        // boradi. Routing prepared bo'lmasa event umuman publish bo'lmaydi.
        publishTelegramCardDispatchEventSafely(workItem, targetStatusCode);

        return workItem;
    }

    /**
     * Phase 164 — fail-soft Telegram dispatch event publish boundary.
     *
     * <p>Routing target qayta resolve qilinadi (intake bilan bir xil pattern):
     * {@code workItem.getTenantId()} + {@code workItem.getTypeCode()} bo'yicha
     * {@link RoutingDecisionService}. Agar mos rule yo'q yoki target
     * yetishmagan bo'lsa, hech qanday event publish qilinmaydi va transition
     * baribir muvaffaqiyatli qaytadi.</p>
     *
     * <p>Routing prepared bo'lsa: yangi {@link PreparedDeliveryTarget}
     * (tenantId + workItem identity + yangi statusCode + resolved chat/topic)
     * quriladi va {@link TelegramCardDispatchRequested} event sifatida
     * {@link ApplicationEventPublisher} orqali publish qilinadi. Spring eventni
     * transaction commit'igacha saqlaydi va keyin
     * {@code TelegramCardDispatchEventListener}'ga uzatadi (AFTER_COMMIT).</p>
     *
     * <p>Kutilmagan {@link RuntimeException} (routing exception, event publish
     * infrastructure xatosi va h.k.) ushlanadi va transition natijasini
     * buzmaydi. Log faqat <em>bounded metadata</em> chiqaradi:
     * {@code tenantId}, {@code workItemId}, {@code targetStatusCode},
     * {@code exceptionType}. {@code ex.getMessage()} ataylab log qilinmaydi
     * (Phase 160 mini-fix logging hardening pattern).</p>
     *
     * <p><strong>Diqqat:</strong> bu metod work item save + transition history
     * + audit'dan KEYIN chaqiriladi. publishEvent transaction ichida bo'ladi
     * (Spring eventni queue qiladi va transaction commit'idan keyin
     * listener'ga uzatadi). Transaction rollback bo'lsa listener umuman
     * chaqirilmaydi.</p>
     */
    private void publishTelegramCardDispatchEventSafely(WorkItem workItem, String targetStatusCode) {
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
            eventPublisher.publishEvent(new TelegramCardDispatchRequested(
                    target,
                    TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION,
                    targetStatusCode));
        } catch (RuntimeException ex) {
            log.warn("Telegram card dispatch event publish failed (fail-soft) tenantId={} workItemId={} targetStatusCode={} exceptionType={}",
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
