package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.workitem.model.WorkItemType;

import java.util.UUID;

/**
 * Bitta work item uchun delivery observability summary.
 *
 * Admin/support listing uchun kompakt ichki model.
 * Full details (recentAttempts) bu yerda yo'q — faqat latest metrics summary.
 *
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi
 * @param title work item sarlavhasi
 * @param typeCode work item turi
 * @param currentStatusCode hozirgi holat kodi
 * @param latestMetrics eng so'nggi delivery metrics snapshot
 */
public record DeliveryObservabilitySummaryItem(
        UUID workItemId,
        String workItemCode,
        String title,
        WorkItemType typeCode,
        String currentStatusCode,
        TelegramDeliveryMetricsSnapshot latestMetrics) {

    public DeliveryObservabilitySummaryItem {
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
    }
}
