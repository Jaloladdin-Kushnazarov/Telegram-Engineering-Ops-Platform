package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phase 171 — Telegram {@code callback_query.message.chat} minimal DTO.
 *
 * <p>Faqat {@code id} saqlanadi (Telegram chat identifier; group/supergroup
 * uchun negative integer, bot-private uchun positive). Boshqa Chat
 * maydonlari ({@code type}, {@code title} va h.k.) ataylab e'tiborsiz
 * qoldiriladi.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackChatRequest(Long id) {}
