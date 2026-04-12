package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Global rol yaratish/yangilash natijasi uchun HTTP response DTO.
 *
 * @param roleId rol identifikatori
 * @param code rol kodi
 * @param name rol nomi
 * @param description tavsif (nullable)
 * @param systemRole tizim roli belgisi
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoleCatalogResponse(
        UUID roleId,
        String code,
        String name,
        String description,
        boolean systemRole,
        boolean active,
        Instant createdAt) {}
