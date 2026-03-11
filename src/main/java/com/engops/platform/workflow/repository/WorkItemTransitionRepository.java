package com.engops.platform.workflow.repository;

import com.engops.platform.workflow.model.WorkItemTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * WorkItemTransition uchun repository.
 */
@Repository
public interface WorkItemTransitionRepository extends JpaRepository<WorkItemTransition, UUID> {

    List<WorkItemTransition> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);

    List<WorkItemTransition> findByTenantIdAndWorkItemId(UUID tenantId, UUID workItemId);
}
