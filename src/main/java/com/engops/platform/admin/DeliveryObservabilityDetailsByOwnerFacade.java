package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * OwnerUserId bo'yicha filtrlangan delivery observability details facade.
 *
 * Admin/support caller'lar uchun owner-focused delivery observability details browse:
 * - bitta tenant, bitta ownerUserId
 * - faqat shu owner'dagi aktiv work item'lar uchun to'liq delivery details
 *   (work item metadata + delivery metrics + recent attempts)
 *
 * Delegation:
 * (tenantId, ownerUserId, limit, actorUserId)
 *   -> boundary validation (tenantId null check)
 *   -> AdminAuthorizationService.authorizeRead(tenantId, actorUserId)
 *   -> WorkItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit) [primary]
 *   -> har bir primary item uchun: TelegramDeliveryObservabilityDetailsFacade.getDetails(tenantId, workItemCode, DEFAULT_HISTORY_LIMIT)
 *   -> List&lt;TelegramDeliveryObservabilityDetailsView&gt;
 *
 * Nima uchun per-item details call:
 * - details composition per-item delegatsiya talab qiladi
 * - har bir primary work item uchun individual details olish semantik to'g'ri
 * - DeliveryObservabilityDetailsByStatusFacade bilan aynan bir xil pattern
 *
 * Muhim:
 * - Primary list authoritative — natija primary tartibni saqlaydi
 * - historyLimit bu facade ichida DEFAULT_HISTORY_LIMIT (10) sifatida belgilangan
 * - Validatsiya: tenantId null check bu facade'da, qolgan validatsiya ichki facade'larga delegatsiya
 * - Bo'sh ro'yxat valid natija
 * - Tenant-scoped
 * - Authorization: TENANT_CONFIG_READ
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class DeliveryObservabilityDetailsByOwnerFacade {

    static final int DEFAULT_HISTORY_LIMIT = 10;

    private final WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade;
    private final TelegramDeliveryObservabilityDetailsFacade detailsFacade;
    private final AdminAuthorizationService authorizationService;

    public DeliveryObservabilityDetailsByOwnerFacade(
            WorkItemSummaryByOwnerFacade workItemSummaryByOwnerFacade,
            TelegramDeliveryObservabilityDetailsFacade detailsFacade,
            AdminAuthorizationService authorizationService) {
        this.workItemSummaryByOwnerFacade = workItemSummaryByOwnerFacade;
        this.detailsFacade = detailsFacade;
        this.authorizationService = authorizationService;
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning delivery observability details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50)
     * @param actorUserId joriy actor identifikatori
     * @return delivery details ro'yxati; bo'sh ro'yxat agar work item yo'q
     * @throws IllegalArgumentException agar tenantId/ownerUserId/limit noto'g'ri bo'lsa
     * @throws com.engops.platform.sharedkernel.exception.AccessDeniedException ruxsat bo'lmasa
     */
    public List<TelegramDeliveryObservabilityDetailsView> getDetailsList(
            UUID tenantId, UUID ownerUserId, int limit, UUID actorUserId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        authorizationService.authorizeRead(tenantId, actorUserId);

        List<WorkItemSummaryItem> primaryList =
                workItemSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit);

        if (primaryList.isEmpty()) {
            return List.of();
        }

        return primaryList.stream()
                .map(wi -> detailsFacade.getDetails(
                        tenantId, wi.workItemCode(), DEFAULT_HISTORY_LIMIT))
                .toList();
    }
}
