package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 190 — WorkItem admin write surface (thin REST adapter).
 *
 * <p>Mavjud {@link WorkItemDetailsController} faqat read endpoint'larni
 * (GET) saqlaydi. Bu controller WorkItem domain field'lariga (priority,
 * severity, owner) production-grade write surface qo'shadi. Write
 * surface intake transition lane'lari bilan parallel — alohida endpoint
 * gruppa.</p>
 *
 * <p><strong>Endpoint'lar:</strong></p>
 * <ul>
 *   <li>{@code POST /api/admin/work-items/{workItemId}/owner} —
 *       {@link AssignWorkItemOwnerRequest}; authorization {@code WORK_ITEM_ASSIGN}.</li>
 *   <li>{@code POST /api/admin/work-items/{workItemId}/priority} —
 *       {@link UpdateWorkItemPriorityRequest}; authorization
 *       {@code WORK_ITEM_UPDATE}; ruxsat etilgan qiymatlar: LOW, MEDIUM, HIGH, CRITICAL.</li>
 *   <li>{@code POST /api/admin/work-items/{workItemId}/severity} —
 *       {@link UpdateWorkItemSeverityRequest}; authorization
 *       {@code WORK_ITEM_UPDATE}; ruxsat etilgan qiymatlar: LOW, MEDIUM, HIGH, CRITICAL.</li>
 * </ul>
 *
 * <p><strong>Xavfsizlik konteksti (Phase 134/139 pattern bilan bir xil):</strong>
 * actor identifikatori {@link CurrentActor} resolver orqali SecurityContext'dan
 * olinadi. Request body'dagi har qanday actor maydoniga (yo'q) e'tibor
 * berilmaydi. Authentication mavjud bo'lmasa resolver controller body'gacha
 * yetib bormay 403 ACCESS_DENIED qaytaradi. Permission tekshiruvi
 * {@link OperationalAuthorizationService} orqali service chaqirilishidan oldin
 * bajariladi (fail-closed). Admin tenant-config write'idan farqli ravishda,
 * bu operatsiyalar {@code WORK_ITEM_*} operational permission'lariga tegishli —
 * shu sababli {@link AdminAuthorizationService} ishlatilmaydi.</p>
 *
 * <p><strong>Audit kontrakti:</strong> har bir muvaffaqiyatli mutatsiya
 * mavjud {@link WorkItemCommandService} servisi tomonidan
 * {@code MANDATORY} propagation orqali joriy biznes tranzaksiyasi ichida
 * audit qatori yozadi ({@code OWNER_ASSIGNED}, {@code PRIORITY_CHANGED},
 * {@code SEVERITY_CHANGED}). {@code action_source} har doim
 * {@code ADMIN_API}. Request body, JWT, IP, exception message audit
 * payload'ga kirmaydi.</p>
 *
 * <p><strong>Exception mapping:</strong> GlobalExceptionHandler tomonidan
 * standart envelope'larga konvertatsiya qilinadi —
 * {@code AccessDeniedException} → 403 ACCESS_DENIED;
 * {@code ResourceNotFoundException} → 404 RESOURCE_NOT_FOUND;
 * {@code BusinessRuleException} → 422 (joriy errorCode bilan);
 * {@code IllegalArgumentException} → 400 BAD_REQUEST.</p>
 *
 * <p><strong>Out of scope (Phase 190):</strong> intake request kengayishi,
 * Telegram card rendering, INCIDENT/TASK bootstrap auto-seed, schema migration,
 * SecurityConfig o'zgarishi, yangi Micrometer counter — bularning hech biri
 * shu phase ichida emas.</p>
 */
@RestController
@RequestMapping("/api/admin/work-items")
public class WorkItemAdminWriteController {

    /** Audit va {@code WorkItemUpdate} qatorlari uchun action manbai. */
    static final String ACTION_SOURCE = "ADMIN_API";

    private final WorkItemCommandService workItemCommandService;
    private final OperationalAuthorizationService operationalAuthorizationService;

