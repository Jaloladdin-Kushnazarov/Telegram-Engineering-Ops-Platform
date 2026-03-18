package com.engops.platform.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Eng so'nggi delivery attempt asosida metrics snapshot qaytaruvchi query servis.
 *
 * Hozirgi Phase 19 scope'da bu servis faqat bitta latest attempt'ni
 * metrics snapshot'ga aylantiradi. To'liq aggregatsiya (ko'p attempt'lar
 * bo'yicha yig'ish, tarixiy statistika) bu phase'da yo'q.
 *
 * Read pipeline:
 * TelegramDeliveryMetricsQuery
 *   -> TelegramDeliveryMetricsReadAccess.findLatestAttempt(...)
 *   -> TelegramDeliveryMetricsAssembler.assemble(attempt)
 *   -> TelegramDeliveryMetricsSnapshot
 *
 * Ma'lumot topilmasa — empty snapshot qaytariladi, exception emas.
 *
 * Muhim:
 * - Metrics hisoblashni o'zi qilmaydi — assembler'ga delegatsiya qiladi
 * - Persistence detail'larini bilmaydi — readAccess interface orqali
 * - Tenant-scoped, work-item-scoped lookup
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TelegramDeliveryMetricsQueryService {

    private final TelegramDeliveryMetricsReadAccess readAccess;
    private final TelegramDeliveryMetricsAssembler assembler;

    public TelegramDeliveryMetricsQueryService(TelegramDeliveryMetricsReadAccess readAccess,
                                                TelegramDeliveryMetricsAssembler assembler) {
        this.readAccess = readAccess;
        this.assembler = assembler;
    }

    /**
     * Query asosida delivery metrics snapshot qaytaradi.
     *
     * Agar berilgan tenant + workItem uchun attempt topilmasa,
     * empty snapshot qaytariladi (exception emas).
     *
     * @param query tenant-scoped, work-item-scoped so'rov
     * @return metrics snapshot (bo'sh yoki to'ldirilgan)
     * @throws IllegalArgumentException agar query null bo'lsa
     */
    public TelegramDeliveryMetricsSnapshot getSnapshot(TelegramDeliveryMetricsQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query null bo'lishi mumkin emas");
        }

        Optional<TelegramDeliveryAttempt> latestAttempt = readAccess.findLatestAttempt(
                query.getTenantId(), query.getWorkItemId());

        return latestAttempt
                .map(assembler::assemble)
                .orElseGet(() -> TelegramDeliveryMetricsSnapshot.empty(
                        query.getTenantId(), query.getWorkItemId()));
    }
}
