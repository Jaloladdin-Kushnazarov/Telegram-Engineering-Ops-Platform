package com.engops.platform.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Unified delivery observability view olish uchun barqaror ichki boundary.
 *
 * Bu facade telegram module ichidagi ikki alohida read concern'ni
 * bitta composed view sifatida qaytaradi:
 * - latest delivery metrics snapshot (TelegramDeliveryMetricsFacade orqali)
 * - recent delivery attempt history (TelegramDeliveryAttemptHistoryFacade orqali)
 *
 * Delegation:
 * (tenantId, workItemId, historyLimit)
 *   -> TelegramDeliveryMetricsFacade.getDeliveryMetrics(tenantId, workItemId)
 *   -> TelegramDeliveryAttemptHistoryFacade.getRecentAttempts(tenantId, workItemId, historyLimit)
 *   -> TelegramDeliveryObservabilityView(snapshot, attempts)
 *
 * Caller'lar uchun foyda:
 * - bitta chaqiruv bilan to'liq delivery holati olish
 * - ikki alohida facade'ni bilish shart emas
 * - validatsiya bir joyda
 *
 * historyLimit: mavjud TelegramDeliveryAttemptHistoryFacade chegaralari
 * (1..50) o'zgarishsiz qo'llanadi — yangi limit siyosati kiritilmaydi.
 *
 * Muhim:
 * - Pure orchestration + validation
 * - Mavjud facade'larga delegatsiya — logika dublikatsiyasi yo'q
 * - Tenant-scoped, work-item-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TelegramDeliveryObservabilityFacade {

    private final TelegramDeliveryMetricsFacade metricsFacade;
    private final TelegramDeliveryAttemptHistoryFacade historyFacade;

    public TelegramDeliveryObservabilityFacade(TelegramDeliveryMetricsFacade metricsFacade,
                                                TelegramDeliveryAttemptHistoryFacade historyFacade) {
        this.metricsFacade = metricsFacade;
        this.historyFacade = historyFacade;
    }

    /**
     * Berilgan tenant va work item uchun to'liq delivery observability view qaytaradi.
     *
     * Ma'lumot topilmasa — empty snapshot + bo'sh ro'yxat bilan valid view qaytariladi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param historyLimit so'nggi attempt'lar soni (1..50)
     * @return composed observability view
     * @throws IllegalArgumentException agar tenantId, workItemId null bo'lsa
     *         yoki historyLimit 1..50 oralig'ida bo'lmasa
     */
    public TelegramDeliveryObservabilityView getObservabilityView(UUID tenantId,
                                                                    UUID workItemId,
                                                                    int historyLimit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }

        TelegramDeliveryMetricsSnapshot latestMetrics =
                metricsFacade.getDeliveryMetrics(tenantId, workItemId);
        List<TelegramDeliveryAttempt> recentAttempts =
                historyFacade.getRecentAttempts(tenantId, workItemId, historyLimit);

        return new TelegramDeliveryObservabilityView(latestMetrics, recentAttempts);
    }
}
