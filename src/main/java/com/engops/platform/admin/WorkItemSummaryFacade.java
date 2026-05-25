package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped work item summary ro'yxatini tayyorlovchi facade.
 *
 * Admin/support caller'lar uchun kompakt browse surface:
 * - aktiv work item'lar ro'yxati (capped, deterministic)
 * - har bir work item uchun faqat operatsion metadata
 * - delivery observability enrich qilinMAYDI (alohida endpoint mavjud)
 *
 * Delegation:
 * (tenantId, limit)
 *   -> WorkItemQueryService.listActiveByTenant(tenantId, limit)
 *   -> map each WorkItem to WorkItemSummaryItem
 *   -> List&lt;WorkItemSummaryItem&gt;
 *
 * Limit siyosati:
 * - Default: controller concern (20)
 * - Max: 50 (xavfsiz yuqori chegara)
 * - Limit DB darajasida qo'llanadi — ortiqcha entity'lar yuklanmaydi
 *
 * Muhim:
 * - Faqat aktiv (arxivlanmagan) work item'lar
 * - Update history yo'q — faqat work item metadata
 * - Bo'sh ro'yxat valid natija (exception emas)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSummaryFacade {

    static final int MAX_LIMIT = 50;

    private final WorkItemQueryService workItemQueryService;
    private final IdentityQueryService identityQueryService;

    public WorkItemSummaryFacade(WorkItemQueryService workItemQueryService,
                                 IdentityQueryService identityQueryService) {
        this.workItemQueryService = workItemQueryService;
        this.identityQueryService = identityQueryService;
    }

    /**
     * Tenant uchun aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50)
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     *         yoki limit 1..50 oralig'ida bo'lmasa
     */
    public List<WorkItemSummaryItem> getSummaryList(UUID tenantId, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit 1.." + MAX_LIMIT + " oralig'ida bo'lishi kerak, berilgan: " + limit);
        }

        List<WorkItem> workItems = workItemQueryService.listActiveByTenant(tenantId, limit);

        // Phase 220a — owner displayName resolution. Har bir UNIKAL owner UUID
        // bir martagina lookup qilinadi (distinct + toMap). Worst-case ≤ limit
        // (50) lookup; ko'pincha kamroq (bir owner'da bir nechta item). Kelajak
        // optimizatsiya: AppUserRepository.findAllById(Set<UUID>) batch.
        // toMap ishlatilmaydi — u null qiymatda NPE tashlaydi (orphan owner →
        // displayName null). HashMap.put null'ga ruxsat beradi.
        Map<UUID, String> ownerDisplayNames = new HashMap<>();
        workItems.stream()
                .map(WorkItem::getCurrentOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(userId -> ownerDisplayNames.put(userId,
                        identityQueryService.findUserById(userId)
                                .map(u -> u.getDisplayName())
                                .orElse(null)));

        return workItems.stream()
                .map(wi -> toSummaryItem(wi, ownerDisplayNames))
                .toList();
    }

    private WorkItemSummaryItem toSummaryItem(WorkItem workItem,
                                              Map<UUID, String> ownerDisplayNames) {
        UUID ownerId = workItem.getCurrentOwnerUserId();
        String ownerDisplayName = (ownerId != null) ? ownerDisplayNames.get(ownerId) : null;
        return new WorkItemSummaryItem(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTitle(),
                workItem.getTypeCode(),
                workItem.getCurrentStatusCode(),
                workItem.getPriorityCode(),
                workItem.getSeverityCode(),
                workItem.getCurrentOwnerUserId(),
                ownerDisplayName,
                workItem.getOpenedAt(),
                workItem.getLastTransitionAt(),
                workItem.getResolvedAt(),
                workItem.getReopenedCount(),
                workItem.isArchived());
    }
}
