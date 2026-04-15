package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * StatusCode bo'yicha filtrlangan combined support details facade.
 *
 * Admin/support caller'lar uchun status-focused combined details browse surface:
 * - bitta tenant, bitta statusCode
 * - har bir aktiv work item uchun to'liq support details:
 *   work item metadata + update history + delivery observability + recent attempts
 *
 * Delegation:
 * (tenantId, statusCode, limit)
 *   -> WorkItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit) [primary]
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
public class WorkItemSupportDetailsByStatusFacade {

    static final int DEFAULT_HISTORY_LIMIT = 10;

    private final WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade;
    private final WorkItemSupportDetailsFacade workItemSupportDetailsFacade;
    private final AdminAuthorizationService authorizationService;

    public WorkItemSupportDetailsByStatusFacade(
            WorkItemSummaryByStatusFacade workItemSummaryByStatusFacade,
            WorkItemSupportDetailsFacade workItemSupportDetailsFacade,
            AdminAuthorizationService authorizationService) {
        this.workItemSummaryByStatusFacade = workItemSummaryByStatusFacade;
        this.workItemSupportDetailsFacade = workItemSupportDetailsFacade;
        this.authorizationService = authorizationService;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning combined support details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @return combined support details ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/statusCode/limit noto'g'ri bo'lsa
     */
    public List<WorkItemSupportDetailsFacade.WorkItemSupportDetailsView> getDetailsList(
            UUID tenantId, String statusCode, int limit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        List<WorkItemSummaryItem> primaryList =
                workItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        if (primaryList.isEmpty()) {
            return List.of();
        }

        return primaryList.stream()
                .map(wi -> workItemSupportDetailsFacade.getDetails(
                        tenantId, wi.workItemCode(), DEFAULT_HISTORY_LIMIT))
                .toList();
    }
}
