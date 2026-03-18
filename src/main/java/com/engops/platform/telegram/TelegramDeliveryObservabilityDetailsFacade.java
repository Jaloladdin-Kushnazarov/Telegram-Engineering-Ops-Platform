package com.engops.platform.telegram;

import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Work item metadata + delivery observability ni birgalikda qaytaruvchi ichki boundary.
 *
 * Bu facade bitta chaqiruv bilan enriched details view qaytaradi:
 * - work item identifikatsiya va holati
 * - delivery metrics snapshot
 * - delivery attempt tarix
 *
 * Delegation:
 * (tenantId, workItemCode, historyLimit)
 *   -> WorkItemQueryService.findByTenantAndCode(tenantId, workItemCode)
 *   -> TelegramDeliveryObservabilityFacade.getObservabilityView(tenantId, workItemId, historyLimit)
 *   -> TelegramDeliveryObservabilityDetailsView(workItem metadata + observability data)
 *
 * Nima uchun WorkItemQueryService + ObservabilityFacade to'g'ridan-to'g'ri ishlatiladi
 * (ObservabilityByCodeFacade o'rniga):
 * - ByCodeFacade ham WorkItemQueryService chaqiradi, lekin WorkItem object'ni
 *   tashqariga chiqarmaydi — faqat id'ni oladi
 * - Bu facade WorkItem metadata kerak (title, type, status), shuning uchun
 *   WorkItem'ni o'zi olishi kerak
 * - ByCodeFacade orqali borsa ikkita lookup bo'lar edi — ortiqcha
 *
 * Muhim:
 * - Thin orchestration — resolve + delegate + compose
 * - WorkItemQueryService — workitem module public API
 * - TelegramDeliveryObservabilityFacade — telegram module public API
 * - Logika dublikatsiyasi yo'q
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TelegramDeliveryObservabilityDetailsFacade {

    private final WorkItemQueryService workItemQueryService;
    private final TelegramDeliveryObservabilityFacade observabilityFacade;

    public TelegramDeliveryObservabilityDetailsFacade(WorkItemQueryService workItemQueryService,
                                                       TelegramDeliveryObservabilityFacade observabilityFacade) {
        this.workItemQueryService = workItemQueryService;
        this.observabilityFacade = observabilityFacade;
    }

    /**
     * WorkItemCode orqali enriched delivery observability details view qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi attempt'lar soni (1..50)
     * @return enriched details view (work item metadata + observability data)
     * @throws IllegalArgumentException agar tenantId null bo'lsa,
     *         workItemCode null/blank bo'lsa,
     *         yoki historyLimit noto'g'ri bo'lsa
     * @throws ResourceNotFoundException agar workItemCode berilgan tenant uchun topilmasa
     */
    public TelegramDeliveryObservabilityDetailsView getDetails(UUID tenantId,
                                                                String workItemCode,
                                                                int historyLimit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemCode == null || workItemCode.isBlank()) {
            throw new IllegalArgumentException("workItemCode null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkItem workItem = workItemQueryService.findByTenantAndCode(tenantId, workItemCode)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", workItemCode));

        TelegramDeliveryObservabilityView observability =
                observabilityFacade.getObservabilityView(tenantId, workItem.getId(), historyLimit);

        return new TelegramDeliveryObservabilityDetailsView(
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTitle(),
                workItem.getTypeCode(),
                workItem.getCurrentStatusCode(),
                observability.latestMetrics(),
                observability.recentAttempts());
    }
}
