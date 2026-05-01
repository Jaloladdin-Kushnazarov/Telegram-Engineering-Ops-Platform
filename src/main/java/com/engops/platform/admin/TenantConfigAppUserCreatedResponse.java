package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * AppUser yaratish natijasi uchun HTTP response DTO.
 *
 * @param userId yaratilgan user identifikatori
 * @param telegramUserId Telegram identifikatori
 * @param username Telegram username (nullable)
 * @param displayName ko'rinish nomi (nullable)
 * @param status user holati (ACTIVE/SUSPENDED/DEACTIVATED)
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigAppUserCreatedResponse(
        UUID userId,
        Long telegramUserId,
        String username,
        String displayName,
        String status,
        Instant createdAt) {}
