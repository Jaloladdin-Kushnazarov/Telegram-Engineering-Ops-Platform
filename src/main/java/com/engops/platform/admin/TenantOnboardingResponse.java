package com.engops.platform.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 199 — POST /api/admin/tenants endpoint uchun HTTP response DTO.
 *
 * Onboarding amalga oshirilgandan keyin tenant + admin AppUser + admin
 * Membership + tenant ichida yaratilgan barcha workflow_definition
 * ma'lumotlarini qaytaradi. Telegram routing config (chat/topic/routing
 * rule) bu javobga kirmaydi — operator alohida endpoint'lar orqali
 * sozlaydi.
 */
public record TenantOnboardingResponse(
        UUID tenantId,
        String tenantSlug,
        String tenantName,
        Instant createdAt,
        UUID adminAppUserId,
        UUID adminMembershipId,
        List<WorkflowDefinitionSummary> workflowDefinitions) {

    /**
     * Yaratilgan har bir workflow_definition uchun qisqacha summary
     * (id, manba shablon kodi, work item turi).
     */
    public record WorkflowDefinitionSummary(
            UUID workflowDefinitionId,
            String templateCode,
            String workItemType) {}
}
