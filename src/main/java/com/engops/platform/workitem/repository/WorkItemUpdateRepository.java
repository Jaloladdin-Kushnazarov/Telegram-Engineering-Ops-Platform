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

    /**
     * Tenant va work item uchun update'larni deterministic tartibda qaytaradi.
     *
     * Ordering: createdAt ASC, id ASC.
     * - createdAt ASC: xronologik (eng eski birinchi)
     * - id ASC: bir xil createdAt bo'lganda deterministic tie-breaker
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @return tartibdagi update'lar ro'yxati
     */
    List<WorkItemUpdate> findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID workItemId);
}
