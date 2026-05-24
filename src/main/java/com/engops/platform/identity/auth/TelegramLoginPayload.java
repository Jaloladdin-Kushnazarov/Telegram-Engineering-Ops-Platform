package com.engops.platform.identity.auth;

/**
 * Phase 218a — Telegram Login Widget'dan kelgan payload.
 *
 * <p>Telegram protokolida quyidagi maydonlar yuboriladi
 * (https://core.telegram.org/widgets/login#receiving-authorization-data):</p>
 * <ul>
 *   <li>{@code id} — BIGINT telegram_user_id (majburiy)</li>
 *   <li>{@code first_name} — String (majburiy)</li>
 *   <li>{@code last_name} — String (ixtiyoriy)</li>
 *   <li>{@code username} — String (ixtiyoriy)</li>
 *   <li>{@code photo_url} — String (ixtiyoriy)</li>
 *   <li>{@code auth_date} — Long Unix seconds (majburiy)</li>
 *   <li>{@code hash} — String hex HMAC-SHA256 (majburiy)</li>
 * </ul>
 *
 * <p>JSON binding (Spring MVC default) — JSON kalitlar
 * {@code @JsonProperty} annotation'siz Java camelCase'iga mos kelishi
 * uchun {@code spring.jackson.property-naming-strategy=SNAKE_CASE} yoki
 * frontend payload'ni camelCase'da yuborishi kerak. Phase 218b widget
 * JSON'ni camelCase'da quradi (frontend control).</p>
 */
public record TelegramLoginPayload(
        Long id,
        String firstName,
        String lastName,
        String username,
        String photoUrl,
        Long authDate,
        String hash) {
}
