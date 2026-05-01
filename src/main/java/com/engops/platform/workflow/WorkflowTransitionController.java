package com.engops.platform.workflow;

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
 * <strong>Xavfsizlik konteksti — Phase 122:</strong>
 * Bu endpoint hozircha INTERNAL/TRUSTED operational boundary sifatida ishlaydi —
 * application-level autentifikatsiya/avtorizatsiya YO'Q (TENANT_CONFIG_WRITE
 * tekshirilmaydi, WORK_ITEM_TRANSITION/UPDATE permission'lar tekshirilmaydi,
 * Spring Security/JWT/API token mavjud emas). Request body'dagi {@code actorUserId}
 * faqat audit/attribution input — ishonchli authentication EMAS. Production
 * deployment'da bu endpoint deployment / tarmoq / API gateway nazoratlari
 * bilan himoyalanishi shart. Mustaqil operational autentifikatsiya phase'i
 * implement qilinmaguncha bu yo'l ochiq qoladi. Internet'ga ochiq endpoint EMAS.
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
     * @param workItemId work item identifikatori (path)
     * @param request transition so'rovi (required body)
     * @return yangilangan work item state (200 OK)
     */
    @PostMapping("/{workItemId}/transitions")
    public ResponseEntity<WorkflowTransitionResponse> transition(
            @PathVariable UUID workItemId,
            @RequestBody(required = false) WorkflowTransitionRequest request) {
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
        if (request.actorUserId() == null) {
            throw new IllegalArgumentException("actorUserId null bo'lishi mumkin emas");
        }
        if (request.actionSource() == null || request.actionSource().isBlank()) {
            throw new IllegalArgumentException("actionSource null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkItem workItem = workflowTransitionService.transition(
                request.tenantId(),
                workItemId,
                request.targetStatusCode(),
                request.actorUserId(),
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
