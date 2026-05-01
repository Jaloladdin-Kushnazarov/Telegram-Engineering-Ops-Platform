package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.CurrentActor;
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
 * - GET /support-details/by-status — status bo'yicha combined support details ro'yxat
 * - GET /support-details/by-owner — owner bo'yicha combined support details ro'yxat
 *
 * Faqat GET — write operatsiya yo'q.
 * Barcha endpoint'lar joriy actor'ni Spring SecurityContext'dan {@link CurrentActor}
 * argument resolver orqali oladi (Phase 129). Avvalgi {@code X-Actor-User-Id}
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
@RequestMapping("/api/admin/work-items")
public class WorkItemDetailsController {

    private final WorkItemDetailsReadFacade detailsReadFacade;
    private final WorkItemSummaryReadFacade summaryReadFacade;
    private final WorkItemSupportDetailsReadFacade supportDetailsReadFacade;
    private final WorkItemSupportSummaryFacade supportSummaryFacade;
    private final WorkItemSupportDetailsByIdFacade supportDetailsByIdFacade;
    private final WorkItemDetailsByIdFacade detailsByIdFacade;
    private final WorkItemSummaryByStatusReadFacade summaryByStatusReadFacade;
    private final WorkItemSummaryByOwnerReadFacade summaryByOwnerReadFacade;
    private final WorkItemSupportSummaryByStatusFacade supportSummaryByStatusFacade;
    private final WorkItemSupportSummaryByOwnerFacade supportSummaryByOwnerFacade;
    private final WorkItemSupportDetailsByStatusFacade supportDetailsByStatusFacade;
    private final WorkItemSupportDetailsByOwnerFacade supportDetailsByOwnerFacade;

    public WorkItemDetailsController(WorkItemDetailsReadFacade detailsReadFacade,
                                     WorkItemSummaryReadFacade summaryReadFacade,
                                     WorkItemSupportDetailsReadFacade supportDetailsReadFacade,
                                     WorkItemSupportSummaryFacade supportSummaryFacade,
                                     WorkItemSupportDetailsByIdFacade supportDetailsByIdFacade,
                                     WorkItemDetailsByIdFacade detailsByIdFacade,
                                     WorkItemSummaryByStatusReadFacade summaryByStatusReadFacade,
                                     WorkItemSummaryByOwnerReadFacade summaryByOwnerReadFacade,
                                     WorkItemSupportSummaryByStatusFacade supportSummaryByStatusFacade,
                                     WorkItemSupportSummaryByOwnerFacade supportSummaryByOwnerFacade,
                                     WorkItemSupportDetailsByStatusFacade supportDetailsByStatusFacade,
                                     WorkItemSupportDetailsByOwnerFacade supportDetailsByOwnerFacade) {
        this.detailsReadFacade = detailsReadFacade;
        this.summaryReadFacade = summaryReadFacade;
        this.supportDetailsReadFacade = supportDetailsReadFacade;
        this.supportSummaryFacade = supportSummaryFacade;
        this.supportDetailsByIdFacade = supportDetailsByIdFacade;
        this.detailsByIdFacade = detailsByIdFacade;
        this.summaryByStatusReadFacade = summaryByStatusReadFacade;
        this.summaryByOwnerReadFacade = summaryByOwnerReadFacade;
        this.supportSummaryByStatusFacade = supportSummaryByStatusFacade;
        this.supportSummaryByOwnerFacade = supportSummaryByOwnerFacade;
        this.supportDetailsByStatusFacade = supportDetailsByStatusFacade;
        this.supportDetailsByOwnerFacade = supportDetailsByOwnerFacade;
    }

