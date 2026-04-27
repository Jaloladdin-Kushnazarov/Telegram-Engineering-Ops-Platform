package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Global permission to'liq detail uchun HTTP response DTO.
 *
 * Permission GLOBAL — tenantga tegishli emas. tenantId endpoint-family
 * izchilligi va admin authorization context uchun mavjud.
 *
 * Bu DTO rol-permission bog'lanishlarini QAYTARMAYDI — buning uchun alohida
 * endpoint mavjud: GET /permissions/{permissionId}/roles.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param permissionId global ruxsat identifikatori
 * @param code ruxsat kodi
 * @param description ixtiyoriy tavsif (null bo'lsa omit)
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigPermissionDetailResponse(
        UUID tenantId,
        UUID permissionId,
        String code,
        String description,
        Instant createdAt) {}
