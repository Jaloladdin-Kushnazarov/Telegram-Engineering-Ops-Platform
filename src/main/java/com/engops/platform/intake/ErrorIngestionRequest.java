package com.engops.platform.intake;

import java.util.List;
import java.util.UUID;

/**
 * Phase 203 — POST /api/intake/errors endpoint uchun HTTP request DTO.
 *
 * <p>SDK / agentlar production application'larida exception / error report'larini
 * shu DTO orqali yuboradi. Validatsiya {@link ErrorIngestionService}'da
 * authoritative — controller faqat thin adapter (Phase 195/199 admin DTO
 * uslublariga muvofiq, Bean Validation YO'Q).</p>
 *
 * <p>Field'lar:</p>
 * <ul>
 *   <li>{@code tenantId} — required (SDK qaysi tenant ekanini biladi).</li>
 *   <li>{@code sourceService} — required, 1..100 (masalan "payment-api").</li>
 *   <li>{@code errorMessage} — required, 1..500 (human-readable error string).</li>
 *   <li>{@code errorStackTrace} — optional, 0..5000 (raw stack trace).</li>
 *   <li>{@code severityHint} — optional enum CRITICAL/HIGH/MEDIUM/LOW; default MEDIUM
 *       agar httpStatusCode ham yo'q bo'lsa.</li>
 *   <li>{@code httpStatusCode} — optional (5xx → CRITICAL, 4xx → HIGH, boshqa → MEDIUM
 *       agar severityHint berilmagan bo'lsa).</li>
 *   <li>{@code environment} — optional, 0..50 (e.g. "production"; title'ga prepend qilinadi).</li>
 *   <li>{@code tags} — optional, 0..10 entry, har biri 1..50; description'ga qo'shiladi va
 *       audit payload'ga tagCount sifatida (qiymatlar emas) yoziladi.</li>
 * </ul>
 */
public record ErrorIngestionRequest(
        UUID tenantId,
        String sourceService,
        String errorMessage,
        String errorStackTrace,
        String severityHint,
        Integer httpStatusCode,
        String environment,
        List<String> tags) {}
