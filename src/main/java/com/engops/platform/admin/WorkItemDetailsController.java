package com.engops.platform.admin;

import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.telegram.TelegramDeliveryMetricsSnapshot;
import com.engops.platform.telegram.TelegramDeliveryObservabilityDetailsView;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * WorkItem uchun read-only admin endpoint'lar.
 *
 * Endpoint'lar:
 * - GET /summary — tenant-scoped kompakt work item ro'yxat
 * - GET /by-status — tenant-scoped status bo'yicha aktiv work item ro'yxat
 * - GET /by-owner — tenant-scoped owner bo'yicha aktiv work item ro'yxat
 * - GET /details — tenant-scoped work item details + update history (by code)
 * - GET /details/by-id — tenant-scoped work item details + update history (by UUID)
 * - GET /support-summary — combined work item + delivery observability summary ro'yxat
 * - GET /support-summary/by-status — status bo'yicha combined support summary ro'yxat
 * - GET /support-summary/by-owner — owner bo'yicha combined support summary ro'yxat
 * - GET /support-details — combined work item details + delivery observability (by code)
 * - GET /support-details/by-id — combined work item details + delivery observability (by UUID)
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
@RequestMapping("/api/admin/work-items")
public class WorkItemDetailsController {

    private final WorkItemDetailsFacade detailsFacade;
    private final WorkItemSummaryFacade summaryFacade;
    private final WorkItemSupportDetailsFacade supportDetailsFacade;
    private final WorkItemSupportSummaryFacade supportSummaryFacade;
    private final WorkItemSupportDetailsByIdFacade supportDetailsByIdFacade;
    private final WorkItemDetailsByIdFacade detailsByIdFacade;
    private final WorkItemSummaryByStatusFacade summaryByStatusFacade;
    private final WorkItemSummaryByOwnerFacade summaryByOwnerFacade;
    private final WorkItemSupportSummaryByStatusFacade supportSummaryByStatusFacade;
    private final WorkItemSupportSummaryByOwnerFacade supportSummaryByOwnerFacade;

    public WorkItemDetailsController(WorkItemDetailsFacade detailsFacade,
                                     WorkItemSummaryFacade summaryFacade,
                                     WorkItemSupportDetailsFacade supportDetailsFacade,
                                     WorkItemSupportSummaryFacade supportSummaryFacade,
                                     WorkItemSupportDetailsByIdFacade supportDetailsByIdFacade,
                                     WorkItemDetailsByIdFacade detailsByIdFacade,
                                     WorkItemSummaryByStatusFacade summaryByStatusFacade,
                                     WorkItemSummaryByOwnerFacade summaryByOwnerFacade,
                                     WorkItemSupportSummaryByStatusFacade supportSummaryByStatusFacade,
                                     WorkItemSupportSummaryByOwnerFacade supportSummaryByOwnerFacade) {
        this.detailsFacade = detailsFacade;
        this.summaryFacade = summaryFacade;
        this.supportDetailsFacade = supportDetailsFacade;
        this.supportSummaryFacade = supportSummaryFacade;
        this.supportDetailsByIdFacade = supportDetailsByIdFacade;
        this.detailsByIdFacade = detailsByIdFacade;
        this.summaryByStatusFacade = summaryByStatusFacade;
        this.summaryByOwnerFacade = summaryByOwnerFacade;
        this.supportSummaryByStatusFacade = supportSummaryByStatusFacade;
        this.supportSummaryByOwnerFacade = supportSummaryByOwnerFacade;
    }

