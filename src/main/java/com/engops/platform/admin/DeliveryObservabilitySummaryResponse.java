package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Delivery observability summary listing endpoint'ining HTTP response DTO'si.
 *
 * Kompakt summary ro'yxat — recentAttempts yo'q, faqat latest metrics summary.
 * Full details uchun mavjud /details endpoint ishlatiladi.
 *
 * @param items summary item'lar ro'yxati
 */
public record DeliveryObservabilitySummaryResponse(
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
            MetricsSummaryResponse latestMetrics) {}

    /**
     * Latest delivery metrics summary — list kontekstida faqat kerakli field'lar.
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
