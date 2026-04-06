package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Global rol katalogi ro'yxati uchun HTTP response DTO.
 *
 * Rollar GLOBAL — tenantga tegishli emas. tenantId endpoint-family
 * izchilligi va admin kontekst validatsiyasi uchun mavjud.
 *
 * Har bir rol uchun compact item qaytaradi:
 * roleId, code, name, description, systemRole, createdAt.
 *
 * Permission set yoki deep detail kiritilmagan — bu list, detail emas.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param items global rol katalogi ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoleListResponse(
        UUID tenantId,
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
