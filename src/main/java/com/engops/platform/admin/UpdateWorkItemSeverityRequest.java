package com.engops.platform.admin;

import java.util.UUID;

/**
 * Phase 190 — work item severity kodini yangilash uchun HTTP request DTO.
 *
 * <p>tenantId va severityCode request body'da keladi. workItemId endpoint URL
 * path variable'da keladi.</p>
 *
 * <p>{@code severityCode} qiymati MVP bounded enum-like:
 * {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL}. Boshqa qiymatlar
 * {@link com.engops.platform.workitem.WorkItemCommandService#updateSeverity}
 * tomonidan {@code BusinessRuleException("INVALID_SEVERITY_CODE", ...)}
 * orqali rad etiladi (422 javob).</p>
 *
 * @param tenantId tenant identifikatori (required)
 * @param severityCode yangi severity kodi (required, bounded enum-like)
 */
public record UpdateWorkItemSeverityRequest(UUID tenantId, String severityCode) {}
