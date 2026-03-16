package com.engops.platform.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Telegram sendMessage uchun transport-oriented request model.
 *
 * Bu DTO keyingi phase'dagi real gateway'ning Telegram Bot API
 * sendMessage call'ini amalga oshirish uchun kerakli barcha
 * ma'lumotni o'z ichiga oladi.
 *
 * Application-level TelegramDeliveryCommand'dan farqi:
 * - DeliveryCommand = operation + routing + content (application layer)
 * - SendMessageRequest = faqat sendMessage semantikasi (transport layer)
 *
 * Hozir field'lar DeliveryCommand bilan bir xil ko'rinadi,
 * lekin keyingi phase'larda transport-specific field'lar qo'shiladi
 * (chatId resolution, parseMode, disableNotification va h.k.).
 *
 * Immutable — List.copyOf orqali keyboard himoyalangan.
 */
public class TelegramSendMessageRequest {

    private final UUID tenantId;
    private final UUID workItemId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;
    private final String text;
    private final List<TelegramInlineKeyboardRow> keyboard;

    public TelegramSendMessageRequest(UUID tenantId, UUID workItemId,
                                       UUID targetChatBindingId, Long targetTopicId,
                                       String text,
                                       List<TelegramInlineKeyboardRow> keyboard) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        if (targetChatBindingId == null) {
            throw new IllegalArgumentException("targetChatBindingId null bo'lishi mumkin emas");
        }
        if (targetTopicId == null) {
            throw new IllegalArgumentException("targetTopicId null bo'lishi mumkin emas");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text null yoki bo'sh bo'lishi mumkin emas");
        }
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
        this.text = text;
        this.keyboard = keyboard != null ? List.copyOf(keyboard) : List.of();
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public String getText() { return text; }
    public List<TelegramInlineKeyboardRow> getKeyboard() { return keyboard; }

    public boolean hasKeyboard() { return !keyboard.isEmpty(); }
}
