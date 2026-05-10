package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phase 171 — Telegram {@code callback_query.from} minimal DTO.
 *
 * <p>Faqat {@code id} (Telegram numeric user identifier) saqlanadi —
 * Phase 171 da bu maydon log attribution sifatida ishlatiladi
 * (callback bosgan operatorni MDC'da kuzatish uchun). Authoritative
 * platform actor identity uchun YETARLI EMAS — Telegram→app user
 * mapping keyingi phase'da hal qilinadi.</p>
 *
 * <p>Boshqa Telegram User obyektining maydonlari ({@code is_bot},
 * {@code first_name}, {@code username} va h.k.) ataylab e'tiborsiz
 * qoldiriladi.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackUserRequest(Long id) {}