    public WorkItemAdminWriteController(WorkItemCommandService workItemCommandService,
                                         OperationalAuthorizationService operationalAuthorizationService) {
        this.workItemCommandService = workItemCommandService;
        this.operationalAuthorizationService = operationalAuthorizationService;
    }

    /**
     * Work item'ga owner tayinlash.
     *
     * @param workItemId work item identifikatori (URL path)
     * @param request {@link AssignWorkItemOwnerRequest} (tenantId, ownerUserId)
     * @param actorUserId joriy actor (SecurityContext'dan)
     * @return yangilangan work item suratining barqaror DTO'si
     */
    @PostMapping("/{workItemId}/owner")
    public ResponseEntity<WorkItemAdminWriteResponse> assignOwner(
            @PathVariable("workItemId") UUID workItemId,
            @RequestBody(required = false) AssignWorkItemOwnerRequest request,
            @CurrentActor UUID actorUserId) {

        AssignWorkItemOwnerRequest body = requireBody(request);
        requireUuid("tenantId", body.tenantId());
        requireUuid("ownerUserId", body.ownerUserId());

        operationalAuthorizationService.authorizeAssignOwner(body.tenantId(), actorUserId);

        WorkItem updated = workItemCommandService.assignOwner(
                body.tenantId(), workItemId, body.ownerUserId(), actorUserId, ACTION_SOURCE);

        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Work item priority kodini yangilash.
     *
     * @param workItemId work item identifikatori (URL path)
     * @param request {@link UpdateWorkItemPriorityRequest} (tenantId, priorityCode)
     * @param actorUserId joriy actor (SecurityContext'dan)
     * @return yangilangan work item suratining barqaror DTO'si
     */
    @PostMapping("/{workItemId}/priority")
    public ResponseEntity<WorkItemAdminWriteResponse> updatePriority(
            @PathVariable("workItemId") UUID workItemId,
            @RequestBody(required = false) UpdateWorkItemPriorityRequest request,
            @CurrentActor UUID actorUserId) {

        UpdateWorkItemPriorityRequest body = requireBody(request);
        requireUuid("tenantId", body.tenantId());

        operationalAuthorizationService.authorizeUpdate(body.tenantId(), actorUserId);

        WorkItem updated = workItemCommandService.updatePriority(
                body.tenantId(), workItemId, body.priorityCode(), actorUserId, ACTION_SOURCE);

        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Work item severity kodini yangilash.
     *
     * @param workItemId work item identifikatori (URL path)
     * @param request {@link UpdateWorkItemSeverityRequest} (tenantId, severityCode)
     * @param actorUserId joriy actor (SecurityContext'dan)
     * @return yangilangan work item suratining barqaror DTO'si
     */
    @PostMapping("/{workItemId}/severity")
    public ResponseEntity<WorkItemAdminWriteResponse> updateSeverity(
            @PathVariable("workItemId") UUID workItemId,
            @RequestBody(required = false) UpdateWorkItemSeverityRequest request,
            @CurrentActor UUID actorUserId) {

        UpdateWorkItemSeverityRequest body = requireBody(request);
        requireUuid("tenantId", body.tenantId());

        operationalAuthorizationService.authorizeUpdate(body.tenantId(), actorUserId);

        WorkItem updated = workItemCommandService.updateSeverity(
                body.tenantId(), workItemId, body.severityCode(), actorUserId, ACTION_SOURCE);

        return ResponseEntity.ok(toResponse(updated));
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new IllegalArgumentException("Request body null bo'lishi mumkin emas");
        }
        return body;
    }

    private static void requireUuid(String fieldName, UUID value) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " majburiy");
        }
    }

    private static WorkItemAdminWriteResponse toResponse(WorkItem workItem) {
        return new WorkItemAdminWriteResponse(
                workItem.getTenantId(),
                workItem.getId(),
                workItem.getWorkItemCode(),
                workItem.getCurrentStatusCode(),
                workItem.getCurrentOwnerUserId(),
                workItem.getPriorityCode(),
                workItem.getSeverityCode(),
                workItem.getUpdatedAt());
    }
}
