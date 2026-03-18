package com.engops.platform.telegram;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Delivery metrics snapshot olish uchun barqaror ichki boundary.
 *
 * Bu facade telegram module ichidagi metrics query pipeline'ning
 * tashqi caller'lar uchun yagona entry point'i.
 * Caller'lar faqat tenantId va workItemId beradi —
 * query object yaratish va ichki pipeline tafsilotlari yashiriladi.
 *
 * Delegation:
 * (tenantId, workItemId)
 *   -> TelegramDeliveryMetricsQuery yaratish
 *   -> TelegramDeliveryMetricsQueryService.getSnapshot(query)
 *   -> TelegramDeliveryMetricsSnapshot
 *
 * Hozirgi scope'da faqat eng so'nggi attempt asosida snapshot qaytaradi.
 * To'liq aggregatsiya bu phase'da yo'q.
 *
 * Muhim:
 * - Pure delegation — metrics logika bu yerda yo'q
 * - Query object yaratishni inkapsulyatsiya qiladi
 * - Tenant-scoped, work-item-scoped
 * - Stateless
 */
@Service
public class TelegramDeliveryMetricsFacade {

    private final TelegramDeliveryMetricsQueryService queryService;

    public TelegramDeliveryMetricsFacade(TelegramDeliveryMetricsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Berilgan tenant va work item uchun delivery metrics snapshot qaytaradi.
     *
     * Ma'lumot topilmasa — empty snapshot qaytariladi, exception emas.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @return metrics snapshot (bo'sh yoki to'ldirilgan)
     * @throws IllegalArgumentException agar tenantId yoki workItemId null bo'lsa
     */
    public TelegramDeliveryMetricsSnapshot getDeliveryMetrics(UUID tenantId, UUID workItemId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }

        TelegramDeliveryMetricsQuery query = new TelegramDeliveryMetricsQuery(tenantId, workItemId);
        return queryService.getSnapshot(query);
    }
}
