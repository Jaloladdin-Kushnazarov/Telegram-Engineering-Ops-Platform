package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership to'liq detail uchun HTTP response DTO.
 *
 * Header field'lari + nested user identity konteksti.
 * Membership child entity bo'lib, AppUser parent identity bilan birga
 * majburiy ko'rsatiladi — shu sababli nested obyekt struktura tanlangan.
 *
 * Bu endpoint a'zolikning rol items'ini QAYTARMAYDI — buning uchun alohida
 * endpoint mavjud: GET /memberships/{membershipId}/roles.
 *
 * @param tenantId tenant identifikatori
 * @param membershipId a'zolik identifikatori
 * @param membershipStatus a'zolik holati (ACTIVE/SUSPENDED/REMOVED)
 * @param createdAt yaratilgan vaqt
 * @param userIdentity foydalanuvchi identity konteksti
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigMembershipDetailResponse(
        UUID tenantId,
        UUID membershipId,
        String membershipStatus,
        Instant createdAt,
        UserIdentity userIdentity) {

    /**
     * Foydalanuvchi identity konteksti.
     *
     * @param userId foydalanuvchi identifikatori
     * @param telegramUserId Telegram identifikatori (asosiy identity)
     * @param displayName ko'rsatish uchun nom (null bo'lsa omit)
     * @param username Telegram username (null bo'lsa omit)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserIdentity(
            UUID userId,
            Long telegramUserId,
            String displayName,
            String username) {}
}
