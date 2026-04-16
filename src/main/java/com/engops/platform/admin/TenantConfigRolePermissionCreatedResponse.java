package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Role-permission binding yaratish natijasi uchun HTTP response DTO.
 *
 * @param bindingId yaratilgan binding identifikatori
 * @param roleId rol identifikatori
 * @param roleCode rol kodi (global katalogdan)
 * @param permissionId ruxsat identifikatori
 * @param permissionCode ruxsat kodi (global katalogdan)
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRolePermissionCreatedResponse(
        UUID bindingId,
        UUID roleId,
        String roleCode,
        UUID permissionId,
        String permissionCode,
        Instant createdAt) {}
