package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Berilgan global rol uchun biriktirilgan ruxsat (permission) ro'yxati uchun HTTP response DTO.
 *
 * Rol va ruxsat ikkalasi ham GLOBAL — tenantga tegishli emas. tenantId
 * endpoint-family izchilligi va admin kontekst validatsiyasi uchun mavjud.
 *
 * Header field'lari (roleId, roleCode, roleName) caller'ga rolni topish uchun
 * qo'shimcha lookup talab qilmaslik uchun kiritilgan.
 *
 * Item shakli global permission catalog item bilan bir xil:
 * permissionId, code, description, createdAt.
 *
 * @param tenantId admin kontekst tenant identifikatori
 * @param roleId global rol identifikatori
 * @param roleCode global rol kodi
 * @param roleName global rol ko'rsatish nomi
 * @param items biriktirilgan ruxsat item'lar ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRolePermissionListResponse(
        UUID tenantId,
        UUID roleId,
        String roleCode,
        String roleName,
        List<PermissionItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PermissionItem(
            UUID permissionId,
            String code,
            String description,
            Instant createdAt) {}
}
