package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.CurrentActor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 199 — POST /api/admin/tenants endpoint'i: tenant onboarding REST surface.
 *
 * <p><strong>Sirt:</strong> {@code POST /api/admin/tenants}. Request body —
 * {@link TenantOnboardingRequest}. Muvaffaqiyat — 201 Created bilan
 * {@link TenantOnboardingResponse}, va {@code Location} header'i kelajakdagi
 * GET /api/admin/tenants/{id} resursiga ishora qiladi.</p>
 *
 * <p><strong>Xavfsizlik:</strong> {@code @CurrentActor UUID actorUserId}
 * SecurityContext'dan resolve qilinadi (request body'dagi har qanday
 * actor maydoniga e'tibor berilmaydi). Permission tekshiruvi service
 * layer'da {@link TenantOnboardingService#onboard} ichida — yangi
 * {@code TENANT_ONBOARD} ruxsati global tarzda (har qanday tenantida)
 * tekshiriladi (yangi tenantning a'zosi bo'lish chicken-and-egg muammosini
 * yechadi).</p>
 *
 * <p><strong>Exception mapping (GlobalExceptionHandler):</strong>
 * {@code AccessDeniedException} → 403; {@code BusinessRuleException} → 422
 * (errorCode standart envelope'da uzatiladi: SLUG_TAKEN,
 * UNKNOWN_WORKFLOW_TEMPLATE, NO_TEMPLATES_REQUESTED, INVALID_SLUG,
 * INVALID_TENANT_NAME va h.k.); {@code IllegalArgumentException} → 400.</p>
 *
 * <p><strong>Out of scope:</strong> tenant rename / delete, second admin
 * binding, Telegram routing config, custom workflow shabloni yaratish.</p>
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantOnboardingController {

    private final TenantOnboardingService tenantOnboardingService;

    public TenantOnboardingController(TenantOnboardingService tenantOnboardingService) {
        this.tenantOnboardingService = tenantOnboardingService;
    }

    @PostMapping
    public ResponseEntity<TenantOnboardingResponse> onboard(
            @RequestBody(required = false) TenantOnboardingRequest request,
            @CurrentActor UUID actorUserId) {

        TenantOnboardingRequest body = requireBody(request);

        TenantOnboardingCommand command = new TenantOnboardingCommand(
                body.tenantName(),
                body.tenantSlug(),
                body.tenantTimezone(),
                body.adminTelegramUserId(),
                body.adminDisplayName(),
                body.adminUsername(),
                body.workflowTemplateCodes(),
                actorUserId);

        TenantOnboardingResult result = tenantOnboardingService.onboard(command);

        TenantOnboardingResponse response = toResponse(result);
        URI location = URI.create("/api/admin/tenants/" + result.tenantId());
        return ResponseEntity.created(location).body(response);
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new IllegalArgumentException("Request body null bo'lishi mumkin emas");
        }
        return body;
    }

    private static TenantOnboardingResponse toResponse(TenantOnboardingResult result) {
        return new TenantOnboardingResponse(
                result.tenantId(),
                result.tenantSlug(),
                result.tenantName(),
                result.createdAt(),
                result.adminAppUserId(),
                result.adminMembershipId(),
                result.workflowDefinitions().stream()
                        .map(d -> new TenantOnboardingResponse.WorkflowDefinitionSummary(
                                d.workflowDefinitionId(), d.templateCode(), d.workItemType()))
                        .collect(Collectors.toList()));
    }
}
