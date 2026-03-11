package com.engops.platform.workitem.repository;

import com.engops.platform.workitem.model.WorkItemUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * WorkItemUpdate uchun repository.
 */
@Repository
public interface WorkItemUpdateRepository extends JpaRepository<WorkItemUpdate, UUID> {

    List<WorkItemUpdate> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);

    List<WorkItemUpdate> findByTenantIdAndWorkItemId(UUID tenantId, UUID workItemId);
}
