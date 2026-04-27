package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Berilgan global ruxsat (permission) uchun unga biriktirilgan rol'lar
 * ro'yxati uchun HTTP response DTO.
 *
 * Rol va ruxsat ikkalasi ham GLOBAL — tenantga tegishli emas. tenantId
 * endpoint-family izchilligi va admin kontekst validatsiyasi uchun mavjud.
 *
 * Header field'lari (permissionId, permissionCode) caller'ga ruxsatni topish
 * uchun qo'shimcha lookup talab qilmaslik uchun kiritilgan.
 *
 * Item shakli global rol catalog item bilan bir xil:
 * roleId, code, name, description, systemRole, createdAt.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param permissionId global ruxsat identifikatori
 * @param permissionCode global ruxsat kodi
 * @param items biriktirilgan rol item'lar ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigPermissionRoleListResponse(
        UUID tenantId,
        UUID permissionId,
        String permissionCode,
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
