package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant a'zolik ro'yxati uchun HTTP response DTO.
 *
 * Har bir membership uchun compact flat item qaytaradi:
 * membershipId, userId, telegramUserId, displayName, username,
 * membershipStatus, roleNames, createdAt.
 *
 * Deep user detail yoki role permission kiritilmagan — bu list, detail emas.
 *
 * @param tenantId tenant identifikatori
 * @param items a'zolik ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigMembershipListResponse(
        UUID tenantId,
        List<MembershipItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MembershipItem(
            UUID membershipId,
            UUID userId,
            Long telegramUserId,
            String displayName,
            String username,
            String membershipStatus,
            List<String> roleNames,
            Instant createdAt) {}
}
