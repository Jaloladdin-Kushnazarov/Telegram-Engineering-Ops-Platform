package com.engops.platform.telegram;

import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * WorkItemCode orqali delivery observability view olish uchun ichki boundary.
 *
 * Caller'lar workItemId o'rniga workItemCode beradi — bu facade
 * code → id resolve qiladi va mavjud TelegramDeliveryObservabilityFacade'ga delegatsiya qiladi.
 *
 * Delegation:
 * (tenantId, workItemCode, historyLimit)
 *   -> WorkItemQueryService.findByTenantAndCode(tenantId, workItemCode)
 *   -> workItem.getId()
 *   -> TelegramDeliveryObservabilityFacade.getObservabilityView(tenantId, workItemId, historyLimit)
 *   -> TelegramDeliveryObservabilityView
 *
 * workItemCode topilmasa — IllegalArgumentException.
 * Bu loyihaning mavjud fail-fast uslubiga mos:
 * - null/noto'g'ri input → IllegalArgumentException
 * - mavjud bo'lmagan resurs resolve qilishda ham IAE
 *   (caller noto'g'ri code berdi — bu input xatosi)
 *
 * Muhim:
 * - Thin orchestration — resolve + delegate
 * - WorkItemQueryService — workitem module'ning public API'si
 * - Observability logika bu yerda yo'q
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TelegramDeliveryObservabilityByCodeFacade {

    private final WorkItemQueryService workItemQueryService;
    private final TelegramDeliveryObservabilityFacade observabilityFacade;

    public TelegramDeliveryObservabilityByCodeFacade(WorkItemQueryService workItemQueryService,
                                                      TelegramDeliveryObservabilityFacade observabilityFacade) {
        this.workItemQueryService = workItemQueryService;
        this.observabilityFacade = observabilityFacade;
    }

    /**
     * WorkItemCode orqali delivery observability view qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi attempt'lar soni (1..50)
     * @return composed observability view
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         workItemCode null/blank bo'lsa,
     *         workItemCode berilgan tenant uchun topilmasa,
     *         yoki historyLimit noto'g'ri bo'lsa
     */
    public TelegramDeliveryObservabilityView getObservabilityViewByCode(UUID tenantId,
                                                                         String workItemCode,
                                                                         int historyLimit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemCode == null || workItemCode.isBlank()) {
            throw new IllegalArgumentException("workItemCode null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkItem workItem = workItemQueryService.findByTenantAndCode(tenantId, workItemCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkItem topilmadi: tenantId=" + tenantId + ", workItemCode=" + workItemCode));

        return observabilityFacade.getObservabilityView(tenantId, workItem.getId(), historyLimit);
    }
}
