package com.engops.platform.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Work item support details uchun authorized read facade.
 *
 * WorkItemSupportDetailsFacade'ni admin boundary'da o'rab oladi:
 * authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - WorkItemSupportDetailsFacade ichki building block — boshqa composed facade'lar
 *   tomonidan ham chaqiriladi, shuning uchun unda auth bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilitySummaryReadFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, workItemCode, historyLimit, actorUserId)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemSupportDetailsFacade.getDetails(tenantId, workItemCode, historyLimit)
 *   -> WorkItemSupportDetailsView
 *
 * Muhim:
 * - Thin wrapper — faqat authorization qo'shadi
 * - Barcha validation va composition supportDetailsFacade tomonidan amalga oshiriladi
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class WorkItemSupportDetailsReadFacade {

    private final AdminAuthorizationService authorizationService;
    private final WorkItemSupportDetailsFacade supportDetailsFacade;

    public WorkItemSupportDetailsReadFacade(
            AdminAuthorizationService authorizationService,
            WorkItemSupportDetailsFacade supportDetailsFacade) {
        this.authorizationService = authorizationService;
        this.supportDetailsFacade = supportDetailsFacade;
    }

    /**
     * Bitta work item uchun combined support details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return composed view: work item details + delivery observability
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         workItemCode null/blank bo'lsa,
     *         yoki historyLimit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.ResourceNotFoundException
     *         agar workItemCode berilgan tenant uchun topilmasa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public WorkItemSupportDetailsFacade.WorkItemSupportDetailsView getDetails(
            UUID tenantId, String workItemCode, int historyLimit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);
        return supportDetailsFacade.getDetails(tenantId, workItemCode, historyLimit);
    }
}
