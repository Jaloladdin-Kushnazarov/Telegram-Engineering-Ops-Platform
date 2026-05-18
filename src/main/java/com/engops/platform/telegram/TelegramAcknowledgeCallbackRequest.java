package com.engops.platform.telegram;

/**
 * Phase 175 — Telegram {@code answerCallbackQuery} uchun transport request.
 *
 * <p>Telegram inline button bosilganda yuboriladigan kichik, ephemeral
 * acknowledgement (toast). Field'lar Telegram Bot API'ning
 * {@code answerCallbackQuery} metodi bilan bir xil semantikaga ega:</p>
 * <ul>
 *   <li>{@code callbackQueryId} — Telegram'dan inbound kelgan callback
 *       so'rovining {@code id} field'i.</li>
 *   <li>{@code text} — operatorga ko'rinadigan qisqa matn (Telegram
 *       cheklovi {@code <= 200} simvol).</li>
 * </ul>
 *
 * <p>Ataylab kiritilmagan:</p>
 * <ul>
 *   <li>{@code show_alert} — Phase 175 default toast (false) bilan
 *       kifoyalanadi; modal popup intruziv va keng UX'ga ehtiyoj yo'q.</li>
 *   <li>{@code url}, {@code cache_time} — kerak emas.</li>
 *   <li>{@code parse_mode} — bu method Telegram'da {@code parse_mode}'ni
 *       qabul qilmaydi; outbound sendMessage qatlami bilan moslashtirilgan
 *       hold (Phase 158 dan plain text).</li>
 * </ul>
 *
 * <p>Validatsiya canonical constructor ichida bajariladi — null/blank
 * callbackQueryId, null/blank text va 200 simvoldan oshgan text rad
 * etiladi.</p>
 */
public record TelegramAcknowledgeCallbackRequest(String callbackQueryId, String text) {

    /** Telegram {@code answerCallbackQuery.text} cheklovi (200 simvol). */
    public static final int MAX_TEXT_LENGTH = 200;

    public TelegramAcknowledgeCallbackRequest {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException(
                    "callbackQueryId null yoki bo'sh bo'lishi mumkin emas");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text null yoki bo'sh bo'lishi mumkin emas");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "text uzunligi " + MAX_TEXT_LENGTH + " simvoldan oshmasligi shart");
        }
    }
}
