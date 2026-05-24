package com.engops.platform.identity.membership;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 219a — tenant a'zosining read-model proyeksiyasi.
 *
 * <p>Member management UI (Phase 219b) va REST {@code GET
 * /api/tenants/{tenantId}/members} javobi uchun view model. AppUser +
 * Membership + (birinchi) MembershipRoleBinding ma'lumotlarini bitta yassi
 * tuzilmaga yig'adi.</p>
 *
 * <p>{@code joinedAt} — Membership entity'sining {@code created_at} qiymati
 * ({@code BaseEntity}). Membership'da alohida {@code joined_at} ustuni yo'q,
 * shuning uchun yaratilgan vaqt a'zolikning boshlanishi sifatida ishlatiladi.</p>
 *
 * <p>{@code roleCode}/{@code roleName} — a'zoning birinchi rol bog'lanishi.
 * Phase 219a model'ida har bir membership bitta rolga ega ({@code changeRole}
 * eski bog'lanishlarni almashtiradi). Rol bo'lmasa {@code "NONE"}/{@code
 * "No role"} qaytariladi.</p>
 */
public record MemberSummary(
        UUID userId,
        Long telegramUserId,
        String displayName,
        String username,
        String roleCode,
        String roleName,
        String status,
        Instant joinedAt) {
}
