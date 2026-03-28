package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant konfiguratsiya details endpoint'ining HTTP response DTO'si.
 *
 * Compact structured sections bilan tenant'ning operatsion konfiguratsiya holatini ifodalaydi:
 * - tenant: asosiy metadata
 * - membershipsSummary: a'zolik countlari
 * - workflowSummary: workflow ta'rif countlari
 * - routingSummary: routing qoida countlari
 * - telegramSummary: Telegram binding countlari
 *
 * Bu "deep export" emas — admin landing/details foundation.
 *
 * @param tenant tenant metadata section
 * @param membershipsSummary a'zolik summary section
 * @param workflowSummary workflow summary section
 * @param routingSummary routing summary section
 * @param telegramSummary telegram summary section
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigDetailsResponse(
        TenantSection tenant,
        MembershipsSummarySection membershipsSummary,
        WorkflowSummarySection workflowSummary,
        RoutingSummarySection routingSummary,
        TelegramSummarySection telegramSummary) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TenantSection(
            UUID tenantId,
            String name,
            String slug,
            String timezone,
            String status,
            Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MembershipsSummarySection(
            int totalMembershipCount,
            int activeMembershipCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkflowSummarySection(
            int totalWorkflowDefinitionCount,
            int activeWorkflowDefinitionCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoutingSummarySection(
            int totalRoutingRuleCount,
            int activeRoutingRuleCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TelegramSummarySection(
            int activeChatBindingCount,
            int activeTopicBindingCount) {}
}
