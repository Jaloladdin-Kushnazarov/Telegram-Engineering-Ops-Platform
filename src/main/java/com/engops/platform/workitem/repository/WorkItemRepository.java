package com.engops.platform.workitem.repository;

import com.engops.platform.workitem.model.WorkItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkItem uchun repository. Barcha so'rovlar tenant-scoped.
 */
@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

    Optional<WorkItem> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<WorkItem> findByTenantIdAndWorkItemCode(UUID tenantId, String workItemCode);

    List<WorkItem> findByTenantIdAndCurrentStatusCode(UUID tenantId, String statusCode);

    List<WorkItem> findByTenantIdAndTypeCode(UUID tenantId, com.engops.platform.workitem.model.WorkItemType typeCode);

    List<WorkItem> findByTenantIdAndCurrentOwnerUserId(UUID tenantId, UUID ownerUserId);

    List<WorkItem> findByTenantIdAndArchivedFalse(UUID tenantId);

    /**
     * Tenant uchun aktiv work item'larni openedAt DESC, id DESC tartibda qaytaradi.
     *
     * Deterministic ordering:
     * - openedAt DESC: eng yangi ochilgan birinchi
     * - id DESC: bir xil openedAt bo'lganda deterministic tie-breaker
     *
     * Pageable orqali natija soni cheklanadi (limit).
     *
     * @param tenantId tenant identifikatori
     * @param pageable limit uchun PageRequest
     * @return aktiv work item'lar, openedAt DESC, id DESC
     */
    List<WorkItem> findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
            UUID tenantId, Pageable pageable);

    /**
     * Tenant ichida berilgan turdagi work item'lar sonini qaytaradi.
     * Code generatsiya uchun ishlatiladi.
     */
    @Query("SELECT COUNT(w) FROM WorkItem w WHERE w.tenantId = :tenantId AND w.typeCode = :typeCode")
    long countByTenantIdAndTypeCode(UUID tenantId, com.engops.platform.workitem.model.WorkItemType typeCode);
}
