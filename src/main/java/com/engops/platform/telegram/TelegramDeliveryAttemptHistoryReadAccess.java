package com.engops.platform.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Delivery attempt tarixini o'qish uchun read-only data access port'i.
 *
 * Bu interface history facade'ni persistence implementatsiyasidan ajratadi.
 * TelegramDeliveryMetricsReadAccess'dan farqi:
 * - MetricsReadAccess faqat bitta latest attempt qaytaradi (snapshot uchun)
 * - HistoryReadAccess so'nggi N ta attempt'lar ro'yxatini qaytaradi
 *
 * Ikkalasi bir xil ordering qoidasini ishlatadi:
 * attempted_at DESC, id DESC (deterministic tie-breaker).
 *
 * Muhim:
 * - Faqat read operatsiyalar
 * - Tenant-scoped va work-item-scoped lookup
 * - Natija newest-first tartibda
 * - Limit Pageable orqali repository'ga uzatiladi
 */
public interface TelegramDeliveryAttemptHistoryReadAccess {

    /**
     * Berilgan tenant va work item uchun so'nggi attempt'larni qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param limit qaytariladigan maksimal natija soni
     * @return so'nggi attempt'lar ro'yxati, newest-first; bo'sh ro'yxat agar ma'lumot yo'q
     */
    List<TelegramDeliveryAttempt> findRecentAttempts(UUID tenantId, UUID workItemId, int limit);
}
