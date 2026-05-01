package com.engops.platform.admin;

/**
 * AppUser yaratish uchun HTTP request DTO.
 *
 * adminContextTenantId bu DTO ichida emas — endpoint query param sifatida keladi
 * va u FAQAT authorization uchun ishlatiladi (yaratiladigan user emas, membership emas).
 *
 * Telegram username asosiy identity emas — telegramUserId yagona ishonchli
 * tashqi identifikator (loyiha xavfsizlik qoidasi).
 *
 * @param telegramUserId Telegram identifikatori (required, positive long, unique)
 * @param username Telegram username (nullable; facade'da blank → null konversiya, max 255)
 * @param displayName ko'rinish nomi (nullable; facade'da blank → null konversiya, max 255)
 */
public record CreateAppUserRequest(
        Long telegramUserId,
        String username,
        String displayName) {}
