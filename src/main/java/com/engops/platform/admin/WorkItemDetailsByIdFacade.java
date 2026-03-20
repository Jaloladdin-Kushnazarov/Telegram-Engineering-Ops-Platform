package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UUID-based work item details facade.
 *
 * WorkItemId (UUID) orqali work item details + update history qaytaradi.
 * Mavjud code-based facade'ni qayta ishlatadi — logika dublikatsiyasi yo'q.
 *
 * Delegation:
 * (tenantId, workItemId)
 *   -> WorkItemQueryService.findByTenantAndId(tenantId, workItemId)
 *   -> resolve workItemCode
 *   -> WorkItemDetailsFacade.getDetails(tenantId, workItemCode)
 *   -> WorkItemDetailsFacade.WorkItemDetailsView
 *
 * Muhim:
 * - UUID -> code resolve faqat bitta qo'shimcha lookup
 * - Keyin mavjud code-based facade to'liq qayta ishlatiladi
 * - workItemId topilmasa — ResourceNotFoundException (404)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemDetailsByIdFacade {

    private final WorkItemQueryService workItemQueryService;
    private final WorkItemDetailsFacade workItemDetailsFacade;

    public WorkItemDetailsByIdFacade(WorkItemQueryService workItemQueryService,
                                      WorkItemDetailsFacade workItemDetailsFacade) {
        this.workItemQueryService = workItemQueryService;
        this.workItemDetailsFacade = workItemDetailsFacade;
    }

    /**
     * WorkItemId (UUID) orqali work item details + update history qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @return work item details + ordered update history
     * @throws IllegalArgumentException agar tenantId yoki workItemId null bo'lsa
     * @throws ResourceNotFoundException agar workItemId berilgan tenant uchun topilmasa
     */
    public WorkItemDetailsFacade.WorkItemDetailsView getDetails(UUID tenantId, UUID workItemId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }

        WorkItem workItem = workItemQueryService.findByTenantAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));

        return workItemDetailsFacade.getDetails(tenantId, workItem.getWorkItemCode());
    }
}
