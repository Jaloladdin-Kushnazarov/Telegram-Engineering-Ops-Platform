package com.engops.platform.admin;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.Role;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.WorkflowTemplateQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import com.engops.platform.workitem.OperationalAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 199 — Tenant onboarding orchestrator service.
 *
 * <p>POST /api/admin/tenants endpoint'i ortida atomik onboarding amalini
 * boshqaradi: tenant yaratish, AppUser find-or-create, admin Membership +
 * ADMIN role binding, va har bir so'ralgan workflow_template uchun
 * tenant ichida workflow_definition + status'lar + transition rule'larni
 * Phase 198 catalog'idan seed qilish.</p>
 *
 * <p><strong>Atomiklik:</strong> butun jarayon yagona {@code @Transactional}
 * boundary ichida bajariladi. Har qanday xatolikda hech narsa commit
 * qilinmaydi (slug-taken, noma'lum template, validation buzilishi,
 * authorization rad etilishi — barchasi rollback).</p>
 *
 * <p><strong>Audit:</strong> uchta darajadagi audit trail yoziladi:
 * (a) past darajadagi qatorlar mavjud command service'lardan
 * ({@code TENANT/CREATED}, {@code MEMBERSHIP/CREATED},
 * {@code MEMBERSHIP_ROLE_BINDING/CREATED}, {@code WORKFLOW_DEFINITION/CREATED},
 * {@code WORKFLOW_STATUS/CREATED}, {@code WORKFLOW_TRANSITION_RULE/CREATED}),
 * va (b) onboarding darajadagi yuqori-yorliqli qatorlar shu service'dan
 * ({@code TENANT/TENANT_CREATED}, {@code MEMBERSHIP/ADMIN_MEMBERSHIP_CREATED},
 * {@code WORKFLOW_DEFINITION/WORKFLOW_SEEDED}). Yuqori-yorliqli qatorlar
 * faqat onboarding flow uchun emit qilinadi va aktiv {@code actorUserId}
 * bilan birga keladi.</p>
 *
 * <p><strong>Telegram dispatch:</strong> bu phase'da onboarding hech qanday
 * Telegram event emit qilmaydi. Yangi tenant uchun chat/topic/routing
 * konfiguratsiyasini operator alohida endpoint'lar orqali sozlaydi.</p>
 */
@Service
@Transactional
public class TenantOnboardingService {

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    static final String TENANT_ONBOARD_PERMISSION = "TENANT_ONBOARD";
    static final String ADMIN_ROLE_CODE = "ADMIN";
    static final String ACTION_SOURCE = "ADMIN_API";

    private final OperationalAuthorizationService operationalAuthorizationService;
    private final WorkflowTemplateQueryService workflowTemplateQueryService;
    private final TenantConfigCommandService tenantConfigCommandService;
    private final IdentityQueryService identityQueryService;
    private final IdentityCommandService identityCommandService;
    private final AuditService auditService;

    public TenantOnboardingService(
            OperationalAuthorizationService operationalAuthorizationService,
            WorkflowTemplateQueryService workflowTemplateQueryService,
            TenantConfigCommandService tenantConfigCommandService,
            IdentityQueryService identityQueryService,
            IdentityCommandService identityCommandService,
            AuditService auditService) {
        this.operationalAuthorizationService = operationalAuthorizationService;
        this.workflowTemplateQueryService = workflowTemplateQueryService;
        this.tenantConfigCommandService = tenantConfigCommandService;
        this.identityQueryService = identityQueryService;
        this.identityCommandService = identityCommandService;
        this.auditService = auditService;
    }

    public TenantOnboardingResult onboard(TenantOnboardingCommand command) {
        // 1. Authorization (global — actor hech qanday tenantning a'zosi
        //    bo'lmasligi ham mumkin emas, lekin TENANT_ONBOARD ruxsati
        //    biror tenantida bo'lishi shart).
        operationalAuthorizationService.authorizeGlobal(
                command.actorUserId(), TENANT_ONBOARD_PERMISSION);

        // 2. Command validation.
        String tenantName = validateTenantName(command.tenantName());
        String tenantSlug = validateTenantSlug(command.tenantSlug());
        String tenantTimezone = normalizeTimezone(command.tenantTimezone());
        Long adminTelegramUserId = validateTelegramUserId(command.adminTelegramUserId());
        String adminDisplayName = validateDisplayName(command.adminDisplayName());
        String adminUsername = normalizeUsername(command.adminUsername());
        List<String> templateCodes = validateTemplateCodes(command.workflowTemplateCodes());

        // 3. Template katalog'dan barcha so'ralgan shablonlarni yig'amiz.
        //    Noma'lum kod → BusinessRuleException, transaction rollback.
        List<WorkflowTemplate> templates = new ArrayList<>(templateCodes.size());
        Set<String> seenWorkflowNames = new HashSet<>();
        for (String code : templateCodes) {
            WorkflowTemplate template = workflowTemplateQueryService.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException(
                            "UNKNOWN_WORKFLOW_TEMPLATE",
                            "Noma'lum workflow shablon kodi: '" + code + "'"));
            // 4. Yagona request ichida ikkita shablon bir xil workflow nomini
            //    talab qilsa, DB unique (tenant_id, name) constraint'i ham
            //    portlaydi. Erta clean diagnostika qaytaramiz.
            if (!seenWorkflowNames.add(template.getName())) {
                throw new BusinessRuleException(
                        "DUPLICATE_WORKFLOW_NAME",
                        "So'rov ichida bir xil workflow nomi ('"
                                + template.getName() + "') ikki marta uchradi");
            }
            templates.add(template);
        }

