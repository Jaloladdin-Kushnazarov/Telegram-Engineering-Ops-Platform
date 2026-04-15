package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Work item summary by status uchun authorized read facade.
 *
 * WorkItemSummaryByStatusFacade'ni admin boundary'da o'rab oladi:
 * authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - WorkItemSummaryByStatusFacade ichki building block — boshqa facade'lar
 *   tomonidan ham chaqiriladi, shuning uchun unda auth bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilitySummaryReadFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, statusCode, limit, actorUserId)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit)
 *   -> List&lt;WorkItemSummaryItem&gt;
 *
 * Muhim:
 * - Thin wrapper — faqat authorization qo'shadi
 * - Barcha validation va composition summaryFacade tomonidan amalga oshiriladi
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSummaryByStatusReadFacade {

    private final AdminAuthorizationService authorizationService;
    private final WorkItemSummaryByStatusFacade summaryFacade;

    public WorkItemSummaryByStatusReadFacade(
            AdminAuthorizationService authorizationService,
            WorkItemSummaryByStatusFacade summaryFacade) {
        this.authorizationService = authorizationService;
        this.summaryFacade = summaryFacade;
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         statusCode null/blank bo'lsa,
     *         yoki limit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public List<WorkItemSummaryItem> getSummaryList(
            UUID tenantId, String statusCode, int limit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);
        return summaryFacade.getSummaryList(tenantId, statusCode, limit);
    }
}
