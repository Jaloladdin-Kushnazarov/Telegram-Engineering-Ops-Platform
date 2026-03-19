package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Combined work item + delivery observability summary ro'yxatini tayyorlovchi composed facade.
 *
 * Admin/support caller'lar uchun kompakt combined browse surface:
 * - har bir aktiv work item uchun work item metadata + delivery metrics summary
 * - bitta endpoint bilan ikki concern ko'rinadi
 *
 * Delegation:
 * (tenantId, limit)
 *   -> WorkItemSummaryFacade.getSummaryList(tenantId, limit)
 *   -> DeliveryObservabilitySummaryFacade.getSummaryList(tenantId, limit)
 *   -> compose by workItemId
 *   -> List&lt;WorkItemSupportSummaryItem&gt;
 *
 * Composition qoidasi:
 * - Work item summary asosiy ro'yxat hisoblanadi
 * - Delivery observability summary workItemId bo'yicha moslanadi
 * - Agar delivery summary topilmasa — IllegalStateException (ichki inconsistency)
 * - Positional zipping ishlatilMAYDI — faqat workItemId bo'yicha matching
 *
 * Nima uchun workItemId bo'yicha composition:
 * - Ikkala facade bir xil deterministic query path ishlatadi
 * - Lekin tartib kafolati faqat DB darajasida — facade'lar orasida emas
 * - workItemId bo'yicha map semantik to'g'ri va xavfsiz
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
public class WorkItemSupportSummaryFacade {

    private final WorkItemSummaryFacade workItemSummaryFacade;
    private final DeliveryObservabilitySummaryFacade deliveryObservabilitySummaryFacade;

    public WorkItemSupportSummaryFacade(WorkItemSummaryFacade workItemSummaryFacade,
                                         DeliveryObservabilitySummaryFacade deliveryObservabilitySummaryFacade) {
        this.workItemSummaryFacade = workItemSummaryFacade;
        this.deliveryObservabilitySummaryFacade = deliveryObservabilitySummaryFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning combined support summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50)
     * @return combined summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     *         yoki limit 1..50 oralig'ida bo'lmasa
     * @throws IllegalStateException agar composition inconsistency aniqlansa
     *         (delivery summary work item uchun topilmasa)
     */
    public List<WorkItemSupportSummaryItem> getSummaryList(UUID tenantId, int limit) {
        List<WorkItemSummaryItem> workItemSummaries =
                workItemSummaryFacade.getSummaryList(tenantId, limit);

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
                            + ". Bu ichki composition inconsistency — ikkala facade bir xil "
                            + "aktiv work item ro'yxatidan foydalanishi kerak.");
        }

        return new WorkItemSupportSummaryItem(workItem, delivery);
    }
}
