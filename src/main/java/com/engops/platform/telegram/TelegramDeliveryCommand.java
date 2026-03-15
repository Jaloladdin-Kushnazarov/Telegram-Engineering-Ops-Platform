package com.engops.platform.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Telegram adapter uchun outbound delivery command contract.
 *
 * Bu DTO keyingi phase'dagi adapter'ning yagona kirish nuqtasi bo'ladi.
 * Adapter shu command'ni qabul qilib, Telegram Bot API call bajaradi.
 *
 * Tarkibi:
 * - operation: qanday operatsiya bajarilishi kerak (SEND_NEW_MESSAGE)
 * - tenantId, workItemId: message konteksti
 * - targetChatBindingId, targetTopicId: qayerga yuborish (mandatory for SEND)
 * - text: tayyor message text
 * - keyboard: inline keyboard row'lari
 *
 * SEND_NEW_MESSAGE uchun fail-fast:
 * - targetChatBindingId va targetTopicId majburiy
 * - Agar null bo'lsa, assembler bosqichida IllegalArgumentException
 *
 * Bu command immutable — bir marta yaratilgandan keyin o'zgarmaydi.
 * Keyboard List.copyOf orqali himoyalangan.
 */
public class TelegramDeliveryCommand {

    private final TelegramDeliveryOperation operation;
    private final UUID tenantId;
    private final UUID workItemId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;
    private final String text;
    private final List<TelegramInlineKeyboardRow> keyboard;

    public TelegramDeliveryCommand(TelegramDeliveryOperation operation,
                                    UUID tenantId, UUID workItemId,
                                    UUID targetChatBindingId, Long targetTopicId,
                                    String text,
                                    List<TelegramInlineKeyboardRow> keyboard) {
        if (operation == null) {
            throw new IllegalArgumentException("operation null bo'lishi mumkin emas");
        }
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
        this.operation = operation;
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
        this.text = text;
        this.keyboard = keyboard != null ? List.copyOf(keyboard) : List.of();
    }

    public TelegramDeliveryOperation getOperation() { return operation; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public String getText() { return text; }
    public List<TelegramInlineKeyboardRow> getKeyboard() { return keyboard; }

    public boolean hasKeyboard() { return !keyboard.isEmpty(); }
}
