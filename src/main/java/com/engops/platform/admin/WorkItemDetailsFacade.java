package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemUpdate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * WorkItem details read facade — admin/support callerlar uchun.
 *
 * Delegation:
 * (tenantId, workItemCode)
 *   -> WorkItemQueryService.findByTenantAndCode(tenantId, workItemCode)
 *   -> WorkItemQueryService.listUpdates(tenantId, workItemId)
 *   -> WorkItem + List&lt;WorkItemUpdate&gt;
 *
 * Muhim:
 * - Thin orchestration — resolve + collect
 * - WorkItemQueryService — workitem module'ning public API'si
 * - Biznes logika bu yerda yo'q
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 * - workItemCode topilmasa — ResourceNotFoundException (404)
 */
@Service
@Transactional(readOnly = true)
public class WorkItemDetailsFacade {

    private final WorkItemQueryService workItemQueryService;

    public WorkItemDetailsFacade(WorkItemQueryService workItemQueryService) {
        this.workItemQueryService = workItemQueryService;
    }

    /**
     * WorkItem details va update history qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @return work item + ordered update history
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     *         yoki workItemCode null/blank bo'lsa
     * @throws ResourceNotFoundException agar workItemCode berilgan tenant uchun topilmasa
     */
    public WorkItemDetailsView getDetails(UUID tenantId, String workItemCode) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemCode == null || workItemCode.isBlank()) {
            throw new IllegalArgumentException("workItemCode null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkItem workItem = workItemQueryService.findByTenantAndCode(tenantId, workItemCode)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemCode));

        List<WorkItemUpdate> updates = workItemQueryService.listUpdates(tenantId, workItem.getId());

        return new WorkItemDetailsView(workItem, updates);
    }

    /**
     * Facade natija modeli — ichki foydalanish uchun.
     *
     * @param workItem work item entity
     * @param updates ordered update ro'yxati (createdAt ASC)
     */
    public record WorkItemDetailsView(WorkItem workItem, List<WorkItemUpdate> updates) {}
}
