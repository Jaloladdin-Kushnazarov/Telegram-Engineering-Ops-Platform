package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 171 — Telegram {@code Update} minimal DTO.
 *
 * <p>Telegram Bot API'ning <em>Update</em> obyektidan faqat Phase 171 da
 * kerak bo'lgan ikki maydon olinadi:</p>
 * <ul>
 *   <li>{@code update_id} — Telegram'ning monotonik update identifier'i
 *       (log attribution uchun).</li>
 *   <li>{@code callback_query} — agar update inline button bosilishi
 *       sababli kelgan bo'lsa, callback_query payload'i. Aks holda null
 *       (oddiy message, edited_message, channel_post va h.k. — Phase 171
 *       ataylab e'tiborsiz qoldiradi va 200 OK qaytaradi).</li>
 * </ul>
 *
 * <p>Boshqa Update maydonlari ({@code message}, {@code edited_message},
 * {@code channel_post}, {@code my_chat_member} va h.k.) ataylab
 * e'tiborsiz qoldiriladi (@JsonIgnoreProperties).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(
        @JsonProperty("update_id") Long updateId,
        @JsonProperty("callback_query") TelegramCallbackQueryRequest callbackQuery) {}
