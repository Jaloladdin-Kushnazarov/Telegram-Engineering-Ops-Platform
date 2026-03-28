package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * OwnerUserId bo'yicha filtrlangan combined support details facade.
 *
 * Admin/support caller'lar uchun owner-focused combined details browse surface:
 * - bitta tenant, bitta ownerUserId
 * - har bir aktiv work item uchun to'liq support details:
 *   work item metadata + update history + delivery observability + recent attempts
 *
 * Delegation:
 * (tenantId, ownerUserId, limit)
 *   -> WorkItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit) [primary]
 *   -> har bir primary item uchun: WorkItemSupportDetailsFacade.getDetails(tenantId, workItemCode, DEFAULT_HISTORY_LIMIT)
 *   -> List&lt;WorkItemSupportDetailsView&gt;
 *
 * Nima uchun tenant-wide capped enrichment ishlatilMAYDI:
 * - details composition per-item delegatsiya talab qiladi
 * - har bir primary work item uchun individual support-details olish semantik to'g'ri
 * - pozitsion zipping yoki map-based composition emas — to'g'ridan-to'g'ri per-item facade call
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
public class WorkItemSupportDetailsByOwnerFacade {

    static final int DEFAULT_HISTORY_LIMIT = 10;

    private final WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade;
    private final WorkItemSupportDetailsFacade workItemSupportDetailsFacade;

    public WorkItemSupportDetailsByOwnerFacade(
            WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade,
            WorkItemSupportDetailsFacade workItemSupportDetailsFacade) {
        this.workItemSummaryByOwnerFacade = workItemSummaryByOwnerFacade;
        this.workItemSupportDetailsFacade = workItemSupportDetailsFacade;
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning combined support details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50)
     * @return combined support details ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/ownerUserId/limit noto'g'ri bo'lsa
     */
    public List<WorkItemSupportDetailsFacade.WorkItemSupportDetailsView> getDetailsList(
            UUID tenantId, UUID ownerUserId, int limit) {

        List<WorkItemSummaryItem> primaryList =
                workItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit);

        if (primaryList.isEmpty()) {
            return List.of();
        }

        return primaryList.stream()
                .map(wi -> workItemSupportDetailsFacade.getDetails(
                        tenantId, wi.workItemCode(), DEFAULT_HISTORY_LIMIT))
                .toList();
    }
}
