package com.engops.platform.admin;

import java.util.UUID;

/**
 * Phase 190 — work item priority kodini yangilash uchun HTTP request DTO.
 *
 * <p>tenantId va priorityCode request body'da keladi. workItemId endpoint URL
 * path variable'da keladi.</p>
 *
 * <p>{@code priorityCode} qiymati MVP bounded enum-like:
 * {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL}. Boshqa qiymatlar
 * {@link com.engops.platform.workitem.WorkItemCommandService#updatePriority}
 * tomonidan {@code BusinessRuleException("INVALID_PRIORITY_CODE", ...)}
 * orqali rad etiladi (422 javob).</p>
 *
 * @param tenantId tenant identifikatori (required)
 * @param priorityCode yangi priority kodi (required, bounded enum-like)
 */
public record UpdateWorkItemPriorityRequest(UUID tenantId, String priorityCode) {}
