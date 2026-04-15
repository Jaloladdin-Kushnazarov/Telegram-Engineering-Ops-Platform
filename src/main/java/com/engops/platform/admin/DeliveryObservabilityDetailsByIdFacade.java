package com.engops.platform.admin;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UUID-based delivery observability details facade.
 *
 * WorkItemId (UUID) orqali delivery observability details qaytaradi.
 * Mavjud code-based facade'ni qayta ishlatadi — logika dublikatsiyasi yo'q.
 *
 * Delegation:
 * (tenantId, workItemId, historyLimit, actorUserId)
 *   -> boundary validation
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemQueryService.findByTenantAndId(tenantId, workItemId)
 *   -> resolve workItemCode
 *   -> TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit)
 *   -> TelegramDeliveryObservabilityDetailsView
 *
 * Muhim:
 * - UUID -> code resolve faqat bitta qo'shimcha lookup
 * - Keyin mavjud code-based facade to'liq qayta ishlatiladi
 * - workItemId topilmasa — ResourceNotFoundException (404)
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilityDetailsByIdFacade {

    private final WorkItemQueryService workItemQueryService;
    private final TelegramDeliveryObservabilityDetailsFacade codeBasedDetailsFacade;
    private final AdminAuthorizationService authorizationService;

    public DeliveryObservabilityDetailsByIdFacade(
            WorkItemQueryService workItemQueryService,
            TelegramDeliveryObservabilityDetailsFacade codeBasedDetailsFacade,
            AdminAuthorizationService authorizationService) {
        this.workItemQueryService = workItemQueryService;
        this.codeBasedDetailsFacade = codeBasedDetailsFacade;
        this.authorizationService = authorizationService;
    }

    /**
     * WorkItemId (UUID) orqali delivery observability details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return delivery observability details view
     * @throws IllegalArgumentException agar tenantId yoki workItemId null bo'lsa,
     *         yoki historyLimit noto'g'ri bo'lsa
     * @throws ResourceNotFoundException agar workItemId berilgan tenant uchun topilmasa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public TelegramDeliveryObservabilityDetailsView getDetails(
            UUID tenantId, UUID workItemId, int historyLimit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        WorkItem workItem = workItemQueryService.findByTenantAndId(tenantId, workItemId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemId));

        return codeBasedDetailsFacade.getDetails(tenantId, workItem.getWorkItemCode(), historyLimit);
    }
}
