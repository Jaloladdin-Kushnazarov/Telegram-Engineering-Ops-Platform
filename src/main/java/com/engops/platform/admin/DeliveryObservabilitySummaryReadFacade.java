package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Delivery observability summary uchun authorized read facade.
 *
 * DeliveryObservabilitySummaryFacade'ni admin boundary'da o'rab oladi:
 * authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - DeliveryObservabilitySummaryFacade ichki building block — WorkItemSupport facade'lar
 *   tomonidan ham chaqiriladi, shuning uchun unda auth bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilityDetailsByCodeFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, limit, actorUserId)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> DeliveryObservabilitySummaryFacade.getSummaryList(tenantId, limit)
 *   -> List&lt;DeliveryObservabilitySummaryItem&gt;
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
public class DeliveryObservabilitySummaryReadFacade {

    private final AdminAuthorizationService authorizationService;
    private final DeliveryObservabilitySummaryFacade summaryFacade;

    public DeliveryObservabilitySummaryReadFacade(
            AdminAuthorizationService authorizationService,
            DeliveryObservabilitySummaryFacade summaryFacade) {
        this.authorizationService = authorizationService;
        this.summaryFacade = summaryFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning delivery observability summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return summary item'lar ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId null bo'lsa yoki limit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public List<DeliveryObservabilitySummaryItem> getSummaryList(
            UUID tenantId, int limit, UUID actorUserId) {
        authorizationService.authorizeRead(tenantId, actorUserId);
        return summaryFacade.getSummaryList(tenantId, limit);
    }
}
