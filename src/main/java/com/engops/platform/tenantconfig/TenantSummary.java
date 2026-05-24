package com.engops.platform.tenantconfig;

import com.engops.platform.tenantconfig.model.Tenant;

import java.util.UUID;

/**
 * Phase 210 — compact tenant DTO for selector dropdowns and read-only
 * cross-tenant listings.
 *
 * <p>{@code displayName} is the operator-facing label that maps to the
 * underlying {@link Tenant#getName()}. The {@code displayName} accessor
 * name keeps the web template wording stable while honoring the Phase
 * 195+ Tenant entity invariant where the column is {@code name}.</p>
 */
public record TenantSummary(UUID id, String slug, String displayName) {

    public static TenantSummary from(Tenant t) {
        return new TenantSummary(t.getId(), t.getSlug(), t.getName());
    }
}
