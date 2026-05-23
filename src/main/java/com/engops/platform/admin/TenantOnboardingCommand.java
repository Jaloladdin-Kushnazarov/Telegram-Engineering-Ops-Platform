package com.engops.platform.admin;

import java.util.List;
import java.util.UUID;

/**
 * Phase 199 — onboarding service uchun ichki command DTO.
 *
 * Controller'dan {@link TenantOnboardingService}'ga uzatiladigan immutable
 * yozuv. {@code actorUserId} JWT SecurityContext'idan {@code @CurrentActor}
 * orqali olinadi (request body'dan emas).
 */
public record TenantOnboardingCommand(
        String tenantName,
        String tenantSlug,
        String tenantTimezone,
        Long adminTelegramUserId,
        String adminDisplayName,
        String adminUsername,
        List<String> workflowTemplateCodes,
        UUID actorUserId) {}
