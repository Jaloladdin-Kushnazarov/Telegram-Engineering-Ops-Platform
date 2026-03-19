package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Combined work item + delivery observability support summary listing response DTO.
 *
 * Kompakt combined ro'yxat — har bir item ikki nested section'dan iborat:
 * - workItem: work item metadata summary
 * - deliveryObservability: delivery metrics summary
 *
 * Full details uchun mavjud /support-details endpoint ishlatiladi.
 *
 * @param items combined summary item'lar ro'yxati
 */
public record WorkItemSupportSummaryResponse(
        List<SupportSummaryItemResponse> items) {

    /**
     * Bitta work item uchun combined support summary.
     */
    public record SupportSummaryItemResponse(
            WorkItemSectionResponse workItem,
            DeliveryObservabilitySectionResponse deliveryObservability) {}

    /**
     * Work item metadata summary section.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkItemSectionResponse(
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

    /**
     * Delivery observability metrics summary section.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryObservabilitySectionResponse(
            UUID workItemId,
            String workItemCode,
            String title,
            String typeCode,
            String currentStatusCode,
            MetricsSummaryResponse latestMetrics) {}

    /**
     * Latest delivery metrics summary.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetricsSummaryResponse(
            String deliveryOutcome,
            boolean success,
            boolean rejected,
            boolean failed,
            String failureCode,
            boolean hasExternalMessageId,
            boolean empty) {}
}
