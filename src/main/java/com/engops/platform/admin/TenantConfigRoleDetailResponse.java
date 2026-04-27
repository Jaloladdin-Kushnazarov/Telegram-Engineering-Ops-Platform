package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Global rol to'liq detail uchun HTTP response DTO.
 *
 * Rol GLOBAL — tenantga tegishli emas. tenantId endpoint-family izchilligi
 * va admin authorization context uchun mavjud.
 *
 * Detail kompakt list view (`TenantConfigRoleListResponse.RoleItem`) bermaydigan
 * `active` operatsion holatni ham yetkazadi.
 *
 * Bu DTO permission'lar ro'yxatini QAYTARMAYDI — buning uchun alohida endpoint
 * mavjud: GET /roles/{roleId}/permissions.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param roleId global rol identifikatori
 * @param code rol kodi
 * @param name rol ko'rsatish nomi
 * @param description ixtiyoriy tavsif (null bo'lsa omit)
 * @param systemRole tizim roli ekanligi (immutable bo'lsa true)
 * @param active aktiv holat
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoleDetailResponse(
        UUID tenantId,
        UUID roleId,
        String code,
        String name,
        String description,
        boolean systemRole,
        boolean active,
        Instant createdAt) {}
