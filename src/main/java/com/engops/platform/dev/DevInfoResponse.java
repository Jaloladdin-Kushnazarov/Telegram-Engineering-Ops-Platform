package com.engops.platform.dev;

import java.util.UUID;

/**
 * Phase 211 — /api/dev/auth/info javob shakli.
 *
 * @param devMode               har doim true (controller faqat dev-mode'da
 *                              yaratiladi — production'da endpoint 404)
 * @param bootstrapAdminUserId  bootstrap admin AppUser UUID
 *                              ({@link DevBootstrapInitializer#BOOTSTRAP_ADMIN_USER_ID})
 * @param firstTenantId         bootstrap tenant UUID
 *                              ({@link DevBootstrapInitializer#BOOTSTRAP_TENANT_ID})
 */
public record DevInfoResponse(boolean devMode,
                              UUID bootstrapAdminUserId,
                              UUID firstTenantId) {
}
