package com.engops.platform.analytics;

import java.util.List;
import java.util.UUID;

/**
 * Phase 205 — service'dan controller'ga uzatiladigan ichki aggregate natija.
 * Controller {@link AnalyticsAggregateResponse} ga aylantiradi.
 */
public record AnalyticsAggregateResult(
        UUID tenantId,
        long totalCount,
        List<AnalyticsBucket> buckets) {

    public AnalyticsAggregateResult {
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }
}
