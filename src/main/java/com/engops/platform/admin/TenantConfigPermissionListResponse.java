package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Global ruxsat katalogi ro'yxati uchun HTTP response DTO.
 *
 * Ruxsatlar GLOBAL — tenantga tegishli emas. tenantId endpoint-family
 * izchilligi va admin kontekst validatsiyasi uchun mavjud.
 *
 * Har bir ruxsat uchun compact item qaytaradi:
 * permissionId, code, description, createdAt.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param items global ruxsat katalogi ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigPermissionListResponse(
        UUID tenantId,
        List<PermissionItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PermissionItem(
            UUID permissionId,
            String code,
            String description,
            Instant createdAt) {}
}