        // 5. ADMIN rolni global katalogdan topamiz (V2 seed orqali mavjud).
        Role adminRole = identityQueryService.findRoleByCode(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new BusinessRuleException(
                        "ADMIN_ROLE_NOT_FOUND",
                        "ADMIN role global katalog'da topilmadi"));

        // 6. Tenant yaratish (slug-taken bu yerda DUPLICATE_TENANT_SLUG sifatida
        //    qaytadi — pastdan kelgan code'ni SLUG_TAKEN ga aylantirib qayta tashlaymiz
        //    onboarding semantikasi aniqroq bo'lishi uchun).
        Tenant tenant;
        try {
            tenant = tenantConfigCommandService.createTenant(tenantName, tenantSlug, tenantTimezone);
        } catch (BusinessRuleException ex) {
            if ("DUPLICATE_TENANT_SLUG".equals(ex.getErrorCode())) {
                throw new BusinessRuleException("SLUG_TAKEN",
                        "'" + tenantSlug + "' slug bilan tenant allaqachon mavjud");
            }
            throw ex;
        }

        // 7. AppUser find-or-create (Telegram user id bo'yicha).
        AppUser adminUser = identityQueryService.findUserByTelegramUserId(adminTelegramUserId)
                .orElseGet(() -> identityCommandService.createAppUser(
                        adminTelegramUserId, adminUsername, adminDisplayName));

        // 8. Admin Membership + ADMIN role binding.
        Membership membership = identityCommandService.createMembership(tenant.getId(), adminUser.getId());
        identityCommandService.assignRoleToMembership(
                tenant.getId(), membership.getId(), adminRole.getId());

        // 9. Onboarding darajadagi audit: TENANT_CREATED + ADMIN_MEMBERSHIP_CREATED.
        auditService.recordEvent(tenant.getId(), "TENANT", tenant.getId(),
                "TENANT_CREATED", command.actorUserId(), ACTION_SOURCE, null,
                "{\"slug\":\"" + tenantSlug + "\",\"name\":\"" + tenantName + "\"}");

        auditService.recordEvent(tenant.getId(), "MEMBERSHIP", membership.getId(),
                "ADMIN_MEMBERSHIP_CREATED", command.actorUserId(), ACTION_SOURCE, null,
                "{\"tenantId\":\"" + tenant.getId()
                        + "\",\"appUserId\":\"" + adminUser.getId()
                        + "\",\"role\":\"ADMIN\"}");

        // 10. Har bir shablon uchun workflow_definition + statuslar + transitionlar
        //     yaratamiz (input tartibida).
        List<TenantOnboardingResult.WorkflowDefinitionSummary> definitionSummaries =
                new ArrayList<>(templates.size());
        for (WorkflowTemplate template : templates) {
            WorkflowDefinition definition = tenantConfigCommandService.createWorkflowDefinition(
                    tenant.getId(), template.getName(),
                    template.getWorkItemType().name(), template.getDescription());

            // 10a. Statuslar — status_order tartibida; har birini mapga yozamiz
            //      keyingi transition rule yaratish uchun.
            Map<String, UUID> statusCodeToId = new HashMap<>();
            List<WorkflowTemplateStatus> templateStatuses =
                    workflowTemplateQueryService.listStatuses(template.getId());
            for (WorkflowTemplateStatus templateStatus : templateStatuses) {
                WorkflowStatus created = tenantConfigCommandService.createWorkflowStatus(
                        tenant.getId(), definition.getId(),
                        templateStatus.getStatusCode(),
                        templateStatus.getStatusOrder(),
                        templateStatus.isInitial(),
                        false /* terminal — shablon currently doesn't carry; default false */);
                statusCodeToId.put(templateStatus.getStatusCode(), created.getId());
            }

            // 10b. Transition rule'lar — fromStatusCode/toStatusCode shablon ichidagi
            //      kodlardan UUID'larga (10a-da yaratilgan WorkflowStatus.id) ko'chiriladi.
            List<WorkflowTemplateTransition> templateTransitions =
                    workflowTemplateQueryService.listTransitions(template.getId());
            for (WorkflowTemplateTransition tt : templateTransitions) {
                UUID fromId = statusCodeToId.get(tt.getFromStatusCode());
                UUID toId = statusCodeToId.get(tt.getToStatusCode());
                if (fromId == null || toId == null) {
                    // Shablon ichida noaniq status_code — bu shablon catalog
                    // tomonidan kafolatlangan invariant'ni buzgan bo'lishi mumkin.
                    // BusinessRuleException ko'rsatamiz, transaction rollback.
                    throw new BusinessRuleException("INVALID_TEMPLATE",
                            "Shablon '" + template.getCode()
                                    + "' ichida transition status kodi ("
                                    + tt.getFromStatusCode() + " -> "
                                    + tt.getToStatusCode() + ") topilmadi");
                }
                tenantConfigCommandService.createWorkflowTransitionRule(
                        tenant.getId(), definition.getId(), fromId, toId);
            }

            // 10c. Onboarding-darajadagi audit: har bir seed qilingan workflow uchun.
            auditService.recordEvent(tenant.getId(), "WORKFLOW_DEFINITION", definition.getId(),
                    "WORKFLOW_SEEDED", command.actorUserId(), ACTION_SOURCE, null,
                    "{\"templateCode\":\"" + template.getCode()
                            + "\",\"workflowDefinitionId\":\"" + definition.getId() + "\"}");

            definitionSummaries.add(new TenantOnboardingResult.WorkflowDefinitionSummary(
                    definition.getId(), template.getCode(),
                    template.getWorkItemType().name()));
        }

