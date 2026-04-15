package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Work item summary uchun authorized read facade.
 *
 * WorkItemSummaryFacade'ni admin boundary'da o'rab oladi:
 * authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - WorkItemSummaryFacade ichki building block — boshqa facade'lar
 *   tomonidan ham chaqiriladi, shuning uchun unda auth bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilitySummaryReadFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, limit, actorUserId)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemSummaryFacade.getSummaryList(tenantId, limit)
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
public class WorkItemSummaryReadFacade {

    private final AdminAuthorizationService authorizationService;
    private final WorkItemSummaryFacade summaryFacade;

    public WorkItemSummaryReadFacade(
            AdminAuthorizationService authorizationService,
            WorkItemSummaryFacade summaryFacade) {
        this.authorizationService = authorizationService;
        this.summaryFacade = summaryFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa yoki limit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public List<WorkItemSummaryItem> getSummaryList(
            UUID tenantId, int limit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);
        return summaryFacade.getSummaryList(tenantId, limit);
    }
}
