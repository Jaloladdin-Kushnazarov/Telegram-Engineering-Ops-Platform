package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * WorkItemCode bo'yicha delivery observability details facade — admin authorization bilan.
 *
 * Telegram module'dagi TelegramDeliveryObservabilityDetailsFacade'ni admin boundary'da
 * o'rab oladi: validation -> authorization -> delegation.
 *
 * Nima uchun wrapper kerak:
 * - Telegram module admin-aware bo'lmasligi kerak
 * - Authorization facade boundary'da bo'lishi kerak, controller'da emas
 * - DeliveryObservabilityDetailsByIdFacade aynan shu pattern'dan foydalanadi
 *
 * Delegation:
 * (tenantId, workItemCode, historyLimit, actorUserId)
 *   -> boundary validation
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, historyLimit)
 *   -> TelegramDeliveryObservabilityDetailsView
 *
 * Muhim:
 * - Thin wrapper — faqat validation + authorization qo'shadi
 * - Barcha details composition telegram facade tomonidan amalga oshiriladi
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilityDetailsByCodeFacade {

    private final TelegramDeliveryObservabilityDetailsFacade telegramDetailsFacade;
    private final AdminAuthorizationService authorizationService;

    public DeliveryObservabilityDetailsByCodeFacade(
            TelegramDeliveryObservabilityDetailsFacade telegramDetailsFacade,
            AdminAuthorizationService authorizationService) {
        this.telegramDetailsFacade = telegramDetailsFacade;
        this.authorizationService = authorizationService;
    }

    /**
     * WorkItemCode orqali delivery observability details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return delivery observability details view
     * @throws IllegalArgumentException agar tenantId null bo'lsa yoki workItemCode blank bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.ResourceNotFoundException agar work item topilmasa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public TelegramDeliveryObservabilityDetailsView getDetails(
            UUID tenantId, String workItemCode, int historyLimit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemCode == null || workItemCode.isBlank()) {
            throw new IllegalArgumentException(
                    "workItemCode null yoki bo'sh bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        return telegramDetailsFacade.getDetails(tenantId, workItemCode, historyLimit);
    }
}
