package com.engops.platform.admin;

import java.util.List;

/**
 * Phase 199 — POST /api/admin/tenants endpoint uchun HTTP request DTO.
 *
 * Validatsiya {@link TenantOnboardingService}'da authoritative — controller
 * faqat thin adapter. Bu DTO mavjud admin*Request DTO'lar uslubiga (record,
 * Bean Validation YO'Q, service-layer validatsiyasi) muvofiq.
 */
public record TenantOnboardingRequest(
        String tenantName,
        String tenantSlug,
        String tenantTimezone,
        Long adminTelegramUserId,
        String adminDisplayName,
        String adminUsername,
        List<String> workflowTemplateCodes) {}
