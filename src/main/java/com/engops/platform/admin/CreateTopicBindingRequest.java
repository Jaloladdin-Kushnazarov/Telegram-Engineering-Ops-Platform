package com.engops.platform.admin;

import java.util.UUID;

/**
 * Topic binding yaratish uchun HTTP request DTO.
 *
 * tenantId bu DTO ichida emas — endpoint query param sifatida keladi.
 *
 * @param chatBindingId ota chat binding identifikatori (required)
 * @param topicId Telegram topic identifikatori (required)
 * @param topicName topic nomi (nullable)
 * @param purpose topic maqsadi (required)
 */
public record CreateTopicBindingRequest(
        UUID chatBindingId,
        Long topicId,
        String topicName,
        String purpose) {}
