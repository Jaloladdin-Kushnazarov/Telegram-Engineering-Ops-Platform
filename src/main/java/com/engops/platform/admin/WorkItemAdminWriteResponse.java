package com.engops.platform.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 190 — work item admin write operatsiyalari uchun response DTO.
 *
 * <p>Owner assignment, priority update va severity update endpoint'lari
 * uchun yagona barqaror shape. Faqat operator uchun zarur bo'lgan stable
 * field'lar — internal entity bevosita ko'rsatilmaydi.</p>
 *
 * <p>Field'lar mavjud {@link WorkItemDetailsResponse} bilan nom-mosligi
 * saqlanadi (UI/admin tool ergonomikasi uchun).</p>
 *
 * @param tenantId tenant identifikatori
 * @param workItemId work item identifikatori
 * @param workItemCode kompakt operator-friendly kod (masalan {@code BUG-1})
 * @param currentStatusCode joriy holat kodi
 * @param currentOwnerUserId joriy owner (null bo'lishi mumkin)
 * @param priorityCode joriy priority (null bo'lishi mumkin)
 * @param severityCode joriy severity (null bo'lishi mumkin)
 * @param updatedAt entity oxirgi marta yangilangan vaqt (UTC)
 */
public record WorkItemAdminWriteResponse(
        UUID tenantId,
        UUID workItemId,
        String workItemCode,
        String currentStatusCode,
        UUID currentOwnerUserId,
        String priorityCode,
        String severityCode,
        Instant updatedAt) {}
