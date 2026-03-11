package com.engops.platform.workitem;

import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
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

    public List<WorkItemUpdate> listUpdates(UUID tenantId, UUID workItemId) {
        return workItemUpdateRepository.findByTenantIdAndWorkItemId(tenantId, workItemId);
    }
}
