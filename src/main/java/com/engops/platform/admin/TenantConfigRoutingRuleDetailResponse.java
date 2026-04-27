package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Routing rule to'liq detail uchun HTTP response DTO.
 *
 * Header field'lari + ixtiyoriy nested targetTopicBinding context (topic +
 * parent chat). Agar routing rule'da targetTopicBindingId null bo'lsa yoki
 * target tenant ichida topilmasa, nested object butunlay JSON'dan omit bo'ladi.
 *
 * @param tenantId tenant identifikatori
 * @param ruleId routing rule identifikatori
 * @param name rule nomi
 * @param workItemType work item turi (BUG, INCIDENT, TASK)
 * @param priority prioritet
 * @param conditionExpression shart ifodasi (null bo'lsa omit)
 * @param active aktiv holat
 * @param createdAt yaratilgan vaqt
 * @param targetTopicBinding nested target context (null bo'lsa omit)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoutingRuleDetailResponse(
        UUID tenantId,
        UUID ruleId,
        String name,
        String workItemType,
        int priority,
        String conditionExpression,
        boolean active,
        Instant createdAt,
        TargetTopicBinding targetTopicBinding) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetTopicBinding(
            UUID topicBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String chatBindingType) {}
}
