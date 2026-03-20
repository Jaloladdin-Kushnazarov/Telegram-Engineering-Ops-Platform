package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UUID-based combined support details facade.
 *
 * WorkItemId (UUID) orqali work item details + delivery observability qaytaradi.
 * Mavjud code-based facade'larni qayta ishlatadi — logika dublikatsiyasi yo'q.
 *
 * Delegation:
 * (tenantId, workItemId, historyLimit)
 *   -> WorkItemQueryService.findByTenantAndId(tenantId, workItemId)
 *   -> resolve workItemCode
 *   -> WorkItemDetailsFacade.getDetails(tenantId, workItemCode)
 *   -> TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit)
 *   -> WorkItemSupportDetailsFacade.WorkItemSupportDetailsView
 *
 * Muhim:
 * - UUID -> code resolve faqat bitta qo'shimcha lookup
 * - Keyin mavjud facade'lar to'liq qayta ishlatiladi
 * - workItemId topilmasa — ResourceNotFoundException (404)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSupportDetailsByIdFacade {

    private final WorkItemQueryService workItemQueryService;
    private final WorkItemDetailsFacade workItemDetailsFacade;
    private final TelegramDeliveryObservabilityDetailsFacade observabilityDetailsFacade;

    public WorkItemSupportDetailsByIdFacade(WorkItemQueryService workItemQueryService,
                                             WorkItemDetailsFacade workItemDetailsFacade,
                                             TelegramDeliveryObservabilityDetailsFacade observabilityDetailsFacade) {
        this.workItemQueryService = workItemQueryService;
        this.workItemDetailsFacade = workItemDetailsFacade;
        this.observabilityDetailsFacade = observabilityDetailsFacade;
    }

    /**
     * WorkItemId (UUID) orqali combined support details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50)
     * @return composed view: work item details + delivery observability
     * @throws IllegalArgumentException agar tenantId yoki workItemId null bo'lsa,
     *         yoki historyLimit noto'g'ri bo'lsa
     * @throws ResourceNotFoundException agar workItemId berilgan tenant uchun topilmasa
     */
    public WorkItemSupportDetailsFacade.WorkItemSupportDetailsView getDetails(
            UUID tenantId, UUID workItemId, int historyLimit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }

        WorkItem workItem = workItemQueryService.findByTenantAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));

        String workItemCode = workItem.getWorkItemCode();

        WorkItemDetailsFacade.WorkItemDetailsView workItemDetails =
                workItemDetailsFacade.getDetails(tenantId, workItemCode);

        TelegramDeliveryObservabilityDetailsView observabilityDetails =
                observabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit);

        return new WorkItemSupportDetailsFacade.WorkItemSupportDetailsView(
                workItemDetails, observabilityDetails);
    }
}
