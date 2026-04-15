package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Work item details uchun authorized read facade.
 *
 * WorkItemDetailsFacade'ni admin boundary'da o'rab oladi:
 * authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - WorkItemDetailsFacade ichki building block — WorkItemSupportDetailsFacade
 *   tomonidan ham chaqiriladi, shuning uchun unda auth bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilitySummaryReadFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, workItemCode, actorUserId)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemDetailsFacade.getDetails(tenantId, workItemCode)
 *   -> WorkItemDetailsView
 *
 * Muhim:
 * - Thin wrapper — faqat authorization qo'shadi
 * - Barcha validation va composition detailsFacade tomonidan amalga oshiriladi
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemDetailsReadFacade {

    private final AdminAuthorizationService authorizationService;
    private final WorkItemDetailsFacade detailsFacade;

    public WorkItemDetailsReadFacade(
            AdminAuthorizationService authorizationService,
            WorkItemDetailsFacade detailsFacade) {
        this.authorizationService = authorizationService;
        this.detailsFacade = detailsFacade;
    }

    /**
     * WorkItem details va update history qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param actorUserId joriy actor identifikatori
     * @return work item + ordered update history
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     *         yoki workItemCode null/blank bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.ResourceNotFoundException
     *         agar workItemCode berilgan tenant uchun topilmasa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public WorkItemDetailsFacade.WorkItemDetailsView getDetails(
            UUID tenantId, String workItemCode, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);
        return detailsFacade.getDetails(tenantId, workItemCode);
    }
}
