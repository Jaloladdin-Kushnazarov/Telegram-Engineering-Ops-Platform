package com.engops.platform.identity.membership;

import jakarta.validation.constraints.NotBlank;

/**
 * Phase 219a — a'zoning rolini o'zgartirish so'rovi.
 *
 * <p>{@code newRoleCode} service layer'da whitelist (ADMIN / ENGINEER /
 * TESTER / VIEWER) bo'yicha tekshiriladi va a'zoning mavjud rol
 * bog'lanish(lar)ini almashtiradi.</p>
 */
public record ChangeRoleRequest(
        @NotBlank String newRoleCode) {
}
