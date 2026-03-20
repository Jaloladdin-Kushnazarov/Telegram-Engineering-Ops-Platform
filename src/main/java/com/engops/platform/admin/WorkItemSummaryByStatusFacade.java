package com.engops.platform.admin;

import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tenant + statusCode bo'yicha aktiv work item summary ro'yxatini tayyorlovchi facade.
 *
 * Admin/support caller'lar uchun status-focused queue browse surface:
 * - bitta tenant, bitta statusCode
 * - faqat aktiv (arxivlanmagan) work item'lar
 * - deterministic ordering (openedAt DESC, id DESC)
 * - capped natija
 *
 * Delegation:
 * (tenantId, statusCode, limit)
 *   -> WorkItemQueryService.listActiveByTenantAndStatus(tenantId, statusCode, limit)
 *   -> map each WorkItem to WorkItemSummaryItem
 *   -> List&lt;WorkItemSummaryItem&gt;
 *
 * Muhim:
 * - Bo'sh ro'yxat valid natija (exception emas)
 * - Delivery observability enrich qilinMAYDI
 * - Update history yo'q
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSummaryByStatusFacade {

    static final int MAX_LIMIT = 50;

    private final WorkItemQueryService workItemQueryService;

    public WorkItemSummaryByStatusFacade(WorkItemQueryService workItemQueryService) {
        this.workItemQueryService = workItemQueryService;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         statusCode null/blank bo'lsa,
     *         yoki limit 1..50 oralig'ida bo'lmasa
     */
    public List<WorkItemSummaryItem> getSummaryList(UUID tenantId, String statusCode, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (statusCode == null || statusCode.isBlank()) {
            throw new IllegalArgumentException("statusCode null yoki bo'sh bo'lishi mumkin emas");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit 1.." + MAX_LIMIT + " oralig'ida bo'lishi kerak, berilgan: " + limit);
        }

        List<WorkItem> workItems = workItemQueryService.listActiveByTenantAndStatus(
                tenantId, statusCode, limit);

        return workItems.stream()
                .map(this::toSummaryItem)
                .toList();
    }

    private WorkItemSummaryItem toSummaryItem(WorkItem workItem) {
        return new WorkItemSummaryItem(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTitle(),
                workItem.getTypeCode(),
                workItem.getCurrentStatusCode(),
                workItem.getPriorityCode(),
                workItem.getSeverityCode(),
                workItem.getCurrentOwnerUserId(),
                workItem.getOpenedAt(),
                workItem.getLastTransitionAt(),
                workItem.getResolvedAt(),
                workItem.getReopenedCount(),
                workItem.isArchived());
    }
}
