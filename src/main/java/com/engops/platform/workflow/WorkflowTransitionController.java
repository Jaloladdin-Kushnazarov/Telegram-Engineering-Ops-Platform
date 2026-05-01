package com.engops.platform.workflow;

import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Workflow transition REST adapter — mavjud
 * {@link WorkflowTransitionService#transition(UUID, UUID, String, UUID, String, String)}
 * use-case'ini HTTP orqali ochadi.
 *
 * Bu controller thin adapter:
 * - HTTP path/body parametrlarini service chaqiruviga aylantiradi
 * - WorkflowTransitionService.transition(...) ni chaqiradi
 * - Qaytgan {@link WorkItem}'ni response DTO'ga map qiladi
 *
 * Biznes/state-machine qoidalari (SAME_STATUS, INVALID_TRANSITION, terminal,
 * reopen, workflow definition lookup) servisda saqlanadi — controller'da
 * dublikat validatsiya qo'shilmaydi. Mavjud {@code BusinessRuleException} va
 * {@code ResourceNotFoundException} GlobalExceptionHandler orqali 422/404
 * ga aylantiriladi.
 *
 * <strong>Xavfsizlik konteksti — Phase 135:</strong>
 * Transition'ni bajaruvchi actor identifikatori endi {@link CurrentActor}
 * resolver orqali SecurityContext'dagi {@code AuthenticatedActor}'dan olinadi.
 * Request body'dagi eski {@code actorUserId} maydoni wire compatibility uchun
 * qabul qilinadi lekin jim e'tiborsiz qoldiriladi — spoofing yo'lga qo'yilmaydi.
 * Authenticated actor mavjud bo'lmasa resolver controller body'gacha yetib
 * bormay 403 ACCESS_DENIED qaytaradi (Phase 128/129/131/132/134 pattern'i bilan
 * bir xil). Permission tekshiruvi (WORK_ITEM_TRANSITION va h.k.) bu phase'da
 * qo'shilmaydi — keyingi alohida phase'da hal qilinadi. SecurityConfig hali ham
 * {@code permitAll()} holatida; production deployment'da deployment / tarmoq /
 * API gateway nazoratlari bilan himoyalanishi shart.
 */
@RestController
@RequestMapping("/api/work-items")
public class WorkflowTransitionController {

    private final WorkflowTransitionService workflowTransitionService;

    public WorkflowTransitionController(WorkflowTransitionService workflowTransitionService) {
        this.workflowTransitionService = workflowTransitionService;
    }

    /**
     * Mavjud work item'ni yangi status'ga o'tkazadi.
     *
     * <p>Actor identifikatori {@link CurrentActor}'dan olinadi —
     * request body'dagi {@code actorUserId} (agar yuborilsa) e'tiborga
     * olinmaydi.</p>
     *
     * @param workItemId work item identifikatori (path)
     * @param request transition so'rovi (required body)
     * @param actorUserId authenticated actor (resolver SecurityContext'dan oladi;
     *                    yo'q bo'lsa 403 ACCESS_DENIED service chaqirilishidan oldin)
     * @return yangilangan work item state (200 OK)
     */
    @PostMapping("/{workItemId}/transitions")
    public ResponseEntity<WorkflowTransitionResponse> transition(
            @PathVariable UUID workItemId,
            @RequestBody(required = false) WorkflowTransitionRequest request,
            @CurrentActor UUID actorUserId) {
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        // REST boundary required-field validation only — biznes/state-machine
        // qoidalari (SAME_STATUS, INVALID_TRANSITION, workflow lookup, tenant
        // safety) servisda saqlanadi. reason ixtiyoriy bo'lib qoladi.
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request.targetStatusCode() == null || request.targetStatusCode().isBlank()) {
            throw new IllegalArgumentException("targetStatusCode null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.actionSource() == null || request.actionSource().isBlank()) {
            throw new IllegalArgumentException("actionSource null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkItem workItem = workflowTransitionService.transition(
                request.tenantId(),
                workItemId,
                request.targetStatusCode(),
                actorUserId,
                request.actionSource(),
                request.reason());

        return ResponseEntity.ok(toResponse(workItem));
    }

    private static WorkflowTransitionResponse toResponse(WorkItem workItem) {
        return new WorkflowTransitionResponse(
                workItem.getTenantId(),
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getTypeCode() != null ? workItem.getTypeCode().name() : null,
                workItem.getTitle(),
                workItem.getCurrentStatusCode(),
                workItem.getWorkflowDefinitionId(),
                workItem.getCurrentOwnerUserId(),
                workItem.getLastTransitionAt(),
                workItem.getResolvedAt(),
                workItem.getReopenedCount(),
                workItem.getUpdatedAt());
    }
}
