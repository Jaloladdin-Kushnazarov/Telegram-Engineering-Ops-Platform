package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Work item summary listing endpoint'ining HTTP response DTO'si.
 *
 * Kompakt summary ro'yxat — update history va description yo'q.
 * Full details uchun mavjud /details endpoint ishlatiladi.
 *
 * @param items summary item'lar ro'yxati
 */
public record WorkItemSummaryResponse(
        List<SummaryItemResponse> items) {

    /**
     * Bitta work item uchun kompakt summary.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryItemResponse(
            UUID workItemId,
            String workItemCode,
            String title,
            String typeCode,
            String currentStatusCode,
            String priorityCode,
            String severityCode,
            UUID currentOwnerUserId,
            Instant openedAt,
            Instant lastTransitionAt,
            Instant resolvedAt,
            int reopenedCount,
            boolean archived) {}
}