    /**
     * Tenant uchun aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @return kompakt summary ro'yxat
     */
    @GetMapping("/summary")
    public ResponseEntity<WorkItemSummaryResponse> getSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit) {

        var items = summaryFacade.getSummaryList(tenantId, limit);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50, default 20)
     * @return kompakt summary ro'yxat
     */
    @GetMapping("/by-status")
    public ResponseEntity<WorkItemSummaryResponse> getByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit) {

        var items = summaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning kompakt summary ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @return kompakt summary ro'yxat
     */
    @GetMapping("/by-owner")
    public ResponseEntity<WorkItemSummaryResponse> getByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit) {

        var items = summaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    /**
     * Tenant uchun combined support summary ro'yxatini qaytaradi:
     * work item metadata + delivery observability summary.
     *
     * @param tenantId tenant identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @return combined support summary ro'yxat
     */
    @GetMapping("/support-summary")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit) {

        var items = supportSummaryFacade.getSummaryList(tenantId, limit);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    /**
     * Tenant + statusCode bo'yicha aktiv work item'larning combined support summary qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param statusCode holat kodi (masalan "BUGS", "PROCESSING")
     * @param limit maksimal natija soni (1..50, default 20)
     * @return combined support summary ro'yxat
     */
    @GetMapping("/support-summary/by-status")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummaryByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit) {

        var items = supportSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    /**
     * Tenant + ownerUserId bo'yicha aktiv work item'larning combined support summary qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param ownerUserId owner user identifikatori
     * @param limit maksimal natija soni (1..50, default 20)
     * @return combined support summary ro'yxat
     */
    @GetMapping("/support-summary/by-owner")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummaryByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit) {

        var items = supportSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    /**
     * Bitta work item uchun combined support details qaytaradi:
     * work item metadata + update history + delivery observability.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50, default 10)
     * @return combined support details
     */
    @GetMapping("/support-details")
    public ResponseEntity<WorkItemSupportDetailsResponse> getSupportDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode,
            @RequestParam(defaultValue = "10") int historyLimit) {

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView view =
                supportDetailsFacade.getDetails(tenantId, workItemCode, historyLimit);

        return ResponseEntity.ok(toSupportDetailsResponse(view));
    }

    /**
     * Bitta work item uchun combined support details qaytaradi (UUID bo'yicha).
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @param historyLimit so'nggi delivery attempt'lar soni (1..50, default 10)
     * @return combined support details
     */
    @GetMapping("/support-details/by-id")
    public ResponseEntity<WorkItemSupportDetailsResponse> getSupportDetailsById(
            @RequestParam UUID tenantId,
            @RequestParam UUID workItemId,
            @RequestParam(defaultValue = "10") int historyLimit) {

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView view =
                supportDetailsByIdFacade.getDetails(tenantId, workItemId, historyLimit);

        return ResponseEntity.ok(toSupportDetailsResponse(view));
    }

    /**
     * Bitta work item uchun details va update history qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemCode work item kodi (masalan "BUG-1")
     * @return work item details + ordered update history
     */
    @GetMapping("/details")
    public ResponseEntity<WorkItemDetailsResponse> getDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode) {

        WorkItemDetailsFacade.WorkItemDetailsView view =
                detailsFacade.getDetails(tenantId, workItemCode);

        return ResponseEntity.ok(toResponse(view));
    }

    /**
     * Bitta work item uchun details va update history qaytaradi (UUID bo'yicha).
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item UUID identifikatori
     * @return work item details + ordered update history
     */
    @GetMapping("/details/by-id")
    public ResponseEntity<WorkItemDetailsResponse> getDetailsById(
            @RequestParam UUID tenantId,
            @RequestParam UUID workItemId) {

        WorkItemDetailsFacade.WorkItemDetailsView view =
                detailsByIdFacade.getDetails(tenantId, workItemId);

        return ResponseEntity.ok(toResponse(view));
    }

    private WorkItemDetailsResponse toResponse(WorkItemDetailsFacade.WorkItemDetailsView view) {
        WorkItem wi = view.workItem();
        return new WorkItemDetailsResponse(
                wi.getId(),
                wi.getWorkItemCode(),
                wi.getTitle(),
                wi.getTypeCode().name(),
                wi.getCurrentStatusCode(),
                wi.getPriorityCode(),
                wi.getSeverityCode(),
                wi.getEnvironmentCode(),
                wi.getSourceService(),
                wi.getCorrelationKey(),
                wi.getCurrentOwnerUserId(),
                wi.getOpenedAt(),
                wi.getLastTransitionAt(),
                wi.getResolvedAt(),
                wi.getReopenedCount(),
                wi.isArchived(),
                view.updates().stream()
                        .map(this::toUpdateItemResponse)
                        .toList());
    }

    private WorkItemDetailsResponse.UpdateItemResponse toUpdateItemResponse(WorkItemUpdate update) {
        return new WorkItemDetailsResponse.UpdateItemResponse(
                update.getId(),
                update.getTenantId(),
                update.getWorkItemId(),
                update.getAuthorUserId(),
                update.getUpdateTypeCode().name(),
                update.getBody(),
                update.getVisibilityCode().name(),
                update.getCreatedAt());
    }

    // ========== Summary mapping ==========

    private WorkItemSummaryResponse.SummaryItemResponse toSummaryItemResponse(
            WorkItemSummaryItem item) {
        return new WorkItemSummaryResponse.SummaryItemResponse(
                item.workItemId(),
                item.workItemCode(),
                item.title(),
                item.typeCode().name(),
                item.currentStatusCode(),
                item.priorityCode(),
                item.severityCode(),
                item.currentOwnerUserId(),
                item.openedAt(),
                item.lastTransitionAt(),
                item.resolvedAt(),
                item.reopenedCount(),
                item.archived());
    }

    // ========== Support details mapping ==========

    private WorkItemSupportDetailsResponse toSupportDetailsResponse(
            WorkItemSupportDetailsFacade.WorkItemSupportDetailsView view) {
        return new WorkItemSupportDetailsResponse(
                toResponse(view.workItemDetails()),
                toObservabilityResponse(view.observabilityDetails()));
    }

    private DeliveryObservabilityDetailsResponse toObservabilityResponse(
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

    // ========== Support summary mapping ==========

    private WorkItemSupportSummaryResponse.SupportSummaryItemResponse toSupportSummaryItemResponse(
            WorkItemSupportSummaryItem item) {
        return new WorkItemSupportSummaryResponse.SupportSummaryItemResponse(
                toWorkItemSectionResponse(item.workItem()),
                toDeliveryObservabilitySectionResponse(item.deliveryObservability()));
    }

    private WorkItemSupportSummaryResponse.WorkItemSectionResponse toWorkItemSectionResponse(
            WorkItemSummaryItem item) {
        return new WorkItemSupportSummaryResponse.WorkItemSectionResponse(
                item.workItemId(),
                item.workItemCode(),
                item.title(),
                item.typeCode().name(),
                item.currentStatusCode(),
                item.priorityCode(),
                item.severityCode(),
                item.currentOwnerUserId(),
                item.openedAt(),
                item.lastTransitionAt(),
                item.resolvedAt(),
                item.reopenedCount(),
                item.archived());
    }

    private WorkItemSupportSummaryResponse.DeliveryObservabilitySectionResponse toDeliveryObservabilitySectionResponse(
            DeliveryObservabilitySummaryItem item) {
        return new WorkItemSupportSummaryResponse.DeliveryObservabilitySectionResponse(
                item.workItemId(),
                item.workItemCode(),
                item.title(),
                item.typeCode().name(),
                item.currentStatusCode(),
                toSummaryMetricsResponse(item.latestMetrics()));
    }

    private WorkItemSupportSummaryResponse.MetricsSummaryResponse toSummaryMetricsResponse(
            TelegramDeliveryMetricsSnapshot snapshot) {
        return new WorkItemSupportSummaryResponse.MetricsSummaryResponse(
                snapshot.getDeliveryOutcome() != null ? snapshot.getDeliveryOutcome().name() : null,
                snapshot.isSuccess(),
                snapshot.isRejected(),
                snapshot.isFailed(),
                snapshot.getFailureCode(),
                snapshot.hasExternalMessageId(),
                snapshot.isEmpty());
    }
}