        // 11. Natija DTO yig'amiz va qaytaramiz.
        return new TenantOnboardingResult(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getCreatedAt(),
                adminUser.getId(), membership.getId(), List.copyOf(definitionSummaries));
    }

    // ========== Validation helpers ==========

    private String validateTenantName(String tenantName) {
        if (tenantName == null) {
            throw new BusinessRuleException("INVALID_TENANT_NAME", "tenantName majburiy");
        }
        String trimmed = tenantName.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("INVALID_TENANT_NAME", "tenantName bo'sh bo'la olmaydi");
        }
        if (trimmed.length() > 200) {
            throw new BusinessRuleException("INVALID_TENANT_NAME",
                    "tenantName 200 belgidan oshmasligi shart");
        }
        return trimmed;
    }

    private String validateTenantSlug(String tenantSlug) {
        if (tenantSlug == null) {
            throw new BusinessRuleException("INVALID_SLUG", "tenantSlug majburiy");
        }
        String trimmed = tenantSlug.strip();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new BusinessRuleException("INVALID_SLUG",
                    "tenantSlug uzunligi 3..50 belgi oralig'ida bo'lishi shart");
        }
        if (!SLUG_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessRuleException("INVALID_SLUG",
                    "tenantSlug faqat kichik harf, raqam va tire (-) bo'lishi mumkin; "
                            + "tirefn bilan boshlanmaslik/tugamaslik shart");
        }
        return trimmed;
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        String trimmed = timezone.strip();
        if (trimmed.length() > 50) {
            throw new BusinessRuleException("INVALID_TIMEZONE",
                    "tenantTimezone 50 belgidan oshmasligi shart");
        }
        return trimmed;
    }

    private Long validateTelegramUserId(Long telegramUserId) {
        if (telegramUserId == null || telegramUserId <= 0L) {
            throw new BusinessRuleException("INVALID_TELEGRAM_USER_ID",
                    "adminTelegramUserId musbat raqam bo'lishi shart");
        }
        return telegramUserId;
    }

    private String validateDisplayName(String displayName) {
        if (displayName == null) {
            throw new BusinessRuleException("INVALID_DISPLAY_NAME",
                    "adminDisplayName majburiy");
        }
        String trimmed = displayName.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("INVALID_DISPLAY_NAME",
                    "adminDisplayName bo'sh bo'la olmaydi");
        }
        if (trimmed.length() > 200) {
            throw new BusinessRuleException("INVALID_DISPLAY_NAME",
                    "adminDisplayName 200 belgidan oshmasligi shart");
        }
        return trimmed;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 100) {
            throw new BusinessRuleException("INVALID_USERNAME",
                    "adminUsername 100 belgidan oshmasligi shart");
        }
        return trimmed;
    }

    private List<String> validateTemplateCodes(List<String> templateCodes) {
        if (templateCodes == null || templateCodes.isEmpty()) {
            throw new BusinessRuleException("NO_TEMPLATES_REQUESTED",
                    "workflowTemplateCodes kamida 1 ta shablon kodini o'z ichiga olishi shart");
        }
        if (templateCodes.size() > 10) {
            throw new BusinessRuleException("TOO_MANY_TEMPLATES",
                    "workflowTemplateCodes 10 ta shablondan oshmasligi shart");
        }
        List<String> normalized = new ArrayList<>(templateCodes.size());
        for (String code : templateCodes) {
            if (code == null || code.isBlank()) {
                throw new BusinessRuleException("INVALID_TEMPLATE_CODE",
                        "workflowTemplateCodes ichida bo'sh kod uchradi");
            }
            normalized.add(code.strip());
        }
        return normalized;
    }
}
