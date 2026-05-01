package com.engops.platform.intake;

import com.engops.platform.workitem.model.WorkItemType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * <strong>Xavfsizlik konteksti — Phase 121:</strong>
 * Bu endpoint hozircha INTERNAL/TRUSTED intake boundary sifatida ishlaydi —
 * application-level autentifikatsiya/avtorizatsiya YO'Q (TENANT_CONFIG_WRITE
 * tekshirilmaydi, Spring Security/JWT/API token mavjud emas). Production
 * deployment'da bu endpoint aniq deployment / tarmoq / API gateway nazoratlari
 * bilan himoyalanishi shart (masalan: ichki tarmoq, mTLS, API gateway). Mustaqil
 * intake autentifikatsiya phase'i implement qilinmaguncha bu yo'l ochiq qoladi.
 * Internet'ga ochiq endpoint EMAS.
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
     * @param request intake so'rovi (required body)
     * @return yaratilgan work item + resolved routing target (201 Created)
     */
    @PostMapping("/work-items")
    public ResponseEntity<WorkItemIntakeResponse> submit(
            @RequestBody(required = false) WorkItemIntakeRequest request) {
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
                .createdByUserId(request.createdByUserId())
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
