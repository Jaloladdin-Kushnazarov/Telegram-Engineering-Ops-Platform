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
 * Delivery observability details uchun read-only admin endpoint.
 *
 * TelegramDeliveryObservabilityDetailsFacade'ni HTTP orqali expose qiladi.
 * Faqat GET — write operatsiya yo'q.
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'ga uzatadi
 * - Facade natijasini response DTO'ga map qiladi
 * - ResourceNotFoundException (404) va IllegalArgumentException (400)
 *   GlobalExceptionHandler tomonidan qayta ishlanadi
 *
 * Mapping:
 * TelegramDeliveryObservabilityDetailsView -> DeliveryObservabilityDetailsResponse
 * Bu mapping shu controller ichida — alohida mapper kerak emas (trivial).
 */
@RestController
@RequestMapping("/api/admin/delivery-observability")
public class DeliveryObservabilityController {

    private final TelegramDeliveryObservabilityDetailsFacade detailsFacade;

    public DeliveryObservabilityController(TelegramDeliveryObservabilityDetailsFacade detailsFacade) {
        this.detailsFacade = detailsFacade;
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
}
