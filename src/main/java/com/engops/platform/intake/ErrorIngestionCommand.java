package com.engops.platform.intake;

import java.util.List;
import java.util.UUID;

/**
 * Phase 203 — internal command DTO. Controller'dan {@link ErrorIngestionService}'ga
 * uzatiladigan immutable yozuv. {@code actorUserId} SecurityContext'dan
 * {@code @CurrentActor} orqali olinadi (request body'dan emas).
 */
public record ErrorIngestionCommand(
        UUID tenantId,
        String sourceService,
        String errorMessage,
        String errorStackTrace,
        String severityHint,
        Integer httpStatusCode,
        String environment,
        List<String> tags,
        UUID actorUserId) {}
