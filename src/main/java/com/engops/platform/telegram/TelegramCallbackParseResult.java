package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Phase 173 — {@link TelegramCallbackQueryService#process(TelegramCallbackQueryRequest)}
 * tomonidan qaytariladigan parse natijasi.
 *
 * <p>{@link TelegramCallbackQueryService.CallbackOutcome#ACCEPTED} bo'lganda
 * {@code workItemId} va {@code actionCode} non-null bo'ladi. Boshqa
 * (ignored) outcomelar uchun ikkala maydon ham {@code null} bo'lishi
 * mumkin — parser bu maydonlarni faqat to'liq, valid callback_data uchun
 * to'ldiradi.</p>
 *
 * <p>Bu record parser <strong>parser-only</strong> bo'lib qolishi uchun
 * mavjud — {@link TelegramCallbackActionExecutionService} natijani olib
 * keyingi orchestration qadamlarini bajaradi.</p>
 */
public record TelegramCallbackParseResult(
        TelegramCallbackQueryService.CallbackOutcome outcome,
        UUID workItemId,
        String actionCode) {
}
