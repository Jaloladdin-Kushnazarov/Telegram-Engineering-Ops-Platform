package com.engops.platform.identity.membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Phase 219a — tenant a'zosini invite qilish so'rovi.
 *
 * <p>{@code roleCode} tayinlanadigan rol kodi — service layer'da whitelist
 * (ADMIN / ENGINEER / TESTER / VIEWER) bo'yicha tekshiriladi. Ownership
 * (TENANT_OWNER) va platform-level rollar invite orqali berilmaydi —
 * ular alohida danger-zone oqimlari (Phase 221+).</p>
 *
 * <p>Bean validation ({@code @Valid} controller'da) majburiy maydonlarni
 * tekshiradi; {@code roleCode} qiymat tekshiruvi (whitelist) service'da
 * {@code BusinessRuleException} sifatida amalga oshadi.</p>
 */
public record InviteMemberRequest(
        @NotNull Long telegramUserId,
        @NotBlank String displayName,
        String username,
        @NotBlank String roleCode) {
}
