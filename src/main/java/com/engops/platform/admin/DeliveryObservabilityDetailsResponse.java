package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Delivery observability details endpoint'ining HTTP response DTO'si.
 *
 * TelegramDeliveryObservabilityDetailsView ichki modelidan
 * tashqi HTTP kontraktiga aniq mapping.
 *
 * Ichki model'ni to'g'ridan-to'g'ri expose qilmaydi —
 * bu record alohida response contract vazifasini bajaradi.
 *
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi
 * @param title work item sarlavhasi
 * @param typeCode work item turi (BUG, INCIDENT, TASK)
 * @param currentStatusCode hozirgi holat kodi
 * @param latestMetrics eng so'nggi delivery metrics
 * @param recentAttempts so'nggi delivery attempt'lar ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryObservabilityDetailsResponse(
        UUID workItemId,
        String workItemCode,
        String title,
        String typeCode,
        String currentStatusCode,
        LatestMetricsResponse latestMetrics,
        List<DeliveryAttemptResponse> recentAttempts) {

    /**
     * Latest delivery metrics nested response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatestMetricsResponse(
            UUID tenantId,
            UUID workItemId,
            String operation,
            String deliveryOutcome,
            boolean success,
            boolean rejected,
            boolean failed,
            String failureCode,
            boolean hasExternalMessageId,
            boolean empty) {}

    /**
     * Bitta delivery attempt nested response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryAttemptResponse(
            UUID attemptId,
            Instant attemptedAt,
            UUID tenantId,
            UUID workItemId,
            String operation,
            UUID targetChatBindingId,
            Long targetTopicId,
            String deliveryOutcome,
            Long externalMessageId,
            String failureCode,
            String failureReason,
            boolean success) {}
}
