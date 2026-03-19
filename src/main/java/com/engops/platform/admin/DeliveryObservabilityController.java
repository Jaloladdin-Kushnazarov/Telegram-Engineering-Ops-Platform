package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsFacade;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Delivery observability uchun read-only admin endpoint'lar.
 *
 * Ikki endpoint:
 * - GET /summary — tenant-scoped kompakt summary ro'yxat
 * - GET /details — bitta work item uchun to'liq details (recentAttempts bilan)
 *
 * Faqat GET — write operatsiya yo'q.
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'larga uzatadi
 * - Facade natijalarini response DTO'larga map qiladi
 * - ResourceNotFoundException (404) va IllegalArgumentException (400)
 *   GlobalExceptionHandler tomonidan qayta ishlanadi
 */
@RestController
@RequestMapping("/api/admin/delivery-observability")
public class DeliveryObservabilityController {

    private final TelegramDeliveryObservabilityDetailsFacade detailsFacade;
    private final DeliveryObservabilitySummaryFacade summaryFacade;

    public DeliveryObservabilityController(TelegramDeliveryObservabilityDetailsFacade detailsFacade,
                                           DeliveryObservabilitySummaryFacade summaryFacade) {
        this.detailsFacade = detailsFacade;
        this.summaryFacade = summaryFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning delivery observability summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @return kompakt summary ro'yxat
     */
    @GetMapping("/summary")
    public ResponseEntity<DeliveryObservabilitySummaryResponse> getSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit) {

        var items = summaryFacade.getSummaryList(tenantId, limit);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new DeliveryObservabilitySummaryResponse(responseItems));
    }

    /**
     * Bitta work item uchun delivery observability details qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi attempt'lar soni (1..50, default 10)
     * @return enriched delivery observability details
     */
    @GetMapping("/details")
    public ResponseEntity<DeliveryObservabilityDetailsResponse> getDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode,
            @RequestParam(defaultValue = "10") int historyLimit) {

        TelegramDeliveryObservabilityDetailsView details =
                detailsFacade.getDetails(tenantId, workItemCode, historyLimit);

        return ResponseEntity.ok(toResponse(details));
    }

    private DeliveryObservabilityDetailsResponse toResponse(
            TelegramDeliveryObservabilityDetailsView details) {
        return new DeliveryObservabilityDetailsResponse(
                details.workItemId(),
                details.workItemCode(),
                details.title(),
                details.typeCode().name(),
                details.currentStatusCode(),
                toMetricsResponse(details.latestMetrics()),
                details.recentAttempts().stream()
                        .map(this::toAttemptResponse)
                        .toList());
    }

    private DeliveryObservabilityDetailsResponse.LatestMetricsResponse toMetricsResponse(
            TelegramDeliveryMetricsSnapshot snapshot) {
        return new DeliveryObservabilityDetailsResponse.LatestMetricsResponse(
                snapshot.getTenantId(),
                snapshot.getWorkItemId(),
                snapshot.getOperation() != null ? snapshot.getOperation().name() : null,
                snapshot.getDeliveryOutcome() != null ? snapshot.getDeliveryOutcome().name() : null,
                snapshot.isSuccess(),
                snapshot.isRejected(),
                snapshot.isFailed(),
                snapshot.getFailureCode(),
                snapshot.hasExternalMessageId(),
                snapshot.isEmpty());
    }

    private DeliveryObservabilityDetailsResponse.DeliveryAttemptResponse toAttemptResponse(
            TelegramDeliveryAttempt attempt) {
        return new DeliveryObservabilityDetailsResponse.DeliveryAttemptResponse(
                attempt.getAttemptId(),
                attempt.getAttemptedAt(),
                attempt.getTenantId(),
                attempt.getWorkItemId(),
                attempt.getOperation().name(),
                attempt.getTargetChatBindingId(),
                attempt.getTargetTopicId(),
                attempt.getDeliveryOutcome().name(),
                attempt.getExternalMessageId(),
                attempt.getFailureCode(),
                attempt.getFailureReason(),
                attempt.isSuccess());
    }

    // ========== Summary mapping ==========

    private DeliveryObservabilitySummaryResponse.SummaryItemResponse toSummaryItemResponse(
            DeliveryObservabilitySummaryItem item) {
        return new DeliveryObservabilitySummaryResponse.SummaryItemResponse(
                item.workItemId(),
                item.workItemCode(),
                item.title(),
                item.typeCode().name(),
                item.currentStatusCode(),
                toMetricsSummaryResponse(item.latestMetrics()));
    }

    private DeliveryObservabilitySummaryResponse.MetricsSummaryResponse toMetricsSummaryResponse(
            TelegramDeliveryMetricsSnapshot snapshot) {
        return new DeliveryObservabilitySummaryResponse.MetricsSummaryResponse(
                snapshot.getDeliveryOutcome() != null ? snapshot.getDeliveryOutcome().name() : null,
                snapshot.isSuccess(),
                snapshot.isRejected(),
                snapshot.isFailed(),
                snapshot.getFailureCode(),
                snapshot.hasExternalMessageId(),
                snapshot.isEmpty());
    }
}
