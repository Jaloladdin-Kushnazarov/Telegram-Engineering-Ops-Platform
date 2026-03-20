package com.engops.platform.workitem;

import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkItem so'rov servisi — faqat o'qish operatsiyalari.
 * Barcha so'rovlar tenant-scoped.
 */
@Service
@Transactional(readOnly = true)
public class WorkItemQueryService {

    private final WorkItemRepository workItemRepository;
    private final WorkItemUpdateRepository workItemUpdateRepository;

    public WorkItemQueryService(WorkItemRepository workItemRepository,
                                 WorkItemUpdateRepository workItemUpdateRepository) {
        this.workItemRepository = workItemRepository;
        this.workItemUpdateRepository = workItemUpdateRepository;
    }

    public Optional<WorkItem> findByTenantAndId(UUID tenantId, UUID workItemId) {
        return workItemRepository.findByTenantIdAndId(tenantId, workItemId);
    }

    public Optional<WorkItem> findByTenantAndCode(UUID tenantId, String workItemCode) {
        return workItemRepository.findByTenantIdAndWorkItemCode(tenantId, workItemCode);
    }

    public List<WorkItem> listByTenantAndStatus(UUID tenantId, String statusCode) {
        return workItemRepository.findByTenantIdAndCurrentStatusCode(tenantId, statusCode);
    }

    public List<WorkItem> listByTenantAndType(UUID tenantId, WorkItemType typeCode) {
        return workItemRepository.findByTenantIdAndTypeCode(tenantId, typeCode);
    }

    public List<WorkItem> listByTenantAndOwner(UUID tenantId, UUID ownerUserId) {
        return workItemRepository.findByTenantIdAndCurrentOwnerUserId(tenantId, ownerUserId);
    }

    public List<WorkItem> listActiveByTenant(UUID tenantId) {
        return workItemRepository.findByTenantIdAndArchivedFalse(tenantId);
    }

    /**
     * Tenant uchun aktiv work item'larni cheklangan sonda qaytaradi.
     *
     * Deterministic ordering: openedAt DESC, id DESC.
     * Eng yangi ochilgan work item'lar birinchi qaytadi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni
     * @return aktiv work item'lar, newest-opened-first
     */
    public List<WorkItem> listActiveByTenant(UUID tenantId, int limit) {
        return workItemRepository.findByTenantIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
                tenantId, PageRequest.of(0, limit));
    }

    /**
     * Tenant uchun berilgan statusdagi aktiv work item'larni cheklangan sonda qaytaradi.
     *
     * Deterministic ordering: openedAt DESC, id DESC.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni
     * @return aktiv work item'lar, newest-opened-first
     */
    public List<WorkItem> listActiveByTenantAndStatus(UUID tenantId, String statusCode, int limit) {
        return workItemRepository.findByTenantIdAndCurrentStatusCodeAndArchivedFalseOrderByOpenedAtDescIdDesc(
                tenantId, statusCode, PageRequest.of(0, limit));
    }

    /**
     * Tenant uchun berilgan owner'dagi aktiv work item'larni cheklangan sonda qaytaradi.
     *
     * Deterministic ordering: openedAt DESC, id DESC.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni
     * @return aktiv work item'lar, newest-opened-first
     */
    public List<WorkItem> listActiveByTenantAndOwner(UUID tenantId, UUID ownerUserId, int limit) {
        return workItemRepository.findByTenantIdAndCurrentOwnerUserIdAndArchivedFalseOrderByOpenedAtDescIdDesc(
                tenantId, ownerUserId, PageRequest.of(0, limit));
    }

    public List<WorkItemUpdate> listUpdates(UUID tenantId, UUID workItemId) {
        return workItemUpdateRepository.findByTenantIdAndWorkItemIdOrderByCreatedAtAscIdAsc(tenantId, workItemId);
    }
}
