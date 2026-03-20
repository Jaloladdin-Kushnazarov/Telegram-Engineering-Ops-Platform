package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * OwnerUserId bo'yicha filtrlangan delivery observability summary facade.
 *
 * Admin/support caller'lar uchun owner-focused delivery observability browse:
 * - bitta tenant, bitta ownerUserId
 * - faqat shu owner'dagi aktiv work item'lar uchun delivery summary
 *
 * Delegation:
 * (tenantId, ownerUserId, limit)
 *   -> WorkItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit) [primary]
 *   -> har bir primary item uchun: TelegramDeliveryMetricsFacade.getDeliveryMetrics(tenantId, workItemId)
 *   -> List&lt;DeliveryObservabilitySummaryItem&gt;
 *
 * Nima uchun tenant-wide capped enrichment list ishlatilMAYDI:
 * - tenant-wide top N primary list dagi barcha work item'larni qamrab olmasligi mumkin
 * - owner-filtered work item'lar tenant-wide top N'dan tashqarida bo'lishi ehtimoli bor
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
public class DeliveryObservabilitySummaryByOwnerFacade {

    private final WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade;
    private final TelegramDeliveryMetricsFacade deliveryMetricsFacade;

    public DeliveryObservabilitySummaryByOwnerFacade(
            WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade,
            TelegramDeliveryMetricsFacade deliveryMetricsFacade) {
        this.workItemSummaryByOwnerFacade = workItemSummaryByOwnerFacade;
        this.deliveryMetricsFacade = deliveryMetricsFacade;
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning delivery observability summary qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50)
     * @return delivery summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/ownerUserId/limit noto'g'ri bo'lsa
     */
    public List<DeliveryObservabilitySummaryItem> getSummaryList(
            UUID tenantId, UUID ownerUserId, int limit) {

        List<WorkItemSummaryItem> workItemSummaries =
                workItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit);

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
