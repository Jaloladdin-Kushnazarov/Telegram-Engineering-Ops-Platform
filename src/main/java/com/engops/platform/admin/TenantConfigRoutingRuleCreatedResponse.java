package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Routing rule yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId tenant identifikatori
 * @param ruleId yaratilgan routing rule identifikatori
 * @param name rule nomi
 * @param workItemType work item turi
 * @param priority rule prioriteti
 * @param targetTopicBindingId target topic binding (nullable)
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoutingRuleCreatedResponse(
        UUID tenantId,
        UUID ruleId,
        String name,
        String workItemType,
        int priority,
        UUID targetTopicBindingId,
        boolean active,
        Instant createdAt) {}
