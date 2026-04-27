package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Topic binding to'liq detail uchun HTTP response DTO.
 *
 * Header field'lari + nested parent chat binding context.
 * Topic binding child entity bo'lib, parent chat kontekst majburiy ko'rsatiladi —
 * shu sababli nested obyekt struktura tanlangan.
 *
 * @param tenantId tenant identifikatori
 * @param topicBindingId topic binding identifikatori
 * @param topicId Telegram topic identifikatori
 * @param topicName topic nomi (null bo'lsa omit)
 * @param purpose topic maqsadi
 * @param active aktiv holat
 * @param createdAt yaratilgan vaqt
 * @param parentChatBinding ota chat binding konteksti (id, chatId, chatTitle, bindingType)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigTopicBindingDetailResponse(
        UUID tenantId,
        UUID topicBindingId,
        long topicId,
        String topicName,
        String purpose,
        boolean active,
        Instant createdAt,
        ParentChatBinding parentChatBinding) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParentChatBinding(
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType) {}
}
