package com.engops.platform.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Delivery attempt tarixini olish uchun barqaror ichki boundary.
 *
 * Bu facade telegram module ichidagi history query pipeline'ning
 * tashqi caller'lar uchun yagona entry point'i.
 *
 * TelegramDeliveryMetricsFacade'dan farqi:
 * - MetricsFacade faqat latest snapshot qaytaradi (bitta attempt → metrics)
 * - HistoryFacade so'nggi N ta attempt'lar ro'yxatini qaytaradi (raw history)
 *
 * Ikkalasi bir xil ordering qoidasini ishlatadi:
 * attempted_at DESC, id DESC — shuning uchun history[0] == latest snapshot manbai.
 *
 * Limit: 1..50 oralig'ida. 50 — kichik lekin yetarli yuqori chegara.
 * Operatsion kontekstda so'nggi 10-20 attempt yetarli,
 * 50 esa kutilmagan holatlar uchun xavfsiz zaxira.
 *
 * Muhim:
 * - Pure validation + delegation
 * - History logika bu yerda yo'q
 * - Tenant-scoped, work-item-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TelegramDeliveryAttemptHistoryFacade {

    static final int MAX_LIMIT = 50;

    private final TelegramDeliveryAttemptHistoryReadAccess readAccess;

    public TelegramDeliveryAttemptHistoryFacade(TelegramDeliveryAttemptHistoryReadAccess readAccess) {
        this.readAccess = readAccess;
    }

    /**
     * Berilgan tenant va work item uchun so'nggi delivery attempt'larni qaytaradi.
     *
     * Ma'lumot topilmasa — bo'sh ro'yxat qaytariladi, exception emas.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param limit qaytariladigan maksimal natija soni (1..50)
     * @return so'nggi attempt'lar ro'yxati, newest-first
     * @throws IllegalArgumentException agar tenantId, workItemId null bo'lsa
     *         yoki limit 1..50 oralig'ida bo'lmasa
     */
    public List<TelegramDeliveryAttempt> getRecentAttempts(UUID tenantId, UUID workItemId, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit 1.." + MAX_LIMIT + " oralig'ida bo'lishi kerak, berilgan: " + limit);
        }

        return readAccess.findRecentAttempts(tenantId, workItemId, limit);
    }
}
