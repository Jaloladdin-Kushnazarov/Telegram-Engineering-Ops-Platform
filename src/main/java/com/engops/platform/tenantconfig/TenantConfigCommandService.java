package com.engops.platform.tenantconfig;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun buyruq (command) servisi — yaratish, o'zgartirish operatsiyalari.
 *
 * Cross-module bog'lanishlar:
 * - AuditService — audit yozish uchun (public API)
 *
 * Muhim:
 * - Faqat tenant-config module'ning o'z repository'laridan foydalanadi
 * - Read-only operatsiyalar TenantConfigQueryService orqali
 * - Bu servis write tranzaksiyasini own qiladi
 */
@Service
@Transactional
public class TenantConfigCommandService {

    private final TenantRepository tenantRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final AuditService auditService;

    public TenantConfigCommandService(TenantRepository tenantRepository,
                                       WorkflowDefinitionRepository workflowDefinitionRepository,
                                       AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.auditService = auditService;
    }

    /**
     * Yangi workflow definition yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Tenant ichida workflow nomi unikal bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param name workflow nomi
     * @param workItemType work item turi (BUG, INCIDENT, TASK)
     * @param description ixtiyoriy tavsif (nullable)
     * @return yaratilgan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar shu nomli workflow allaqachon mavjud bo'lsa
     */
    public WorkflowDefinition createWorkflowDefinition(UUID tenantId, String name,
                                                         String workItemType, String description) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        workflowDefinitionRepository.findByTenantIdAndName(tenantId, name)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                            "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
                });

        WorkflowDefinition definition = new WorkflowDefinition(tenantId, name, workItemType);
        if (description != null && !description.isBlank()) {
            definition.setDescription(description);
        }

        try {
            definition = workflowDefinitionRepository.save(definition);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return definition;
    }

    /**
     * DataIntegrityViolationException workflow_definition (tenant_id, name) unique
     * constraint violation ekanligini tekshiradi.
     *
     * PostgreSQL auto-generated constraint nomi: workflow_definition_tenant_id_name_key
     */
    private static boolean isDuplicateNameConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_definition")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("name");
        }
        return false;
    }
}
