package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsFacade;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped delivery observability summary ro'yxatini tayyorlovchi facade.
 *
 * Admin/support caller'lar uchun kompakt overview:
 * - aktiv work item'lar ro'yxati (capped)
 * - har bir work item uchun latest delivery metrics snapshot
 *
 * Delegation:
 * (tenantId, limit)
 *   -> WorkItemQueryService.listActiveByTenant(tenantId, limit)
 *   -> har bir workItem uchun: TelegramDeliveryMetricsFacade.getDeliveryMetrics(tenantId, workItemId)
 *   -> List<DeliveryObservabilitySummaryItem>
 *
 * Limit siyosati:
 * - Default: 20 (operatsion overview uchun yetarli)
 * - Max: 50 (xavfsiz yuqori chegara)
 * - Limit DB darajasida qo'llanadi — ortiqcha entity'lar yuklanmaydi
 *
 * Muhim:
 * - Faqat aktiv (arxivlanmagan) work item'lar
 * - recentAttempts yo'q — faqat latest snapshot
 * - Bo'sh ro'yxat valid natija (exception emas)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilitySummaryFacade {

    static final int MAX_LIMIT = 50;

    private final WorkItemQueryService workItemQueryService;
    private final TelegramDeliveryMetricsFacade metricsFacade;

    public DeliveryObservabilitySummaryFacade(WorkItemQueryService workItemQueryService,
                                              TelegramDeliveryMetricsFacade metricsFacade) {
        this.workItemQueryService = workItemQueryService;
        this.metricsFacade = metricsFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning delivery observability summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50)
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     *         yoki limit 1..50 oralig'ida bo'lmasa
     */
    public List<DeliveryObservabilitySummaryItem> getSummaryList(UUID tenantId, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit 1.." + MAX_LIMIT + " oralig'ida bo'lishi kerak, berilgan: " + limit);
        }

        List<WorkItem> workItems = workItemQueryService.listActiveByTenant(tenantId, limit);

        return workItems.stream()
                .map(workItem -> toSummaryItem(tenantId, workItem))
                .toList();
    }

    private DeliveryObservabilitySummaryItem toSummaryItem(UUID tenantId, WorkItem workItem) {
        TelegramDeliveryMetricsSnapshot latestMetrics =
                metricsFacade.getDeliveryMetrics(tenantId, workItem.getId());

        return new DeliveryObservabilitySummaryItem(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTitle(),
                workItem.getTypeCode(),
                workItem.getCurrentStatusCode(),
                latestMetrics);
    }
}
