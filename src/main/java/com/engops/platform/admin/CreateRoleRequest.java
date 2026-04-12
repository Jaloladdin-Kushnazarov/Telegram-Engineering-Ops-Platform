package com.engops.platform.admin;

/**
 * Global rol yaratish uchun HTTP request DTO.
 *
 * @param code rol kodi (required, unique, uppercase normalize qilinadi)
 * @param name rol nomi (required)
 * @param description ixtiyoriy tavsif (nullable)
 */
public record CreateRoleRequest(
        String code,
        String name,
        String description) {}
