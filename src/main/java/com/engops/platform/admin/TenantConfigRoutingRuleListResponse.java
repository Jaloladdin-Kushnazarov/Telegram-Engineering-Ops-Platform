package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant routing qoidalari ro'yxati uchun HTTP response DTO.
 *
 * Har bir routing rule uchun compact item qaytaradi:
 * ruleId, name, workItemType, priority, targetTopicBindingId, active, createdAt.
 *
 * conditionExpression kiritilmagan — TEXT ustun, list darajasi uchun juda og'ir.
 *
 * @param tenantId tenant identifikatori
 * @param items routing qoidalari ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoutingRuleListResponse(
        UUID tenantId,
        List<RoutingRuleItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoutingRuleItem(
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            UUID targetTopicBindingId,
            boolean active,
            Instant createdAt) {}
}
