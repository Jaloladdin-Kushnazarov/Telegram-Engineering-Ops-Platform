package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * StatusCode bo'yicha filtrlangan combined support summary facade.
 *
 * Admin/support caller'lar uchun status-focused combined browse surface:
 * - bitta tenant, bitta statusCode
 * - har bir aktiv work item uchun work item metadata + delivery metrics summary
 *
 * Delegation:
 * (tenantId, statusCode, limit)
 *   -> WorkItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit)
 *   -> DeliveryObservabilitySummaryFacade.getSummaryList(tenantId, limit)
 *   -> compose by workItemId
 *   -> List&lt;WorkItemSupportSummaryItem&gt;
 *
 * Composition qoidasi:
 * - Work item summary (status-filtered) = primary list
 * - Delivery observability summary = enrichment source
 * - workItemId bo'yicha matching (pozitsion emas)
 * - Delivery summary topilmasa -> IllegalStateException
 *
 * Muhim:
 * - Validatsiya ichki facade'larga delegatsiya qilinadi
 * - Bo'sh ro'yxat valid natija
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSupportSummaryByStatusFacade {

    private final WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade;
    private final DeliveryObservabilitySummaryFacade deliveryObservabilitySummaryFacade;

    public WorkItemSupportSummaryByStatusFacade(
            WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade,
            DeliveryObservabilitySummaryFacade deliveryObservabilitySummaryFacade) {
        this.workItemSummaryByStatusFacade = workItemSummaryByStatusFacade;
        this.deliveryObservabilitySummaryFacade = deliveryObservabilitySummaryFacade;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning combined support summary qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @return combined summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/statusCode/limit noto'g'ri bo'lsa
     * @throws IllegalStateException agar composition inconsistency aniqlansa
     */
    public List<WorkItemSupportSummaryItem> getSummaryList(UUID tenantId, String statusCode, int limit) {
        List<WorkItemSummaryItem> workItemSummaries =
                workItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        if (workItemSummaries.isEmpty()) {
            return List.of();
        }

        List<DeliveryObservabilitySummaryItem> deliverySummaries =
                deliveryObservabilitySummaryFacade.getSummaryList(tenantId, limit);

        Map<UUID, DeliveryObservabilitySummaryItem> deliveryByWorkItemId =
                deliverySummaries.stream()
                        .collect(Collectors.toMap(
                                DeliveryObservabilitySummaryItem::workItemId,
                                Function.identity()));

        return workItemSummaries.stream()
                .map(workItem -> compose(workItem, deliveryByWorkItemId))
                .toList();
    }

    private WorkItemSupportSummaryItem compose(
            WorkItemSummaryItem workItem,
            Map<UUID, DeliveryObservabilitySummaryItem> deliveryByWorkItemId) {

        DeliveryObservabilitySummaryItem delivery = deliveryByWorkItemId.get(workItem.workItemId());
        if (delivery == null) {
            throw new IllegalStateException(
                    "Delivery observability summary topilmadi: workItemId=" + workItem.workItemId()
                            + ". Bu ichki composition inconsistency.");
        }

        return new WorkItemSupportSummaryItem(workItem, delivery);
    }
}
