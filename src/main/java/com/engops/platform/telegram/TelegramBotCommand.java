package com.engops.platform.telegram;

/**
 * Phase 200 — Telegram bot command interfeysi.
 *
 * <p>Har bir bot buyrug'i (masalan {@code /start}, {@code /help})
 * shu interfeysning bitta {@code @Component} implementatsiyasi orqali
 * registrlanadi. {@link TelegramBotCommandRegistry} Spring tomonidan
 * yig'ilgan barcha implement'larni boshlanish vaqtida indekslaydi.</p>
 *
 * <p>Reply matni plain text (no Markdown / HTML / parse_mode).
 * Telegram limit'i — bitta xabar uchun maksimum 4000 belgi.</p>
 *
 * <p><strong>Invariantlar:</strong></p>
 * <ul>
 *   <li>{@link #commandName()} doim "/" bilan boshlanadi va bitta token
 *       (bo'sh joy yo'q).</li>
 *   <li>{@link #execute(TelegramBotCommandContext)} hech qachon null
 *       qaytarmaydi; null/blank qaytsa dispatcher xatolik qaydi qiladi.</li>
 *   <li>Implementor stateless va deterministic bo'lishi kerak (registry
 *       singleton sifatida saqlaydi).</li>
 * </ul>
 */
public interface TelegramBotCommand {

    /**
     * Buyruq nomi, "/" bilan boshlanadi (masalan {@code "/help"}).
     */
    String commandName();

    /**
     * Buyruqni bajaradi va Telegram'ga yuboriladigan plain-text reply
     * matnini qaytaradi.
     *
     * @param context resolved actor + tenant + parsed arguments
     * @return reply text (non-null, non-blank, &le; 4000 belgi)
     */
    String execute(TelegramBotCommandContext context);
}
