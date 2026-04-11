package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership-role binding yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId tenant identifikatori
 * @param membershipId a'zolik identifikatori
 * @param bindingId yaratilgan binding identifikatori
 * @param roleId rol identifikatori
 * @param roleCode rol kodi (global katalogdan)
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigMembershipRoleBindingCreatedResponse(
        UUID tenantId,
        UUID membershipId,
        UUID bindingId,
        UUID roleId,
        String roleCode,
        Instant createdAt) {}
