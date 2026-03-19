package com.engops.platform.admin;

import com.engops.platform.workitem.model.WorkItemType;

import java.time.Instant;
import java.util.UUID;

/**
 * Bitta work item uchun kompakt admin/support summary.
 *
 * Admin listing uchun ichki model — full details bu yerda yo'q.
 * Description, environmentCode, sourceService, correlationKey kabi
 * maydonlar ataylab chiqarib tashlangan — ular details endpoint'ga tegishli.
 *
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi (masalan "BUG-1")
 * @param title work item sarlavhasi
 * @param typeCode work item turi (BUG, INCIDENT, TASK)
 * @param currentStatusCode hozirgi holat kodi
 * @param priorityCode ustuvorlik kodi (nullable — domain ruxsat beradi)
 * @param severityCode jiddiylik kodi (nullable — domain ruxsat beradi)
 * @param currentOwnerUserId hozirgi egasi (nullable — tayinlanmagan bo'lishi mumkin)
 * @param openedAt ochilgan vaqt
 * @param lastTransitionAt oxirgi holat o'tkazish vaqti (nullable)
 * @param resolvedAt yechilgan vaqt (nullable)
 * @param reopenedCount qayta ochilishlar soni
 * @param archived arxivlangan holat
 */
public record WorkItemSummaryItem(
        UUID workItemId,
        String workItemCode,
        String title,
        WorkItemType typeCode,
        String currentStatusCode,
        String priorityCode,
        String severityCode,
        UUID currentOwnerUserId,
        Instant openedAt,
        Instant lastTransitionAt,
        Instant resolvedAt,
        int reopenedCount,
        boolean archived) {

    public WorkItemSummaryItem {
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
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt null bo'lishi mumkin emas");
        }
    }
}
