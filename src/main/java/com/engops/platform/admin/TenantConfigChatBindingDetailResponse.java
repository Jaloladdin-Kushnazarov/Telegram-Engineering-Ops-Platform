package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chat binding to'liq detail uchun HTTP response DTO.
 *
 * Header field'lari + nested topicBindings[] ro'yxati.
 * Nested topic item parent context'siz qaytadi (chat header outer level'da).
 *
 * @param tenantId tenant identifikatori
 * @param chatBindingId chat binding identifikatori
 * @param chatId Telegram chat identifikatori
 * @param chatTitle chat sarlavhasi (null bo'lsa omit)
 * @param bindingType binding turi (MAIN_GROUP / NOTIFICATION_GROUP)
 * @param active aktiv holat
 * @param createdAt yaratilgan vaqt
 * @param topicBindings shu chat binding ichidagi topic bog'lanishlar
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigChatBindingDetailResponse(
        UUID tenantId,
        UUID chatBindingId,
        long chatId,
        String chatTitle,
        String bindingType,
        boolean active,
        Instant createdAt,
        List<TopicItem> topicBindings) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicItem(
            UUID topicBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt) {}
}
