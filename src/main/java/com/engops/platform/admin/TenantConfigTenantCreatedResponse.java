package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId yaratilgan tenant identifikatori
 * @param name tenant nomi
 * @param slug tenant slug (lowercase normallashgan)
 * @param timezone tenant vaqt zonasi
 * @param status tenant holati (ACTIVE/SUSPENDED/ARCHIVED)
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigTenantCreatedResponse(
        UUID tenantId,
        String name,
        String slug,
        String timezone,
        String status,
        Instant createdAt) {}