    @GetMapping("/summary")
    public ResponseEntity<WorkItemSummaryResponse> getSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryReadFacade.getSummaryList(tenantId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    @GetMapping("/by-status")
    public ResponseEntity<WorkItemSummaryResponse> getByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryByStatusReadFacade.getSummaryList(tenantId, statusCode, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    @GetMapping("/by-owner")
    public ResponseEntity<WorkItemSummaryResponse> getByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = summaryByOwnerReadFacade.getSummaryList(tenantId, ownerUserId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSummaryResponse(responseItems));
    }

    @GetMapping("/support-summary")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummary(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = supportSummaryFacade.getSummaryList(tenantId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    @GetMapping("/support-summary/by-status")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummaryByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = supportSummaryByStatusFacade.getSummaryList(tenantId, statusCode, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    @GetMapping("/support-summary/by-owner")
    public ResponseEntity<WorkItemSupportSummaryResponse> getSupportSummaryByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var items = supportSummaryByOwnerFacade.getSummaryList(tenantId, ownerUserId, limit, actorUserId);

        var responseItems = items.stream()
                .map(this::toSupportSummaryItemResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportSummaryResponse(responseItems));
    }

    @GetMapping("/support-details")
    public ResponseEntity<WorkItemSupportDetailsResponse> getSupportDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode,
            @RequestParam(defaultValue = "10") int historyLimit,
            @CurrentActor UUID actorUserId) {

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView view =
                supportDetailsReadFacade.getDetails(tenantId, workItemCode, historyLimit, actorUserId);

        return ResponseEntity.ok(toSupportDetailsResponse(view));
    }

    @GetMapping("/support-details/by-status")
    public ResponseEntity<WorkItemSupportDetailsByStatusResponse> getSupportDetailsByStatus(
            @RequestParam UUID tenantId,
            @RequestParam String statusCode,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var views = supportDetailsByStatusFacade.getDetailsList(tenantId, statusCode, limit, actorUserId);

        var responseItems = views.stream()
                .map(this::toSupportDetailsResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportDetailsByStatusResponse(responseItems));
    }

    @GetMapping("/support-details/by-owner")
    public ResponseEntity<WorkItemSupportDetailsByOwnerResponse> getSupportDetailsByOwner(
            @RequestParam UUID tenantId,
            @RequestParam UUID ownerUserId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentActor UUID actorUserId) {

        var views = supportDetailsByOwnerFacade.getDetailsList(tenantId, ownerUserId, limit, actorUserId);

        var responseItems = views.stream()
                .map(this::toSupportDetailsResponse)
                .toList();

        return ResponseEntity.ok(new WorkItemSupportDetailsByOwnerResponse(responseItems));
    }

    @GetMapping("/support-details/by-id")
    public ResponseEntity<WorkItemSupportDetailsResponse> getSupportDetailsById(
            @RequestParam UUID tenantId,
            @RequestParam UUID workItemId,
            @RequestParam(defaultValue = "10") int historyLimit,
            @CurrentActor UUID actorUserId) {

        WorkItemSupportDetailsFacade.WorkItemSupportDetailsView view =
                supportDetailsByIdFacade.getDetails(tenantId, workItemId, historyLimit, actorUserId);

        return ResponseEntity.ok(toSupportDetailsResponse(view));
    }

    @GetMapping("/details")
    public ResponseEntity<WorkItemDetailsResponse> getDetails(
            @RequestParam UUID tenantId,
            @RequestParam String workItemCode,
            @CurrentActor UUID actorUserId) {

        WorkItemDetailsFacade.WorkItemDetailsView view =
                detailsReadFacade.getDetails(tenantId, workItemCode, actorUserId);

        return ResponseEntity.ok(toResponse(view));
    }

    @GetMapping("/details/by-id")
    public ResponseEntity<WorkItemDetailsResponse> getDetailsById(
            @RequestParam UUID tenantId,
            @RequestParam UUID workItemId,
            @CurrentActor UUID actorUserId) {

        WorkItemDetailsFacade.WorkItemDetailsView view =
                detailsByIdFacade.getDetails(tenantId, workItemId, actorUserId);

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
