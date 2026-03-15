package com.engops.platform.telegram;

/**
 * Telegram inline keyboard button uchun transport-level model.
 *
 * Bu Telegram Bot API InlineKeyboardButton'ning internal representation'i.
 * Haqiqiy Telegram API call keyingi phase'da bo'ladi —
 * hozir faqat structured model sifatida ishlatiladi.
 *
 * TelegramCardAction'dan mapping:
 * - action.label → text
 * - action.callbackData → callbackData
 */
public class TelegramInlineKeyboardButton {

    private final String text;
    private final String callbackData;

    public TelegramInlineKeyboardButton(String text, String callbackData) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Button text null yoki bo'sh bo'lishi mumkin emas");
        }
        if (callbackData == null || callbackData.isBlank()) {
            throw new IllegalArgumentException("Button callbackData null yoki bo'sh bo'lishi mumkin emas");
        }
        this.text = text;
        this.callbackData = callbackData;
    }

    public String getText() { return text; }
    public String getCallbackData() { return callbackData; }
}
