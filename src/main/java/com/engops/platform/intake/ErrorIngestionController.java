package com.engops.platform.intake;

import com.engops.platform.infrastructure.security.CurrentActor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Phase 203 — POST /api/intake/errors endpoint'i: SDK / agent'lardan keladigan
 * production error report'lar uchun ingestion sirti.
 *
 * <p><strong>Sirt:</strong> {@code POST /api/intake/errors}. Request body —
 * {@link ErrorIngestionRequest}. Muvaffaqiyat — 201 Created bilan
 * {@link ErrorIngestionResponse}, va {@code Location} header'i yangi
 * work item'ning admin read URL'iga ishora qiladi.</p>
 *
 * <p><strong>Xavfsizlik:</strong> {@code @CurrentActor UUID actorUserId}
 * SecurityContext'dan resolve qilinadi (request body'dagi har qanday
 * createdByUserId / actor maydoniga e'tibor berilmaydi). Permission
 * tekshiruvi service layer'da — mavjud {@code WORK_ITEM_CREATE} ruxsati
 * (D4, yangi permission yo'q).</p>
 *
 * <p><strong>Exception mapping (GlobalExceptionHandler):</strong>
 * {@code AccessDeniedException} → 403; {@code BusinessRuleException} → 422
 * standart envelope ({@code errorCode}, {@code message}, va h.k.);
 * {@code IllegalArgumentException} → 400.</p>
 *
 * <p><strong>Out of scope:</strong> bot command (Phase 204+),
 * INCIDENT_INGEST granular permission, batch ingestion, rate limiting.</p>
 */
@RestController
@RequestMapping("/api/intake/errors")
public class ErrorIngestionController {

    private final ErrorIngestionService errorIngestionService;

    public ErrorIngestionController(ErrorIngestionService errorIngestionService) {
        this.errorIngestionService = errorIngestionService;
    }

    @PostMapping
    public ResponseEntity<ErrorIngestionResponse> submit(
            @RequestBody(required = false) ErrorIngestionRequest request,
            @CurrentActor UUID actorUserId) {

        ErrorIngestionRequest body = requireBody(request);

        ErrorIngestionCommand command = new ErrorIngestionCommand(
                body.tenantId(),
                body.sourceService(),
                body.errorMessage(),
                body.errorStackTrace(),
                body.severityHint(),
                body.httpStatusCode(),
                body.environment(),
                body.tags(),
                actorUserId);

        ErrorIngestionResult result = errorIngestionService.ingest(command);

        ErrorIngestionResponse response = new ErrorIngestionResponse(
                result.workItemId(),
                result.tenantId(),
                result.title(),
                result.workItemType(),
                result.severityCode(),
                result.statusCode(),
                result.createdAt());

        URI location = URI.create(
                "/api/admin/work-items/details/by-id?tenantId="
                        + result.tenantId()
                        + "&workItemId=" + result.workItemId());

        return ResponseEntity.created(location).body(response);
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new IllegalArgumentException("Request body null bo'lishi mumkin emas");
        }
        return body;
    }
}
