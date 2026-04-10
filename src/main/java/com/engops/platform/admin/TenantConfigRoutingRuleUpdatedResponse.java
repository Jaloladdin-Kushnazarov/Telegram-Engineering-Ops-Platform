package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Routing rule yangilash natijasi uchun HTTP response DTO.
 *
 * workItemType update surface'ga kiritilmagan — bu field faqat create va read response'larda mavjud.
 *
 * @param tenantId tenant identifikatori
 * @param ruleId routing rule identifikatori
 * @param name rule nomi
 * @param priority rule prioriteti
 * @param targetTopicBindingId target topic binding (nullable)
 * @param conditionExpression shart ifodasi (nullable)
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigRoutingRuleUpdatedResponse(
        UUID tenantId,
        UUID ruleId,
        String name,
        int priority,
        UUID targetTopicBindingId,
        String conditionExpression,
        boolean active,
        Instant createdAt) {}
