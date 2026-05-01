package com.engops.platform.admin;

/**
 * Tenant yaratish uchun HTTP request DTO.
 *
 * adminContextTenantId bu DTO ichida emas — endpoint query param sifatida keladi
 * va u FAQAT authorization uchun ishlatiladi (yaratiladigan tenant emas).
 *
 * @param name tenant nomi (required, non-blank, max 255)
 * @param slug tenant slug — global unikal identifikator (required, non-blank, max 100,
 *             facade boundary'da lowercase qilinadi)
 * @param timezone tenant vaqt zonasi (nullable; null/blank bo'lsa facade
 *                  "UTC" default qiymat o'rnatadi; max 50)
 */
public record CreateTenantRequest(
        String name,
        String slug,
        String timezone) {}
