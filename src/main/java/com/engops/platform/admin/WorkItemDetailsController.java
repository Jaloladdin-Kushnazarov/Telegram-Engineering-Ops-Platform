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
 * WorkItem details uchun read-only admin endpoint.
 *
 * Bitta endpoint:
 * - GET /details — tenant-scoped work item details + update history
 *
 * Faqat GET — write operatsiya yo'q.
 *
 * Bu controller thin adapter:
 * - HTTP request parametrlarini facade'ga uzatadi
 * - Facade natijasini response DTO'ga map qiladi
 * - ResourceNotFoundException (404) va IllegalArgumentException (400)
 *   GlobalExceptionHandler tomonidan qayta ishlanadi
 */
@RestController
@RequestMapping("/api/admin/work-items")
public class WorkItemDetailsController {

    private final WorkItemDetailsFacade detailsFacade;

    public WorkItemDetailsController(WorkItemDetailsFacade detailsFacade) {
        this.detailsFacade = detailsFacade;
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
}
