package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
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
 * Yetti endpoint:
 * - GET /summary — tenant-scoped kompakt summary ro'yxat
 * - GET /summary/by-status — status bo'yicha filtrlangan delivery summary ro'yxat
 * - GET /summary/by-owner — owner bo'yicha filtrlangan delivery summary ro'yxat
 * - GET /details — bitta work item uchun to'liq details (workItemCode bo'yicha)
 * - GET /details/by-id — bitta work item uchun to'liq details (workItemId bo'yicha)
 * - GET /details/by-status — status bo'yicha filtrlangan delivery details ro'yxat
 * - GET /details/by-owner — owner bo'yicha filtrlangan delivery details ro'yxat
 *
 * Faqat GET — write operatsiya yo'q.
 * Barcha endpoint'lar joriy actor'ni Spring SecurityContext'dan {@link CurrentActor}
 * argument resolver orqali oladi (Phase 128). Avvalgi {@code X-Actor-User-Id}
 * header endi ishlatilmaydi — JWT'dan kelgan {@code AuthenticatedActor} yagona
 * actor manbai. Authentication SecurityContext'da bo'lmasa resolver
 * AccessDeniedException tashlaydi va GlobalExceptionHandler 403 qaytaradi.
 * Authorization facade boundary'da amalga oshiriladi — controller thin adapter
 * bo'lib qoladi.
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'larga uzatadi
 * - Facade natijalarini response DTO'larga map qiladi
 * - ResourceNotFoundException (404), IllegalArgumentException (400),
 *   AccessDeniedException (403) GlobalExceptionHandler tomonidan qayta ishlanadi
 */
@RestController
@RequestMapping("/api/admin/delivery-observability")
public class DeliveryObservabilityController {

    private final DeliveryObservabilityDetailsByCodeFacade detailsByCodeFacade;
    private final DeliveryObservabilitySummaryReadFacade summaryReadFacade;
    private final DeliveryObservabilityDetailsByIdFacade detailsByIdFacade;
    private final DeliveryObservabilitySummaryByStatusFacade summaryByStatusFacade;
    private final DeliveryObservabilitySummaryByOwnerFacade summaryByOwnerFacade;
    private final DeliveryObservabilityDetailsByStatusFacade detailsByStatusFacade;
    private final DeliveryObservabilityDetailsByOwnerFacade detailsByOwnerFacade;

    public DeliveryObservabilityController(DeliveryObservabilityDetailsByCodeFacade detailsByCodeFacade,
                                           DeliveryObservabilitySummaryReadFacade summaryReadFacade,
                                           DeliveryObservabilityDetailsByIdFacade detailsByIdFacade,
                                           DeliveryObservabilitySummaryByStatusFacade summaryByStatusFacade,
                                           DeliveryObservabilitySummaryByOwnerFacade summaryByOwnerFacade,
                                           DeliveryObservabilityDetailsByStatusFacade detailsByStatusFacade,
                                           DeliveryObservabilityDetailsByOwnerFacade detailsByOwnerFacade) {
        this.detailsByCodeFacade = detailsByCodeFacade;
        this.summaryReadFacade = summaryReadFacade;
        this.detailsByIdFacade = detailsByIdFacade;
        this.summaryByStatusFacade = summaryByStatusFacade;
        this.summaryByOwnerFacade = summaryByOwnerFacade;
        this.detailsByStatusFacade = detailsByStatusFacade;
        this.detailsByOwnerFacade = detailsByOwnerFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning delivery observability summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @param actorUserId joriy actor identifikatori
     * @return kompakt summary ro'yxat
     */
    @GetMapping("/summary")
    public ResponseEntity<DeliveryObservabilitySummaryResponse> getSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryReadFacade.getSummaryList(tenantId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new DeliveryObservabilitySummaryResponse(responseItems));
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning delivery summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50, default 20)
     * @param actorUserId joriy actor identifikatori
     * @return status-filtered delivery summary ro'yxat
     */
    @GetMapping("/summary/by-status")
    public ResponseEntity<DeliveryObservabilitySummaryResponse> getSummaryByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryByStatusFacade.getSummaryList(tenantId, statusCode, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new DeliveryObservabilitySummaryResponse(responseItems));
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning delivery summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @param actorUserId joriy actor identifikatori
     * @return owner-filtered delivery summary ro'yxat
     */
    @GetMapping("/summary/by-owner")
    public ResponseEntity<DeliveryObservabilitySummaryResponse> getSummaryByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit, actorUserId);

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
     * @param actorUserId joriy actor identifikatori
     * @return enriched delivery observability details
     */
    @GetMapping("/details")
    public ResponseEntity<DeliveryObservabilityDetailsResponse> getDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode,
            @RequestParam(defaultValue = "10") int historyLimit,
            @CurrentActor UUID actorUserId) {

        TelegramDeliveryObservabilityDetailsView details =
                detailsByCodeFacade.getDetails(tenantId, workItemCode, historyLimit, actorUserId);

        return ResponseEntity.ok(toResponse(details));
    }

    /**
     * Bitta work item uchun delivery observability details qaytaradi (UUID bo'yicha).
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @param historyLimit so'nggi attempt'lar soni (1..50, default 10)
     * @param actorUserId joriy actor identifikatori
     * @return enriched delivery observability details
     */
    @GetMapping("/details/by-id")
    public ResponseEntity<DeliveryObservabilityDetailsResponse> getDetailsById(
            @RequestParam UUID tenantId,
            @RequestParam UUID workItemId,
            @RequestParam(defaultValue = "10") int historyLimit,
            @CurrentActor UUID actorUserId) {

        TelegramDeliveryObservabilityDetailsView details =
                detailsByIdFacade.getDetails(tenantId, workItemId, historyLimit, actorUserId);

        return ResponseEntity.ok(toResponse(details));
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning delivery observability details ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50, default 20)
     * @param actorUserId joriy actor identifikatori
     * @return status-filtered delivery details ro'yxat
     */
    @GetMapping("/details/by-status")
    public ResponseEntity<DeliveryObservabilityDetailsByStatusResponse> getDetailsByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = detailsByStatusFacade.getDetailsList(tenantId, statusCode, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new DeliveryObservabilityDetailsByStatusResponse(responseItems));
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning delivery observability details ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @param actorUserId joriy actor identifikatori
     * @return owner-filtered delivery details ro'yxat
     */
    @GetMapping("/details/by-owner")
    public ResponseEntity<DeliveryObservabilityDetailsByOwnerResponse> getDetailsByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = detailsByOwnerFacade.getDetailsList(tenantId, ownerUserId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new DeliveryObservabilityDetailsByOwnerResponse(responseItems));
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
