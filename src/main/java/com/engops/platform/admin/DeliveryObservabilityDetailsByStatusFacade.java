package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * StatusCode bo'yicha filtrlangan delivery observability details facade.
 *
 * Admin/support caller'lar uchun status-focused delivery observability details browse:
 * - bitta tenant, bitta statusCode
 * - faqat shu statusdagi aktiv work item'lar uchun to'liq delivery details
 *   (work item metadata + delivery metrics + recent attempts)
 *
 * Delegation:
 * (tenantId, statusCode, limit)
 *   -> WorkItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit) [primary]
 *   -> har bir primary item uchun: TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, DEFAULT_HISTORY_LIMIT)
 *   -> List&lt;TelegramDeliveryObservabilityDetailsView&gt;
 *
 * Nima uchun per-item details call:
 * - details composition per-item delegatsiya talab qiladi
 * - har bir primary work item uchun individual details olish semantik to'g'ri
 * - WorkItemSupportDetailsByStatusFacade bilan aynan bir xil pattern
 *
 * Muhim:
 * - Primary list authoritative — natija primary tartibni saqlaydi
 * - historyLimit bu facade ichida DEFAULT_HISTORY_LIMIT (10) sifatida belgilangan
 * - Validatsiya ichki facade'larga delegatsiya qilinadi
 * - Bo'sh ro'yxat valid natija
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilityDetailsByStatusFacade {

    static final int DEFAULT_HISTORY_LIMIT = 10;

    private final WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade;
    private final TelegramDeliveryObservabilityDetailsFacade detailsFacade;

    public DeliveryObservabilityDetailsByStatusFacade(
            WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade,
            TelegramDeliveryObservabilityDetailsFacade detailsFacade) {
        this.workItemSummaryByStatusFacade = workItemSummaryByStatusFacade;
        this.detailsFacade = detailsFacade;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning delivery observability details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @return delivery details ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/statusCode/limit noto'g'ri bo'lsa
     */
    public List<TelegramDeliveryObservabilityDetailsView> getDetailsList(
            UUID tenantId, String statusCode, int limit) {

        List<WorkItemSummaryItem> primaryList =
                workItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        if (primaryList.isEmpty()) {
            return List.of();
        }

        return primaryList.stream()
                .map(wi -> detailsFacade.getDetails(
                        tenantId, wi.workItemCode(), DEFAULT_HISTORY_LIMIT))
                .toList();
    }
}
