package com.engops.platform.admin;

/**
 * Bitta work item uchun combined support summary.
 *
 * Ikki mustaqil concern'ni birgalikda saqlaydi:
 * - workItem: kompakt work item metadata
 * - deliveryObservability: kompakt delivery metrics summary
 *
 * Admin/support listing uchun ichki composed model.
 * Full details bu yerda yo'q — ular /support-details endpoint'ga tegishli.
 *
 * @param workItem kompakt work item summary
 * @param deliveryObservability kompakt delivery observability summary
 */
public record WorkItemSupportSummaryItem(
        WorkItemSummaryItem workItem,
        DeliveryObservabilitySummaryItem deliveryObservability) {

    public WorkItemSupportSummaryItem {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem null bo'lishi mumkin emas");
        }
        if (deliveryObservability == null) {
            throw new IllegalArgumentException("deliveryObservability null bo'lishi mumkin emas");
        }
    }
}
