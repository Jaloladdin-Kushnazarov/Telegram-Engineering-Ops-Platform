package com.engops.platform.telegram;

import java.util.List;

/**
 * Phase 177 — Telegram {@code editMessageText} uchun transport request.
 *
 * <p>Telegram Bot API'ning {@code editMessageText} metodi mavjud xabar
 * matnini (va ixtiyoriy inline keyboard'ini) joyida o'zgartiradi.
 * Field'lar Telegram API semantikasiga mos:</p>
 * <ul>
 *   <li>{@code chatId} — Telegram numeric chat id (group/supergroup
 *       uchun negative; bot-private uchun positive). Caller bu
 *       qiymatni tenant config orqali resolve qiladi.</li>
 *   <li>{@code messageId} — eski {@code sendMessage} muvaffaqiyatli
 *       bo'lganda Telegram qaytargan {@code message_id}.</li>
 *   <li>{@code text} — yangi message matn (plain text, parse_mode yo'q).</li>
 *   <li>{@code keyboard} — ixtiyoriy yangi inline keyboard.
 *       {@code null} yoki bo'sh ro'yxat → request body'ga
 *       {@code reply_markup} qo'shilmaydi (mavjud
 *       {@link TelegramSendMessageRequest} bilan bir xil semantika).</li>
 * </ul>
 *
 * <p><strong>Ataylab kiritilmagan:</strong> {@code parse_mode},
 * {@code disable_web_page_preview}, {@code show_alert},
 * {@code callback_query_id}, {@code message_thread_id}. Phase 177 plain
 * text bilan kifoyalanadi (Phase 158 sendMessage pattern bilan
 * sinxron).</p>
 *
 * <p><strong>Phase 177 wiring:</strong> bu request hozircha production
 * dispatch yo'lida ishlatilmaydi. {@link TelegramCardRefreshService}
 * orqali test sharoitida tuzilishi va gateway'ga uzatilishi mumkin,
 * lekin AFTER_COMMIT yo'li hali edit qilmaydi.</p>
 */
public record TelegramEditMessageTextRequest(
        Long chatId,
        Long messageId,
        String text,
        List<TelegramInlineKeyboardRow> keyboard) {

    /**
     * Telegram message text cheklovi (Bot API):
     * sendMessage va editMessageText uchun ham bir xil 4096 simvol.
     */
    public static final int MAX_TEXT_LENGTH = 4096;

    public TelegramEditMessageTextRequest {
        if (chatId == null) {
            throw new IllegalArgumentException("chatId null bo'lishi mumkin emas");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId null bo'lishi mumkin emas");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text null yoki bo'sh bo'lishi mumkin emas");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "text uzunligi " + MAX_TEXT_LENGTH + " simvoldan oshmasligi shart");
        }
        // Mavjud TelegramSendMessageRequest pattern bilan bir xil:
        // null keyboard → bo'sh ro'yxat, mavjud ro'yxat → defensive copy.
        keyboard = keyboard != null ? List.copyOf(keyboard) : List.of();
    }

    public boolean hasKeyboard() {
        return !keyboard.isEmpty();
    }
}
