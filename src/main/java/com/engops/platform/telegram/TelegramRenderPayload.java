package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Telegram adapter uchun render-ready contract.
 *
 * Bu DTO Telegram card/message renderga tayyor structured ma'lumotni o'z ichiga oladi.
 * Future Telegram adapter shu contract'ni qabul qilib, message text hosil qiladi.
 *
 * ProjectionPayload dan farqi:
 * - ProjectionPayload = adapter-neutral, barcha surface'lar uchun umumiy
 * - TelegramRenderPayload = Telegram-specific render hint'lar qo'shilgan
 *
 * Render-specific field'lar:
 * - headerLine: "Bug | BUG-1" — compact card header (type label + code)
 * - statusLine: "Status: BUGS" — render-ready status display
 *
 * Bu field'lar plain text building block'lar — markdown/HTML formatting yo'q.
 * Telegram adapter ularni o'z message formatiga moslashtiradi.
 *
 * deliveryReady=false holatda target field'lar null bo'ladi —
 * adapter bu holatda message yubormasdan yoki fallback render qilishi mumkin.
 */
public class TelegramRenderPayload {

    private final UUID tenantId;

    // Work item identity
    private final UUID workItemId;
    private final String workItemCode;
    private final String workItemType;
    private final String title;
    private final String currentStatusCode;

    // Display fields (ProjectionPayload'dan uzatiladi)
    private final String displayTitle;
    private final String displayTypeLabel;

    // Telegram-specific render hints (assembler tomonidan computed)
    private final String headerLine;
    private final String statusLine;

    // Delivery readiness
    private final boolean deliveryReady;

    // Resolved delivery target (faqat deliveryReady=true holatda to'ldiriladi)
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public TelegramRenderPayload(UUID tenantId,
                                  UUID workItemId, String workItemCode,
                                  String workItemType, String title,
                                  String currentStatusCode,
                                  String displayTitle, String displayTypeLabel,
                                  String headerLine, String statusLine,
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
        this.headerLine = headerLine;
        this.statusLine = statusLine;
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
    public String getHeaderLine() { return headerLine; }
    public String getStatusLine() { return statusLine; }
    public boolean isDeliveryReady() { return deliveryReady; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
}
