package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Workflow transition rule uchun repository. Workflow definition orqali tenant-scoped.
 *
 * Pre-check va DB unique constraint translation pattern uchun:
 * - existsByWorkflowDefinition_IdAndFromStatus_IdAndToStatus_Id —
 *   duplicate transition rule pre-check
 *   (DB constraint: UNIQUE (workflow_definition_id, from_status_id, to_status_id))
 */
@Repository
public interface WorkflowTransitionRuleRepository extends JpaRepository<WorkflowTransitionRule, UUID> {

    boolean existsByWorkflowDefinition_IdAndFromStatus_IdAndToStatus_Id(
            UUID workflowDefinitionId, UUID fromStatusId, UUID toStatusId);
}
