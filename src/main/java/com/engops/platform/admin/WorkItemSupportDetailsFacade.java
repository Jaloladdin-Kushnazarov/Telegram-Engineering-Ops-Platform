package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Work item details + delivery observability ni bitta chaqiruvda qaytaruvchi composed facade.
 *
 * Admin/support callerlar uchun bitta endpoint orqali to'liq kontekst:
 * - work item metadata + update history
 * - delivery observability metrics + attempt history
 *
 * Delegation:
 * (tenantId, workItemCode, historyLimit)
 *   -> WorkItemDetailsFacade.getDetails(tenantId, workItemCode)
 *   -> TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit)
 *   -> WorkItemSupportDetailsView
 *
 * Muhim:
 * - Faqat mavjud facade'larni compose qiladi — logika dublikatsiyasi yo'q
 * - Ikkala facade mustaqil validatsiya va exception semantikasiga ega
 * - Birinchi facade ResourceNotFoundException otsa, ikkinchisi chaqirilmaydi
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSupportDetailsFacade {

    private final WorkItemDetailsFacade workItemDetailsFacade;
    private final TelegramDeliveryObservabilityDetailsFacade observabilityDetailsFacade;

    public WorkItemSupportDetailsFacade(WorkItemDetailsFacade workItemDetailsFacade,
                                         TelegramDeliveryObservabilityDetailsFacade observabilityDetailsFacade) {
        this.workItemDetailsFacade = workItemDetailsFacade;
        this.observabilityDetailsFacade = observabilityDetailsFacade;
    }

    /**
     * Bitta work item uchun combined support details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50)
     * @return composed view: work item details + delivery observability
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         workItemCode null/blank bo'lsa,
     *         yoki historyLimit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.ResourceNotFoundException
     *         agar workItemCode berilgan tenant uchun topilmasa
     */
    public WorkItemSupportDetailsView getDetails(UUID tenantId,
                                                   String workItemCode,
                                                   int historyLimit) {
        WorkItemDetailsFacade.WorkItemDetailsView workItemDetails =
                workItemDetailsFacade.getDetails(tenantId, workItemCode);

        TelegramDeliveryObservabilityDetailsView observabilityDetails =
                observabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit);

        return new WorkItemSupportDetailsView(workItemDetails, observabilityDetails);
    }

    /**
     * Composed facade natija modeli.
     *
     * @param workItemDetails work item metadata + ordered update history
     * @param observabilityDetails delivery metrics + recent attempts
     */
    public record WorkItemSupportDetailsView(
            WorkItemDetailsFacade.WorkItemDetailsView workItemDetails,
            TelegramDeliveryObservabilityDetailsView observabilityDetails) {}
}
