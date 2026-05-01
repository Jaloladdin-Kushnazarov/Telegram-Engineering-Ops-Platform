package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Workflow status uchun repository. Workflow definition orqali tenant-scoped.
 *
 * Pre-check va DB unique constraint translation pattern uchun:
 * - existsByWorkflowDefinition_IdAndName — duplicate status nomi pre-check uchun
 *   (DB constraint: UNIQUE (workflow_definition_id, name))
 * - existsByWorkflowDefinition_IdAndInitialTrue — bitta workflow definition'da
 *   ikkita initial status bo'lmasligi uchun application-level pre-check uchun
 *   (DB darajasida partial unique index YO'Q — concurrency hardening keyingi
 *   migration phase'iga qoldirilgan, faqat app-level enforce qilinadi)
 *
 * Tenant-safe lookup uchun:
 * - findByIdAndWorkflowDefinition_IdAndWorkflowDefinition_TenantId — status'ni
 *   bitta SQL'da (tenant + workflow definition) scope ichida topadi.
 *   Cross-tenant yoki cross-definition status — Optional.empty (404 sifatida
 *   ko'rsatish uchun caller orElseThrow qiladi). In-memory scope check qilinmaydi.
 */
@Repository
public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {

    boolean existsByWorkflowDefinition_IdAndName(UUID workflowDefinitionId, String name);

    boolean existsByWorkflowDefinition_IdAndInitialTrue(UUID workflowDefinitionId);

    Optional<WorkflowStatus> findByIdAndWorkflowDefinition_IdAndWorkflowDefinition_TenantId(
            UUID statusId, UUID workflowDefinitionId, UUID tenantId);
}
