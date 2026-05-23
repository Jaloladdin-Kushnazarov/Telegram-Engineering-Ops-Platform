package com.engops.platform.intake;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 203 — service'dan controller'ga uzatiladigan ichki natija.
 * Controller {@link ErrorIngestionResponse}'ga aylantiradi.
 */
public record ErrorIngestionResult(
        UUID workItemId,
        UUID tenantId,
        String title,
        String workItemType,
        String severityCode,
        String statusCode,
        Instant createdAt) {}
