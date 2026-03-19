package com.engops.platform.admin;

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
 * - GET /details — tenant-scoped work item details + update history
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

    public WorkItemDetailsController(WorkItemDetailsFacade detailsFacade,
                                     WorkItemSummaryFacade summaryFacade) {
        this.detailsFacade = detailsFacade;
        this.summaryFacade = summaryFacade;
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
}
