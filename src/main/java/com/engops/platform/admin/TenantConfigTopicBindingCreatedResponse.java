package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Topic binding write natijasi uchun HTTP response DTO.
 *
 * Create, update, activate va deactivate yo'llarida qayta ishlatiladi.
 *
 * @param tenantId tenant identifikatori
 * @param topicBindingId topic binding identifikatori
 * @param chatBindingId ota chat binding identifikatori
 * @param topicId Telegram topic identifikatori
 * @param topicName topic nomi (nullable)
 * @param purpose topic maqsadi
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigTopicBindingCreatedResponse(
        UUID tenantId,
        UUID topicBindingId,
        UUID chatBindingId,
        long topicId,
        String topicName,
        String purpose,
        boolean active,
        Instant createdAt) {}
