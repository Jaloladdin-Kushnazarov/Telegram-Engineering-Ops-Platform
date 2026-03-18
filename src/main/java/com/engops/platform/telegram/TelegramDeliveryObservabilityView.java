package com.engops.platform.telegram;

import java.util.List;

/**
 * Work-item-scoped delivery observability ko'rinishi.
 *
 * Bitta work item uchun ikki xil read concern'ni birlashtiradi:
 * - latestMetrics: eng so'nggi attempt'dan olingan metrics snapshot
 * - recentAttempts: so'nggi N ta attempt'lar tarixiy ro'yxati
 *
 * Bu view faqat read-side composition — dispatch/write-side bilan aloqasi yo'q.
 * Ikki alohida facade natijalarini bitta immutable object'ga jamlaydi.
 *
 * Har doim valid holat:
 * - latestMetrics bo'sh snapshot bo'lishi mumkin (isEmpty() == true)
 * - recentAttempts bo'sh ro'yxat bo'lishi mumkin
 * - lekin null bo'lmaydi
 *
 * @param latestMetrics eng so'nggi delivery metrics snapshot
 * @param recentAttempts so'nggi attempt'lar ro'yxati, newest-first
 */
public record TelegramDeliveryObservabilityView(
        TelegramDeliveryMetricsSnapshot latestMetrics,
        List<TelegramDeliveryAttempt> recentAttempts) {

    public TelegramDeliveryObservabilityView {
        if (latestMetrics == null) {
            throw new IllegalArgumentException("latestMetrics null bo'lishi mumkin emas");
        }
        if (recentAttempts == null) {
            throw new IllegalArgumentException("recentAttempts null bo'lishi mumkin emas");
        }
        recentAttempts = List.copyOf(recentAttempts);
    }
}
