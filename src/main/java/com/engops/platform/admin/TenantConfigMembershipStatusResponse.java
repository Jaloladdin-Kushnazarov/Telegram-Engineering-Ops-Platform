package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership status o'zgarishi natijasi uchun HTTP response DTO.
 *
 * Activate va suspend yo'llarida qayta ishlatiladi.
 *
 * @param tenantId tenant identifikatori
 * @param membershipId a'zolik identifikatori
 * @param userId foydalanuvchi identifikatori
 * @param status yangi membership holati (ACTIVE, SUSPENDED, REMOVED)
 * @param createdAt a'zolik yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigMembershipStatusResponse(
        UUID tenantId,
        UUID membershipId,
        UUID userId,
        String status,
        Instant createdAt) {}
