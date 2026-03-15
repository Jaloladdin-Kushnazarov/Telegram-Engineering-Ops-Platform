package com.engops.platform.telegram;

import java.util.List;

/**
 * Telegram inline keyboard'ning bitta qatori.
 *
 * Har bir row bir yoki bir nechta button'ni o'z ichiga oladi.
 * MVP'da har bir action alohida row bo'lib chiqadi (bitta button per row).
 *
 * Buttons list immutable — List.copyOf orqali himoyalangan.
 */
public class TelegramInlineKeyboardRow {

    private final List<TelegramInlineKeyboardButton> buttons;

    public TelegramInlineKeyboardRow(List<TelegramInlineKeyboardButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            throw new IllegalArgumentException("Row buttons null yoki bo'sh bo'lishi mumkin emas");
        }
        this.buttons = List.copyOf(buttons);
    }

    public List<TelegramInlineKeyboardButton> getButtons() { return buttons; }
}
