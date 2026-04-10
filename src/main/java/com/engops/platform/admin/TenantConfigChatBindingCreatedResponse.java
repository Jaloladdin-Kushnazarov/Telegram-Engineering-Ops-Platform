package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat binding yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId tenant identifikatori
 * @param chatBindingId yaratilgan chat binding identifikatori
 * @param chatId Telegram chat identifikatori
 * @param chatTitle chat sarlavhasi (nullable)
 * @param bindingType binding turi
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigChatBindingCreatedResponse(
        UUID tenantId,
        UUID chatBindingId,
        long chatId,
        String chatTitle,
        String bindingType,
        boolean active,
        Instant createdAt) {}
