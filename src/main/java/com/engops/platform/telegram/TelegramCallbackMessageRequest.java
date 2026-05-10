package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 171 — Telegram {@code callback_query.message} minimal DTO.
 *
 * <p>Faqat {@code messageId} (callback bosilgan kartaning Telegram message
 * identifier'i) va {@code chat} (chat konteksti) saqlanadi. Boshqa Message
 * maydonlari ({@code text}, {@code from}, {@code date}, {@code reply_markup}
 * va h.k.) ataylab e'tiborsiz qoldiriladi.</p>
 *
 * <p>{@code message_id} kelajakda {@code editMessageText} yoki
 * {@code answerCallbackQuery} chaqiruvlari uchun foydali — Phase 171 da
 * faqat log attribution.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackMessageRequest(
        @JsonProperty("message_id") Long messageId,
        TelegramCallbackChatRequest chat) {}
