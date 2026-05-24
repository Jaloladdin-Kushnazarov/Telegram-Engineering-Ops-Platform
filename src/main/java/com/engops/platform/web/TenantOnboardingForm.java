package com.engops.platform.web;

/**
 * Phase 213 — Tenant onboarding HTML form DTO.
 *
 * <p>Web-layer DTO. Controller {@code TenantOnboardingService}'ning
 * {@code TenantOnboardingCommand}'iga adapt qiladi (admin module byte-frozen).
 * Form'da bo'lmagan maydonlar uchun controller defaults beradi
 * ({@code workflowTemplateCodes = ["BUG_MINIMAL"]}, {@code adminUsername = null}).</p>
 *
 * <p>Spring Boot 3.x records'ni form binding sifatida qo'llab-quvvatlaydi —
 * default constructor kerak emas.</p>
 */
public record TenantOnboardingForm(
        String name,
        String slug,
        Long adminTelegramUserId,
        String adminDisplayName,
        String timezone) {
}
