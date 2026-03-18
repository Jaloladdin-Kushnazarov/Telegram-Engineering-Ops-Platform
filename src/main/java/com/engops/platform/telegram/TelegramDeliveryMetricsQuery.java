package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Delivery metrics query uchun tenant-scoped, work-item-scoped so'rov.
 *
 * Bu query object qaysi tenant va qaysi work item uchun
 * metrics snapshot kerakligini aniq belgilaydi.
 *
 * Invariantlar:
 * - tenantId majburiy (non-null)
 * - workItemId majburiy (non-null)
 *
 * Immutable — yaratilgandan keyin o'zgartirilmaydi.
 */
public class TelegramDeliveryMetricsQuery {

    private final UUID tenantId;
    private final UUID workItemId;

    public TelegramDeliveryMetricsQuery(UUID tenantId, UUID workItemId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        this.tenantId = tenantId;
        this.workItemId = workItemId;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
}
