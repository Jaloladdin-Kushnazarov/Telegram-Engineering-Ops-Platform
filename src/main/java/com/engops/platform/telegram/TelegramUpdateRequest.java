package com.engops.platform.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 171 / 200 — Telegram {@code Update} minimal DTO.
 *
 * <p>Telegram Bot API'ning <em>Update</em> obyektidan faqat kerakli
 * maydonlar olinadi:</p>
 * <ul>
 *   <li>{@code update_id} — Telegram'ning monotonik update identifier'i
 *       (log attribution uchun).</li>
 *   <li>{@code callback_query} — Phase 171: inline button bosilishi
 *       sababli kelgan callback_query payload'i. Aks holda null.</li>
 *   <li>{@code message} — Phase 200: oddiy text xabar (bot command
 *       dispatcher). Agar {@code text} "/" bilan boshlansa,
 *       {@link TelegramBotCommandService} bajariladi. Aks holda webhook
 *       e'tiborsiz qoldiradi.</li>
 * </ul>
 *
 * <p>Boshqa Update maydonlari ({@code edited_message}, {@code channel_post},
 * {@code my_chat_member} va h.k.) ataylab e'tiborsiz qoldiriladi
 * ({@code @JsonIgnoreProperties}).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(
        @JsonProperty("update_id") Long updateId,
        @JsonProperty("callback_query") TelegramCallbackQueryRequest callbackQuery,
        @JsonProperty("message") TelegramMessageRequest message) {}
