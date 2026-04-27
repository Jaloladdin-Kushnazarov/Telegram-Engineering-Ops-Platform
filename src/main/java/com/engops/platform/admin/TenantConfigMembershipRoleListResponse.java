package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Berilgan a'zolik (membership) uchun unga biriktirilgan global rol'lar
 * ro'yxati uchun HTTP response DTO.
 *
 * Header field'lari (membershipId, userId, membershipStatus) caller'ga
 * a'zolik kontekstini yana lookup qilishga muhtoj qilmaslik uchun kiritilgan.
 *
 * Item shakli global rol catalog item bilan bir xil:
 * roleId, code, name, description, systemRole, createdAt.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param membershipId a'zolik identifikatori
 * @param userId a'zolik biriktirilgan foydalanuvchi identifikatori
 * @param membershipStatus a'zolik holati (ACTIVE/SUSPENDED/REMOVED)
 * @param items biriktirilgan rol item'lar ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigMembershipRoleListResponse(
        UUID tenantId,
        UUID membershipId,
        UUID userId,
        String membershipStatus,
        List<RoleItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoleItem(
            UUID roleId,
            String code,
            String name,
            String description,
            boolean systemRole,
            Instant createdAt) {}
}
