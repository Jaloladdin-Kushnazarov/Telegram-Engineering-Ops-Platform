package com.engops.platform.platform;

import com.engops.platform.tenantconfig.model.Tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 217a — PLATFORM_OWNER uchun read model.
 *
 * <p>Tenant entity'idan cross-tenant ko'rish uchun ajratilgan compact
 * DTO. Phase 210'dagi {@link com.engops.platform.tenantconfig.TenantSummary}
 * tenant selector dropdown uchun (id+slug+displayName). Bu record platform
 * darajasidagi to'liq tenant overview uchun — status, timestamp'lar va
 * timezone qo'shilgan.</p>
 *
 * <p>{@code status} sifatida {@link com.engops.platform.tenantconfig.model.TenantStatus}
 * enum'ining {@code name()} qiymati uzatiladi (ACTIVE / SUSPENDED / ARCHIVED).
 * Bu UI rendering uchun tilsiz, deterministik kod beradi va kelajakda
 * yangi status qo'shilsa avtomatik prosaylash.</p>
 */
public record PlatformTenantSummary(
        UUID id,
        String name,
        String slug,
        String timezone,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static PlatformTenantSummary from(Tenant tenant) {
        return new PlatformTenantSummary(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}
