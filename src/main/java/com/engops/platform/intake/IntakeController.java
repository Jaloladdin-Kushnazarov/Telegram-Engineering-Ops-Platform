package com.engops.platform.intake;

import com.engops.platform.infrastructure.security.CurrentActor;
import com.engops.platform.workitem.model.WorkItemType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Intake REST adapter — mavjud {@link IntakeApplicationService} use-case'ini
 * HTTP orqali ochadi.
 *
 * Bu controller thin adapter:
 * - HTTP request'ni IntakeCommand ga aylantiradi
 * - IntakeApplicationService.submit(...) ni chaqiradi
 * - IntakeResult'ni response DTO'ga map qiladi
 *
 * Biznes validatsiyasi qo'shilmaydi — IntakeApplicationService.validateCommand
 * mavjud kontraktni hurmat qiladi (BusinessRuleException("INTAKE_VALIDATION", ...)
 * → 422 GlobalExceptionHandler orqali; ResourceNotFoundException → 404; va h.k.).
 *
 * <strong>Xavfsizlik konteksti — Phase 134:</strong>
 * Yaratuvchi/actor identifikatori endi {@link CurrentActor} resolver orqali
 * SecurityContext'dagi {@code AuthenticatedActor}'dan olinadi. Request body'dagi
 * eski {@code createdByUserId} maydoni wire compatibility uchun qabul qilinadi
 * lekin jim e'tiborsiz qoldiriladi — spoofing yo'lga qo'yilmaydi. Authenticated
 * actor mavjud bo'lmasa resolver controller body'gacha yetib bormay 403
 * ACCESS_DENIED qaytaradi (Phase 128/129/131/132 pattern'i bilan bir xil).
 * Permission tekshiruvi (TENANT_CONFIG_WRITE va h.k.) bu phase'da qo'shilmaydi —
 * keyingi alohida phase'da hal qilinadi. SecurityConfig hali ham
 * {@code permitAll()} holatida; production deployment'da deployment / tarmoq /
 * API gateway nazoratlari bilan himoyalanishi shart.
 */
@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final IntakeApplicationService intakeApplicationService;

    public IntakeController(IntakeApplicationService intakeApplicationService) {
        this.intakeApplicationService = intakeApplicationService;
    }

    /**
     * Yangi work item yaratadi mavjud intake application service orqali.
     *
     * <p>Yaratuvchi identifikatori {@link CurrentActor}'dan olinadi —
     * request body'dagi {@code createdByUserId} (agar yuborilsa) e'tiborga
     * olinmaydi.</p>
     *
     * @param request intake so'rovi (required body)
     * @param actorUserId authenticated actor (resolver SecurityContext'dan oladi;
     *                    yo'q bo'lsa 403 ACCESS_DENIED service chaqirilishidan oldin)
     * @return yaratilgan work item + resolved routing target (201 Created)
     */
    @PostMapping("/work-items")
    public ResponseEntity<WorkItemIntakeResponse> submit(
            @RequestBody(required = false) WorkItemIntakeRequest request,
            @CurrentActor UUID actorUserId) {
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(request.tenantId())
                .typeCode(parseTypeCode(request.typeCode()))
                .title(request.title())
                .description(request.description())
                .workflowDefinitionId(request.workflowDefinitionId())
                .initialStatusCode(request.initialStatusCode())
                .createdByUserId(actorUserId)
                .actionSource(request.actionSource())
                .build();

        IntakeResult result = intakeApplicationService.submit(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    /**
     * String typeCode'ni WorkItemType enum'ga konvertatsiya qiladi. Null
     * holatida null qaytaradi — IntakeApplicationService.validateCommand
     * keyin INTAKE_VALIDATION 422 qaytaradi. Noto'g'ri qiymat IllegalArgumentException
     * (400) sifatida tarjima qilinadi.
     */
    private static WorkItemType parseTypeCode(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        try {
            return WorkItemType.valueOf(typeCode);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "typeCode noto'g'ri: '" + typeCode + "' (BUG, INCIDENT, TASK)");
        }
    }

    private static WorkItemIntakeResponse toResponse(IntakeResult result) {
        return new WorkItemIntakeResponse(
                result.getTenantId(),
                result.getWorkItemId(),
                result.getWorkItemCode(),
                result.getWorkItemType(),
                result.getTitle(),
                result.getCurrentStatusCode(),
                result.getWorkflowDefinitionId(),
                result.isRoutingPrepared(),
                result.getMatchedRoutingRuleId(),
                result.getTargetTopicBindingId(),
                result.getTargetChatBindingId(),
                result.getTargetTopicId());
    }
}
