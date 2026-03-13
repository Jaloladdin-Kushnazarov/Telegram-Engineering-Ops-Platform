package com.engops.platform.intake;

import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Intake application servisi — yangi work item yaratish uchun yagona kirish nuqtasi.
 *
 * Tashqi adapterlar (Telegram, REST, integration) shu servis orqali ishlaydi.
 * Bu servis:
 * 1. Kiruvchi commandni validatsiya qiladi
 * 2. Workflow definitionni aniqlaydi (explicit yoki auto-resolve)
 * 3. Initial statusni aniqlaydi (explicit yoki auto-resolve)
 * 4. WorkItemCommandService orqali work item yaratadi
 * 5. RoutingDecisionService orqali routing qarorini oladi
 * 6. Structured natija qaytaradi (work item + routing info)
 *
 * Cross-module bog'lanishlar:
 * - WorkItemCommandService — work item yaratish uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 * - RoutingDecisionService — routing qarori olish uchun (public API)
 */
@Service
@Transactional
public class IntakeApplicationService {

    private final WorkItemCommandService workItemCommandService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final RoutingDecisionService routingDecisionService;

    public IntakeApplicationService(WorkItemCommandService workItemCommandService,
                                     TenantConfigQueryService tenantConfigQueryService,
                                     RoutingDecisionService routingDecisionService) {
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.routingDecisionService = routingDecisionService;
    }

    /**
     * Yangi work item yaratadi intake command asosida.
     *
     * @param command intake buyrug'i
     * @return yaratilgan work item haqida structured natija
     */
    public IntakeResult submit(IntakeCommand command) {
        validateCommand(command);

        // Workflow definition aniqlash
        WorkflowDefinition definition = resolveWorkflowDefinition(command);

        // Initial status aniqlash
        String initialStatusCode = resolveInitialStatus(command, definition);

        // WorkItemCommandService orqali yaratish (domain validatsiya u yerda bo'ladi)
        WorkItem workItem = workItemCommandService.create(
                command.getTenantId(),
                command.getTypeCode(),
                definition.getId(),
                command.getTitle(),
                command.getDescription(),
                initialStatusCode,
                command.getCreatedByUserId(),
                command.getActionSource());

        // Routing decision — RoutingDecisionService orqali (side effect yo'q)
        RoutingDecision routing = routingDecisionService.resolve(
                command.getTenantId(), workItem.getTypeCode().name());

        return new IntakeResult(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getCurrentStatusCode(),
                workItem.getWorkflowDefinitionId(),
                workItem.getTenantId(),
                routing.isPrepared(),
                routing.getMatchedRoutingRuleId(),
                routing.getTargetTopicBindingId(),
                routing.getTargetChatBindingId(),
                routing.getTargetTopicId());
    }

    /**
     * Command invariantlarini tekshiradi.
     * Bu application-level validatsiya — domain rule'lar WorkItemCommandService ichida qoladi.
     */
    private void validateCommand(IntakeCommand command) {
        if (command.getTenantId() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "tenantId majburiy");
        }
        if (command.getTypeCode() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "typeCode majburiy");
        }
        if (command.getTitle() == null || command.getTitle().isBlank()) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "title bo'sh bo'lishi mumkin emas");
        }
        if (command.getCreatedByUserId() == null) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "createdByUserId majburiy");
        }
        if (command.getActionSource() == null || command.getActionSource().isBlank()) {
            throw new BusinessRuleException("INTAKE_VALIDATION",
                    "actionSource bo'sh bo'lishi mumkin emas");
        }
    }

    /**
     * Workflow definitionni aniqlaydi:
     * - Agar command'da workflowDefinitionId berilgan bo'lsa — tenant-safe lookup qiladi
     * - Agar berilmagan bo'lsa — tenant va typeCode bo'yicha active workflow topadi
     */
    private WorkflowDefinition resolveWorkflowDefinition(IntakeCommand command) {
        if (command.getWorkflowDefinitionId() != null) {
            return tenantConfigQueryService
                    .findWorkflowDefinitionById(command.getTenantId(), command.getWorkflowDefinitionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "WorkflowDefinition", command.getWorkflowDefinitionId()));
        }

        // Type bo'yicha active workflow auto-resolve (deterministic: 0→fail, 1→use, >1→fail)
        List<WorkflowDefinition> activeWorkflows = tenantConfigQueryService
                .findActiveWorkflowDefinitionsByType(command.getTenantId(), command.getTypeCode().name());

        if (activeWorkflows.isEmpty()) {
            throw new BusinessRuleException("NO_ACTIVE_WORKFLOW",
                    "'" + command.getTypeCode() + "' turi uchun aktiv workflow ta'rifi topilmadi");
        }

        if (activeWorkflows.size() > 1) {
            throw new BusinessRuleException("AMBIGUOUS_WORKFLOW",
                    "'" + command.getTypeCode() + "' turi uchun " + activeWorkflows.size()
                            + " ta aktiv workflow topildi. workflowDefinitionId ni aniq ko'rsating");
        }

        return activeWorkflows.getFirst();
    }

    /**
     * Initial statusni aniqlaydi:
     * - Agar command'da initialStatusCode berilgan bo'lsa — shuni ishlatadi
     * - Agar berilmagan bo'lsa — workflow definition'dagi initial=true statusni topadi
     */
    private String resolveInitialStatus(IntakeCommand command, WorkflowDefinition definition) {
        if (command.getInitialStatusCode() != null && !command.getInitialStatusCode().isBlank()) {
            return command.getInitialStatusCode();
        }

        // Workflow definition'dan initial status auto-resolve
        List<WorkflowStatus> initialStatuses = definition.getStatuses().stream()
                .filter(WorkflowStatus::isInitial)
                .toList();

        if (initialStatuses.isEmpty()) {
            throw new BusinessRuleException("NO_INITIAL_STATUS",
                    "'" + definition.getName() + "' workflow ta'rifida boshlang'ich status topilmadi");
        }

        if (initialStatuses.size() > 1) {
            throw new BusinessRuleException("AMBIGUOUS_INITIAL_STATUS",
                    "'" + definition.getName() + "' workflow ta'rifida bir nechta boshlang'ich status mavjud. "
                            + "initialStatusCode ni aniq ko'rsating");
        }

        return initialStatuses.getFirst().getName();
    }
}
