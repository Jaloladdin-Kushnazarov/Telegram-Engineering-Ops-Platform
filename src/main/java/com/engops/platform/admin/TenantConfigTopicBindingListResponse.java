package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant Telegram topic bog'lanishlari ro'yxati uchun HTTP response DTO.
 *
 * Har bir topic binding uchun compact flat item qaytaradi:
 * topicBindingId, chatBindingId, chatId, chatTitle, topicId, topicName, purpose, active, createdAt.
 *
 * Chat binding kontekst field'lari (chatId, chatTitle) flat sifatida kiritilgan —
 * nested object emas. Nested chat binding yoki topic details kiritilmagan.
 *
 * @param tenantId tenant identifikatori
 * @param items topic bog'lanishlari ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigTopicBindingListResponse(
        UUID tenantId,
        List<TopicBindingItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicBindingItem(
            UUID topicBindingId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt) {}
}
