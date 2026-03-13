package com.engops.platform.intake;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.RoutingRule;
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
 * 5. Routing preparation — tenant config asosida mos routing rule topadi
 * 6. Structured natija qaytaradi (work item + routing info)
 *
 * Cross-module bog'lanishlar:
 * - WorkItemCommandService — work item yaratish uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 */
@Service
@Transactional
public class IntakeApplicationService {

    private final WorkItemCommandService workItemCommandService;
    private final TenantConfigQueryService tenantConfigQueryService;

    public IntakeApplicationService(WorkItemCommandService workItemCommandService,
                                     TenantConfigQueryService tenantConfigQueryService) {
        this.workItemCommandService = workItemCommandService;
        this.tenantConfigQueryService = tenantConfigQueryService;
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

        // Routing preparation — mos routing rule topish (side effect yo'q)
        RoutingPreparation routing = prepareRouting(
                command.getTenantId(), workItem.getTypeCode().name());

        return new IntakeResult(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getCurrentStatusCode(),
                workItem.getWorkflowDefinitionId(),
                workItem.getTenantId(),
                routing.prepared,
                routing.routingRuleId,
                routing.targetTopicBindingId);
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

    /**
     * Routing preparation — work item turi bo'yicha mos unconditional routing rule topadi.
     *
     * Phase 4.2 da faqat unconditional rule'lar (conditionExpression == null yoki blank)
     * candidate sifatida qatnashadi. Conditional rule'lar evaluate qilinmaydi —
     * ular full routing engine phase'da ishga tushiriladi.
     *
     * Deterministic selection policy (unconditional candidatelar orasida):
     * - 0 ta mos rule → routingPrepared=false (valid, faqat rule yo'q)
     * - 1 ta mos rule → shu ishlatiladi
     * - N ta mos rule → eng yuqori priority tanlanadi (DESC tartibda birinchi)
     * - Agar 2+ rule bir xil eng yuqori priority'ga ega → fail-fast (noaniqlik)
     *
     * Side effect yo'q — faqat query va selection.
     */
    private RoutingPreparation prepareRouting(UUID tenantId, String workItemType) {
        List<RoutingRule> activeRules = tenantConfigQueryService
                .findActiveRoutingRulesByType(tenantId, workItemType);

        // Faqat unconditional rule'lar — conditionExpression null yoki blank
        List<RoutingRule> candidates = activeRules.stream()
                .filter(rule -> rule.getConditionExpression() == null
                        || rule.getConditionExpression().isBlank())
                .toList();

        if (candidates.isEmpty()) {
            return RoutingPreparation.none();
        }

        // candidates allaqachon priority DESC tartibda (repository query tartibini saqlab qoladi)
        RoutingRule topRule = candidates.getFirst();

        // Noaniqlik tekshiruvi: agar 2+ unconditional rule bir xil eng yuqori priority'ga ega bo'lsa
        if (candidates.size() > 1) {
            RoutingRule secondRule = candidates.get(1);
            if (topRule.getPriority() == secondRule.getPriority()) {
                throw new BusinessRuleException("AMBIGUOUS_ROUTING",
                        "'" + workItemType + "' turi uchun " + countRulesWithPriority(candidates, topRule.getPriority())
                                + " ta unconditional routing rule bir xil prioritetga (=" + topRule.getPriority()
                                + ") ega. Prioritetlarni aniqlashtiring");
            }
        }

        return RoutingPreparation.matched(topRule.getId(), topRule.getTargetTopicBindingId());
    }

    private long countRulesWithPriority(List<RoutingRule> rules, int priority) {
        return rules.stream().filter(r -> r.getPriority() == priority).count();
    }

    /**
     * Routing preparation ichki natijasi — service ichida ishlatiladi.
     */
    private record RoutingPreparation(boolean prepared, UUID routingRuleId, UUID targetTopicBindingId) {

        static RoutingPreparation none() {
            return new RoutingPreparation(false, null, null);
        }

        static RoutingPreparation matched(UUID routingRuleId, UUID targetTopicBindingId) {
            return new RoutingPreparation(true, routingRuleId, targetTopicBindingId);
        }
    }
}
