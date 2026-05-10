package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phase 171 — Telegram {@code callback_query} minimal DTO.
 *
 * <p>Faqat Phase 171 doirasida zarur bo'lgan maydonlar:</p>
 * <ul>
 *   <li>{@code id} — callback_query identifier (kelajakda
 *       {@code answerCallbackQuery} uchun kerak; Phase 171 da log
 *       attribution).</li>
 *   <li>{@code from} — bosilgan Telegram user (faqat numeric id).</li>
 *   <li>{@code message} — qaysi card ustida bosildi (chat + message_id).</li>
 *   <li>{@code data} — outbound {@code callback_data} sifatida yuborilgan
 *       string. Phase 171 da {@code "<UUID workItemId>:<ACTION_CODE>"}
 *       formatida.</li>
 * </ul>
 *
 * <p>Boshqa CallbackQuery maydonlari ({@code chat_instance},
 * {@code inline_message_id}, {@code game_short_name} va h.k.) ataylab
 * e'tiborsiz qoldiriladi.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQueryRequest(
        String id,
        TelegramCallbackUserRequest from,
        TelegramCallbackMessageRequest message,
        String data) {}
