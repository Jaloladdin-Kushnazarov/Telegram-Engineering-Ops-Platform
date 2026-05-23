package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phase 200 — Telegram inbound {@code message} minimal DTO (bot command path).
 *
 * <p>Telegram Bot API'ning <em>Message</em> obyektidan faqat bot command
 * dispatcher uchun zarur bo'lgan maydonlar olinadi:</p>
 * <ul>
 *   <li>{@code from} — xabar yuborgan Telegram user (faqat numeric id).</li>
 *   <li>{@code chat} — xabar qaysi chatda yuborilgan (reply targeting uchun).</li>
 *   <li>{@code text} — xabar matni; agar "/" bilan boshlansa command sifatida
 *       parse qilinadi.</li>
 * </ul>
 *
 * <p>Boshqa Message maydonlari ({@code message_id}, {@code date},
 * {@code reply_to_message}, va h.k.) ataylab e'tiborsiz qoldiriladi
 * ({@code @JsonIgnoreProperties}). Callback'larda ishlatiladigan
 * {@link TelegramCallbackUserRequest} va {@link TelegramCallbackChatRequest}
 * minimal DTO'lari reuse qilinadi — Phase 200 ularning {@code id} maydoni
 * bilan to'liq kifoyalanadi.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessageRequest(
        TelegramCallbackUserRequest from,
        TelegramCallbackChatRequest chat,
        String text) {}
