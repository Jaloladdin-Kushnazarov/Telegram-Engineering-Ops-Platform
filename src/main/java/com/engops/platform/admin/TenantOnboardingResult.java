package com.engops.platform.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 199 — onboarding service'idan controller'ga uzatiladigan ichki natija
 * DTO. Service orqali yig'iladi va controller {@link TenantOnboardingResponse}
 * ga aylantiradi.
 */
public record TenantOnboardingResult(
        UUID tenantId,
        String tenantSlug,
        String tenantName,
        Instant createdAt,
        UUID adminAppUserId,
        UUID adminMembershipId,
        List<WorkflowDefinitionSummary> workflowDefinitions) {

    public record WorkflowDefinitionSummary(
            UUID workflowDefinitionId,
            String templateCode,
            String workItemType) {}
}
