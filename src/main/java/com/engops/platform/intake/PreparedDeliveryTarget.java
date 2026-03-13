package com.engops.platform.intake;

import java.util.UUID;

/**
 * Adapter-facing DTO — future projection/delivery layer uchun tayyor, minimal ma'lumot.
 *
 * Bu DTO faqat "nimani, qayerga" savollariga javob beradi:
 * - nima: work item identity va holati (id, code, type, title, status)
 * - qayerga: resolved delivery target (chatBindingId, topicId)
 * - tenant konteksti
 *
 * Routing internal details (matchedRoutingRuleId) bu yerda yo'q —
 * adapter uchun faqat final delivery target kerak.
 *
 * Foydalanish:
 * - IntakeResult.toPreparedDeliveryTarget() orqali olinadi
 * - Future Telegram adapter shu contract orqali ishlaydi
 * - Transport-neutral: Telegram, webhook yoki boshqa surface uchun mos
 *
 * deliveryReady=false holatda target fieldlar null bo'ladi —
 * bu valid holat, work item yaratilgan lekin routing target topilmagan.
 */
public class PreparedDeliveryTarget {

    private final UUID tenantId;

    // Work item identity
    private final UUID workItemId;
    private final String workItemCode;
    private final String workItemType;
    private final String title;
    private final String currentStatusCode;

    // Delivery readiness
    private final boolean deliveryReady;

    // Resolved target (faqat deliveryReady=true holatda to'ldiriladi)
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public PreparedDeliveryTarget(UUID tenantId,
                                   UUID workItemId, String workItemCode,
                                   String workItemType, String title,
                                   String currentStatusCode,
                                   boolean deliveryReady,
                                   UUID targetChatBindingId, Long targetTopicId) {
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.workItemCode = workItemCode;
        this.workItemType = workItemType;
        this.title = title;
        this.currentStatusCode = currentStatusCode;
        this.deliveryReady = deliveryReady;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public String getWorkItemCode() { return workItemCode; }
    public String getWorkItemType() { return workItemType; }
    public String getTitle() { return title; }
    public String getCurrentStatusCode() { return currentStatusCode; }
    public boolean isDeliveryReady() { return deliveryReady; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
}
