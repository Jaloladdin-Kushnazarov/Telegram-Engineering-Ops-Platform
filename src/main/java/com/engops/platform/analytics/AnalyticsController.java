package com.engops.platform.analytics;

import com.engops.platform.infrastructure.security.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 205 — read-only analytics REST surface.
 *
 * <p>3 ta GET endpoint, har biri {@code tenantId} query param qabul qiladi
 * va {@link AnalyticsAggregateResponse} qaytaradi (200 OK).</p>
 * <ul>
 *   <li>{@code GET /api/analytics/work-items/by-status?tenantId={uuid}}</li>
 *   <li>{@code GET /api/analytics/work-items/by-type?tenantId={uuid}}</li>
 *   <li>{@code GET /api/analytics/work-items/by-severity?tenantId={uuid}}</li>
 * </ul>
 *
 * <p><strong>Xavfsizlik:</strong> {@code @CurrentActor} SecurityContext'dan;
 * authorization service-layer'da ({@code AdminAuthorizationService.authorizeRead}
 * → existing {@code TENANT_CONFIG_READ} permission). Bo'sh JWT → 401.
 * Yo'q ruxsat → 403.</p>
 *
 * <p><strong>Out of scope (Phase 205):</strong> pagination, date-range
 * filter, custom dimensions, bot command, dashboard UI.</p>
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/work-items/by-status")
    public AnalyticsAggregateResponse byStatus(@RequestParam UUID tenantId,
                                                @CurrentActor UUID actorUserId) {
        return toResponse(analyticsQueryService.workItemsByStatus(tenantId, actorUserId));
    }

    @GetMapping("/work-items/by-type")
    public AnalyticsAggregateResponse byType(@RequestParam UUID tenantId,
                                              @CurrentActor UUID actorUserId) {
        return toResponse(analyticsQueryService.workItemsByType(tenantId, actorUserId));
    }

    @GetMapping("/work-items/by-severity")
    public AnalyticsAggregateResponse bySeverity(@RequestParam UUID tenantId,
                                                  @CurrentActor UUID actorUserId) {
        return toResponse(analyticsQueryService.workItemsBySeverity(tenantId, actorUserId));
    }

    private static AnalyticsAggregateResponse toResponse(AnalyticsAggregateResult result) {
        return new AnalyticsAggregateResponse(
                result.tenantId(), result.totalCount(), result.buckets());
    }
}
