package com.engops.platform.intake;

import java.util.UUID;

/**
 * Projection-ready contract — adapter'lar (Telegram, webhook, va boshqalar)
 * shu DTO orqali tayyor ma'lumotni qabul qiladi.
 *
 * Bu contract barcha business qarorlardan KEYIN hosil bo'ladi:
 * - routing allaqachon resolved
 * - work item allaqachon yaratilgan
 * - delivery target allaqachon aniqlangan
 *
 * Adapter bu contract'ni qabul qilib, o'z formatiga moslashtiradi
 * (Telegram card, webhook payload, va h.k.).
 *
 * PreparedDeliveryTarget dan farqi:
 * - PreparedDeliveryTarget = intake module'ning internal chiqish shakli
 * - ProjectionPayload = adapter'lar uchun stabil, projection-ready contract
 * - Keyingi phase'larda display hint'lar, computed field'lar shu yerga qo'shiladi
 *
 * deliveryReady=false holatda target field'lar null bo'ladi —
 * bu valid holat, adapter o'zi qanday handle qilishni hal qiladi.
 */
public class ProjectionPayload {

    private final UUID tenantId;

    // Work item identity
    private final UUID workItemId;
    private final String workItemCode;
    private final String workItemType;
    private final String title;
    private final String currentStatusCode;

    // Adapter-neutral display fields (assembler tomonidan computed)
    private final String displayTitle;
    private final String displayTypeLabel;

    // Delivery readiness
    private final boolean deliveryReady;

    // Resolved delivery target (faqat deliveryReady=true holatda to'ldiriladi)
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public ProjectionPayload(UUID tenantId,
                              UUID workItemId, String workItemCode,
                              String workItemType, String title,
                              String currentStatusCode,
                              String displayTitle, String displayTypeLabel,
                              boolean deliveryReady,
                              UUID targetChatBindingId, Long targetTopicId) {
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.workItemCode = workItemCode;
        this.workItemType = workItemType;
        this.title = title;
        this.currentStatusCode = currentStatusCode;
        this.displayTitle = displayTitle;
        this.displayTypeLabel = displayTypeLabel;
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
    public String getDisplayTitle() { return displayTitle; }
    public String getDisplayTypeLabel() { return displayTypeLabel; }
    public boolean isDeliveryReady() { return deliveryReady; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
}
