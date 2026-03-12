package com.engops.platform.workitem;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * WorkItem buyruq (command) servisi — yaratish, owner tayinlash, yangilash.
 * Status o'tkazish WorkflowTransitionService orqali amalga oshiriladi.
 *
 * Cross-module bog'lanishlar:
 * - IdentityQueryService — membership tekshiruvi uchun (public API)
 * - TenantConfigQueryService — workflow definition olish uchun (public API)
 * - AuditService — audit yozish uchun (public API)
 */
@Service
@Transactional
public class WorkItemCommandService {

    private final WorkItemRepository workItemRepository;
    private final WorkItemUpdateRepository workItemUpdateRepository;
    private final WorkItemCodeGenerator codeGenerator;
    private final AuditService auditService;
    private final TenantConfigQueryService tenantConfigQueryService;
    private final IdentityQueryService identityQueryService;

    public WorkItemCommandService(WorkItemRepository workItemRepository,
                                   WorkItemUpdateRepository workItemUpdateRepository,
                                   WorkItemCodeGenerator codeGenerator,
                                   AuditService auditService,
                                   TenantConfigQueryService tenantConfigQueryService,
                                   IdentityQueryService identityQueryService) {
        this.workItemRepository = workItemRepository;
        this.workItemUpdateRepository = workItemUpdateRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.identityQueryService = identityQueryService;
    }

    /**
     * Yangi work item yaratadi.
     *
     * @param description ixtiyoriy tavsif (nullable)
     *
     * Validatsiyalar:
     * 1. Workflow definition tenant ga tegishli bo'lishi kerak
     * 2. Workflow definition work item type ga mos kelishi kerak
     * 3. initialStatusCode workflow definition ichida bo'lishi kerak
     * 4. initialStatusCode initial=true deb belgilangan bo'lishi kerak
     */
    public WorkItem create(UUID tenantId, WorkItemType typeCode, UUID workflowDefinitionId,
                            String title, String description, String initialStatusCode,
                            UUID createdByUserId, String actionSource) {
        // Workflow definition tenant-safe tekshiruv (facade orqali)
        WorkflowDefinition definition = tenantConfigQueryService
                .findWorkflowDefinitionById(tenantId, workflowDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "WorkflowDefinition", workflowDefinitionId));

        // Workflow active tekshiruvi
        if (!definition.isActive()) {
            throw new BusinessRuleException("INACTIVE_WORKFLOW",
                    "Workflow '" + definition.getName() + "' aktiv emas. "
                            + "Faqat aktiv workflow bilan work item yaratish mumkin");
        }

        // Workflow type va WorkItem type mosligi tekshiruvi
        validateWorkflowTypeCompatibility(definition, typeCode);

        // Initial status validatsiya
        validateInitialStatus(definition, initialStatusCode);

        String code = codeGenerator.generate(tenantId, typeCode);

        WorkItem workItem = new WorkItem(tenantId, code, typeCode, workflowDefinitionId,
                title, initialStatusCode, createdByUserId);

        if (description != null && !description.isBlank()) {
            workItem.setDescription(description);
        }

        workItem = workItemRepository.save(workItem);

        auditService.recordEvent(tenantId, "WORK_ITEM", workItem.getId(),
                "CREATED", createdByUserId, actionSource, null, code);

        return workItem;
    }

    /**
     * Work item'ga owner tayinlaydi.
     *
     * Validatsiya: owner shu tenantda active membership ga ega bo'lishi kerak.
     */
    public WorkItem assignOwner(UUID tenantId, UUID workItemId, UUID ownerUserId,
                                 UUID actorUserId, String actionSource) {
        WorkItem workItem = findWorkItem(tenantId, workItemId);

        // Owner membership validatsiya (facade orqali)
        validateActiveMembership(tenantId, ownerUserId);

        UUID previousOwner = workItem.getCurrentOwnerUserId();
        workItem.assignOwner(ownerUserId);
        workItem.setUpdatedByUserId(actorUserId);

        workItem = workItemRepository.save(workItem);

        workItemUpdateRepository.save(new WorkItemUpdate(
                tenantId, workItemId, actorUserId, UpdateType.ASSIGNMENT,
                "Owner tayinlandi: " + ownerUserId));

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "OWNER_ASSIGNED", actorUserId, actionSource,
                previousOwner != null ? previousOwner.toString() : null,
                ownerUserId.toString());

        return workItem;
    }

    /**
     * Work item'ga tizimli yangilanish (izoh) qo'shadi.
     */
    public WorkItemUpdate addUpdate(UUID tenantId, UUID workItemId, UUID authorUserId,
                                     UpdateType updateType, String body, String actionSource) {
        findWorkItem(tenantId, workItemId);

        WorkItemUpdate update = new WorkItemUpdate(tenantId, workItemId, authorUserId,
                updateType, body);

        update = workItemUpdateRepository.save(update);

        auditService.recordEvent(tenantId, "WORK_ITEM", workItemId,
                "UPDATE_ADDED", authorUserId, actionSource,
                null, updateType.name());

        return update;
    }

    /**
     * Workflow definition va WorkItem type mosligi tekshiruvi.
     * Agar workflow definition boshqa type uchun yaratilgan bo'lsa — rad etiladi.
     */
    private void validateWorkflowTypeCompatibility(WorkflowDefinition definition,
                                                     WorkItemType typeCode) {
        if (!definition.getWorkItemType().equals(typeCode.name())) {
            throw new BusinessRuleException("WORKFLOW_TYPE_MISMATCH",
                    "Workflow '" + definition.getName() + "' turi '"
                            + definition.getWorkItemType() + "', lekin work item turi '"
                            + typeCode.name() + "'. Mos kelmaydi");
        }
    }

    /**
     * Initial status ni validatsiya qiladi:
     * 1. Status workflow definition ichida mavjud bo'lishi kerak
     * 2. Status initial=true deb belgilangan bo'lishi kerak
     */
    private void validateInitialStatus(WorkflowDefinition definition, String statusCode) {
        Optional<WorkflowStatus> statusOpt = definition.getStatuses().stream()
                .filter(s -> s.getName().equals(statusCode))
                .findFirst();

        if (statusOpt.isEmpty()) {
            throw new BusinessRuleException("INVALID_INITIAL_STATUS",
                    "'" + statusCode + "' statusi '" + definition.getName()
                            + "' workflow ta'rifida topilmadi");
        }

        if (!statusOpt.get().isInitial()) {
            throw new BusinessRuleException("NOT_INITIAL_STATUS",
                    "'" + statusCode + "' statusi boshlang'ich holat emas. "
                            + "Faqat initial=true belgilangan status bilan yaratish mumkin");
        }
    }

    /**
     * Foydalanuvchining shu tenantda active membership ga ega ekanligini tekshiradi
     * (IdentityQueryService facade orqali).
     */
    private void validateActiveMembership(UUID tenantId, UUID userId) {
        if (!identityQueryService.hasActiveMembership(tenantId, userId)) {
            throw new BusinessRuleException("INVALID_OWNER",
                    "Foydalanuvchi (id=" + userId + ") shu tenantda faol a'zo emas");
        }
    }

    /**
     * Work item'ni saqlaydi.
     * Boshqa modullar work item holatini o'zgartirgandan keyin persist qilish uchun ishlatiladi.
     */
    public WorkItem save(WorkItem workItem) {
        return workItemRepository.save(workItem);
    }

    private WorkItem findWorkItem(UUID tenantId, UUID workItemId) {
        return workItemRepository.findByTenantIdAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));
    }
}
