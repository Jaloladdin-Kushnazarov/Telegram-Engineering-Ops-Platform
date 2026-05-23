package com.engops.platform.analytics;

import java.util.List;
import java.util.UUID;

/**
 * Phase 205 — HTTP response DTO. Uniform shape for all three
 * /api/analytics/work-items/by-{status,type,severity} endpoints.
 */
public record AnalyticsAggregateResponse(
        UUID tenantId,
        long totalCount,
        List<AnalyticsBucket> buckets) {}
