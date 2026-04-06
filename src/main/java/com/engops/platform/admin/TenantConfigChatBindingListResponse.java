package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant Telegram chat bog'lanishlari ro'yxati uchun HTTP response DTO.
 *
 * Har bir chat binding uchun compact item qaytaradi:
 * chatBindingId, chatId, chatTitle, bindingType, active, activeTopicBindingCount, createdAt.
 *
 * Nested topic binding ro'yxati kiritilmagan — bu list, detail emas.
 *
 * @param tenantId tenant identifikatori
 * @param items chat bog'lanishlari ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigChatBindingListResponse(
        UUID tenantId,
        List<ChatBindingItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatBindingItem(
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            int activeTopicBindingCount,
            Instant createdAt) {}
}
