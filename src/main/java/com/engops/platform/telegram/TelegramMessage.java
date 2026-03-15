package com.engops.platform.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Transport-level Telegram message representation.
 *
 * Bu internal model — haqiqiy Telegram Bot API call emas.
 * Keyingi phase'da Telegram adapter shu model'ni qabul qilib,
 * Telegram API orqali message yuboradi.
 *
 * Tarkibi:
 * - tenantId, workItemId: message konteksti
 * - text: tayyor message text (header + title + status)
 * - keyboard: inline keyboard row'lari (bo'sh bo'lishi mumkin)
 * - targetChatBindingId, targetTopicId: resolved delivery target
 *
 * Keyboard bo'sh list bo'lishi valid holat —
 * masalan action'siz INCIDENT/TASK uchun.
 *
 * deliveryReady bo'lmasa targetChatBindingId va targetTopicId null bo'ladi —
 * adapter bu holatda message yubormasdan yoki fallback render qilishi mumkin.
 */
public class TelegramMessage {

    private final UUID tenantId;
    private final UUID workItemId;
    private final String text;
    private final List<TelegramInlineKeyboardRow> keyboard;

    // Resolved delivery target (deliveryReady bo'lmasa null)
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public TelegramMessage(UUID tenantId, UUID workItemId,
                            String text, List<TelegramInlineKeyboardRow> keyboard,
                            UUID targetChatBindingId, Long targetTopicId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text null yoki bo'sh bo'lishi mumkin emas");
        }
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.text = text;
        this.keyboard = keyboard != null ? List.copyOf(keyboard) : List.of();
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public String getText() { return text; }
    public List<TelegramInlineKeyboardRow> getKeyboard() { return keyboard; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }

    public boolean hasKeyboard() { return !keyboard.isEmpty(); }
}
