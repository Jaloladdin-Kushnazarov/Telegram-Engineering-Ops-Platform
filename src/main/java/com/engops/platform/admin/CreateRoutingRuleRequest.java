package com.engops.platform.admin;

import java.util.UUID;

/**
 * Routing rule yaratish uchun HTTP request DTO.
 *
 * tenantId bu DTO ichida emas — endpoint query param sifatida keladi.
 *
 * @param name rule nomi (required)
 * @param workItemType work item turi: BUG, INCIDENT, TASK (required)
 * @param priority rule prioriteti (required)
 * @param targetTopicBindingId target topic binding (nullable)
 * @param conditionExpression shart ifodasi (nullable)
 */
public record CreateRoutingRuleRequest(
        String name,
        String workItemType,
        int priority,
        UUID targetTopicBindingId,
        String conditionExpression) {}
