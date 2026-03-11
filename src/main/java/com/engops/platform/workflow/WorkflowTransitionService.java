package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.workflow.model.WorkItemTransition;
import com.engops.platform.workflow.repository.WorkItemTransitionRepository;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
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
 *
 * Bu generic BPM engine EMAS — aniq domain servisi.
 */
@Service
@Transactional
public class WorkflowTransitionService {

    private final WorkItemQueryService workItemQueryService;
    private final WorkItemCommandService workItemCommandService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final WorkItemTransitionRepository transitionRepository;
    private final AuditService auditService;

    public WorkflowTransitionService(WorkItemQueryService workItemQueryService,
                                      WorkItemCommandService workItemCommandService,
                                      TenantConfigQueryService tenantConfigQueryService,
                                      WorkItemTransitionRepository transitionRepository,
                                      AuditService auditService) {
        this.workItemQueryService = workItemQueryService;
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.transitionRepository = transitionRepository;
        this.auditService = auditService;
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

        return workItem;
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
