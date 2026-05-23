package com.engops.platform.intake;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 203 — POST /api/intake/errors uchun HTTP response DTO.
 *
 * Yaratilgan WorkItem (INCIDENT) identifikatori, derived title va severity,
 * boshlang'ich status va ingestion timestamp.
 */
public record ErrorIngestionResponse(
        UUID workItemId,
        UUID tenantId,
        String title,
        String workItemType,
        String severityCode,
        String statusCode,
        Instant createdAt) {}
