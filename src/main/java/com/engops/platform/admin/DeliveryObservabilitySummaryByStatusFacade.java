package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * StatusCode bo'yicha filtrlangan delivery observability summary facade.
 *
 * Admin/support caller'lar uchun status-focused delivery observability browse:
 * - bitta tenant, bitta statusCode
 * - faqat shu statusdagi aktiv work item'lar uchun delivery summary
 *
 * Delegation:
 * (tenantId, statusCode, limit)
 *   -> WorkItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit) [primary]
 *   -> har bir primary item uchun: TelegramDeliveryMetricsFacade.getDeliveryMetrics(tenantId, workItemId)
 *   -> List&lt;DeliveryObservabilitySummaryItem&gt;
 *
 * Nima uchun tenant-wide capped enrichment list ishlatilMAYDI:
 * - tenant-wide top N primary list dagi barcha work item'larni qamrab olmasligi mumkin
 * - status-filtered work item'lar tenant-wide top N'dan tashqarida bo'lishi ehtimoli bor
 * - bu false IllegalStateException keltirib chiqaradi
 * - shuning uchun har bir primary item uchun individual metrics olish semantik to'g'ri
 *
 * Muhim:
 * - Primary list authoritative — natija primary tartibni saqlaydi
 * - Validatsiya ichki facade'larga delegatsiya qilinadi
 * - Bo'sh ro'yxat valid natija
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilitySummaryByStatusFacade {

    private final WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade;
    private final TelegramDeliveryMetricsFacade deliveryMetricsFacade;

    public DeliveryObservabilitySummaryByStatusFacade(
            WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade,
            TelegramDeliveryMetricsFacade deliveryMetricsFacade) {
        this.workItemSummaryByStatusFacade = workItemSummaryByStatusFacade;
        this.deliveryMetricsFacade = deliveryMetricsFacade;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning delivery observability summary qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @return delivery summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/statusCode/limit noto'g'ri bo'lsa
     */
    public List<DeliveryObservabilitySummaryItem> getSummaryList(
            UUID tenantId, String statusCode, int limit) {

        List<WorkItemSummaryItem> workItemSummaries =
                workItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        if (workItemSummaries.isEmpty()) {
            return List.of();
        }

        return workItemSummaries.stream()
                .map(wi -> toDeliverySummaryItem(tenantId, wi))
                .toList();
    }

    private DeliveryObservabilitySummaryItem toDeliverySummaryItem(
            UUID tenantId, WorkItemSummaryItem wi) {

        TelegramDeliveryMetricsSnapshot latestMetrics =
                deliveryMetricsFacade.getDeliveryMetrics(tenantId, wi.workItemId());

        return new DeliveryObservabilitySummaryItem(
                wi.workItemId(),
                wi.workItemCode(),
                wi.title(),
                wi.typeCode(),
                wi.currentStatusCode(),
                latestMetrics);
    }
}
