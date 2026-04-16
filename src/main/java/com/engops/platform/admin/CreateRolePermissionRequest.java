package com.engops.platform.admin;

import java.util.UUID;

/**
 * Role-permission binding yaratish uchun HTTP request DTO.
 *
 * roleId request body'da emas — endpoint URL path'da keladi.
 *
 * @param permissionId global ruxsat identifikatori (required)
 */
public record CreateRolePermissionRequest(UUID permissionId) {}
