package com.engops.platform.telegram;

import com.engops.platform.workitem.model.WorkItemType;

import java.util.List;
import java.util.UUID;

/**
 * Work item metadata + delivery observability ni birlashtirgan enriched ko'rinish.
 *
 * Bitta work item uchun uchta concern jamlangan:
 * - work item identifikatsiya va holati (id, code, title, type, status)
 * - eng so'nggi delivery metrics snapshot
 * - so'nggi delivery attempt'lar tarixiy ro'yxati
 *
 * Bu view support/debug/admin-style caller'lar uchun mo'ljallangan —
 * bitta chaqiruv bilan work item konteksti va delivery holati olinadi.
 *
 * Har doim valid holat:
 * - work item metadata har doim mavjud (work item topilgan bo'lishi shart)
 * - latestMetrics bo'sh snapshot bo'lishi mumkin
 * - recentAttempts bo'sh ro'yxat bo'lishi mumkin
 *
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi (masalan "BUG-1")
 * @param title work item sarlavhasi
 * @param typeCode work item turi (BUG, INCIDENT, TASK)
 * @param currentStatusCode hozirgi holat kodi
 * @param latestMetrics eng so'nggi delivery metrics snapshot
 * @param recentAttempts so'nggi attempt'lar ro'yxati, newest-first
 */
public record TelegramDeliveryObservabilityDetailsView(
        UUID workItemId,
        String workItemCode,
        String title,
        WorkItemType typeCode,
        String currentStatusCode,
        TelegramDeliveryMetricsSnapshot latestMetrics,
        List<TelegramDeliveryAttempt> recentAttempts) {

    public TelegramDeliveryObservabilityDetailsView {
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        if (workItemCode == null) {
            throw new IllegalArgumentException("workItemCode null bo'lishi mumkin emas");
        }
        if (title == null) {
            throw new IllegalArgumentException("title null bo'lishi mumkin emas");
        }
        if (typeCode == null) {
            throw new IllegalArgumentException("typeCode null bo'lishi mumkin emas");
        }
        if (currentStatusCode == null) {
            throw new IllegalArgumentException("currentStatusCode null bo'lishi mumkin emas");
        }
        if (latestMetrics == null) {
            throw new IllegalArgumentException("latestMetrics null bo'lishi mumkin emas");
        }
        if (recentAttempts == null) {
            throw new IllegalArgumentException("recentAttempts null bo'lishi mumkin emas");
        }
        recentAttempts = List.copyOf(recentAttempts);
    }
}
