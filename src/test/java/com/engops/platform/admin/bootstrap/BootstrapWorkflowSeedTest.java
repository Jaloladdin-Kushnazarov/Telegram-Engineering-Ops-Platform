package com.engops.platform.admin.bootstrap;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 156 — first-admin bootstrap workflow seed kengaytmasi uchun testlar.
 *
 * <p>Mockito-based unit testlar — collaborator service'lar mock qilinadi va
 * {@link BootstrapAdminInitializer#seedDefaultBugWorkflow(UUID)}'ning xulqi
 * tekshiriladi:</p>
 * <ul>
 *   <li>Default disabled — admin bootstrap o'tadi, workflow operatsiyasi yo'q</li>
 *   <li>Enabled + yangi workflow — definition + 4 status + 5 transition</li>
 *   <li>Enabled + mavjud workflow — qayta ishlatiladi, dublikatsiz</li>
 *   <li>Enabled + qisman mavjud (ba'zi statuslar/rule'lar) — yetishmaganlar
 *       to'ldiriladi, mavjudlari qayta yaratilmaydi</li>
 *   <li>Enabled + to'liq mavjud (idempotent re-run) — hech qanday create yo'q</li>
 * </ul>
 *
 * <p>Spring context bootstrap qilinmaydi — fast, deterministic. Workflow seed
 * failure rollback xulqi {@code @Transactional} run() darajasida hujjatlangan
 * (Phase 143 pattern); alohida transaction failure testi qo'shilmaydi
 * (overengineering oldini olish — Phase 143 da admin yo'liga test bor).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapWorkflowSeedTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID WORKFLOW_DEFINITION_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID STATUS_BUGS_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID STATUS_PROCESSING_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID STATUS_TESTING_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555553");
    private static final UUID STATUS_FIXED_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555554");
    private static final Long TELEGRAM_USER_ID = 12345L;
    private static final String TENANT_NAME = "Acme";
    private static final String TENANT_SLUG = "acme";
    private static final String DISPLAY_NAME = "Operator Admin";
    private static final String WORKFLOW_NAME = "MVP Bug Flow";

    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private TenantConfigCommandService tenantConfigCommandService;
    @Mock private IdentityQueryService identityQueryService;
    @Mock private IdentityCommandService identityCommandService;
    @Mock private AuditService auditService;

    private BootstrapProperties properties;
    private BootstrapWorkflowProperties workflowProperties;
    private BootstrapAdminInitializer initializer;
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @BeforeEach
    void setUp() {
        properties = new BootstrapProperties();
        properties.setEnabled(true);
        properties.setTenantName(TENANT_NAME);
        properties.setTenantSlug(TENANT_SLUG);
        properties.setTenantTimezone("UTC");
        properties.setAppUserId(ADMIN_USER_ID);
        properties.setTelegramUserId(TELEGRAM_USER_ID);
        properties.setDisplayName(DISPLAY_NAME);

        workflowProperties = new BootstrapWorkflowProperties();
        // workflow.enabled default false — har bir test atayin yoqadi.

        initializer = new BootstrapAdminInitializer(properties, workflowProperties,
                tenantConfigQueryService, tenantConfigCommandService,
                identityQueryService, identityCommandService, auditService);

        // Admin bootstrap collaborator'larini happy-path defaultlar bilan to'ldirish.
        Tenant tenant = mock(Tenant.class);
        lenient().when(tenant.getId()).thenReturn(TENANT_ID);
        AppUser user = mock(AppUser.class);
        lenient().when(user.getId()).thenReturn(ADMIN_USER_ID);
        Membership membership = mock(Membership.class);
        lenient().when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        Role adminRole = mock(Role.class);
        lenient().when(adminRole.getId()).thenReturn(ADMIN_ROLE_ID);
        lenient().when(adminRole.getCode()).thenReturn(BootstrapAdminInitializer.ADMIN_ROLE_CODE);
        MembershipRoleBinding adminBinding = mock(MembershipRoleBinding.class);
        lenient().when(adminBinding.getRole()).thenReturn(adminRole);

        lenient().when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG))
                .thenReturn(Optional.of(tenant));
        lenient().when(identityQueryService.findUserById(ADMIN_USER_ID))
                .thenReturn(Optional.of(user));
        lenient().when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.of(membership));
        lenient().when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        lenient().when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID))
                .thenReturn(List.of(adminBinding));
    }

    // ========== Admin disabled — workflow flag ham e'tiborga olinmaydi ==========

    @Test
    void adminBootstrapDisabledWithWorkflowSeedEnabled_isCompleteNoOp() {
        // Phase 156 mini-fix: agar admin bootstrap o'chirilgan bo'lsa, workflow
        // seed flag yoqilgan yoki valid bo'lishidan qat'i nazar, initializer
        // hech qanday collaborator'ga tegmasligi shart (validatsiya ham
        // o'tkazilmaydi — early return).
        properties.setEnabled(false);
        workflowProperties.setEnabled(true);
        workflowProperties.setName("MVP Bug Flow");

        initializer.run(args);

        verifyNoInteractions(tenantConfigQueryService, tenantConfigCommandService,
                identityQueryService, identityCommandService, auditService);
    }

    // ========== Fail-fast — workflow seed yoqilgan, lekin name blank ==========

    @Test
    void adminEnabledWorkflowEnabledBlankName_failsFastBeforeAnyMutation() {
        // Phase 156 mini-fix: workflow seed yoqilgan, lekin
        // app.bootstrap.workflow.name bo'sh/whitespace bo'lsa, fail-fast
        // IllegalStateException — hech qanday tenant/user lookup yoki create
        // qilinmasdan oldin.
        workflowProperties.setEnabled(true);
        workflowProperties.setName("   ");

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bootstrap.workflow.name");

        // Hech qanday tenant/user/membership/role/audit/workflow operatsiyasi
        // bo'lmagan — validatsiya admin bootstrap mutatsiyalaridan oldin
        // run() ni rad etadi.
        verify(tenantConfigCommandService, never()).createTenant(any(), any(), any());
        verify(identityCommandService, never()).createAppUserWithId(any(), any(), any(), any());
        verify(identityCommandService, never()).createMembership(any(), any());
        verify(identityCommandService, never()).assignRoleToMembership(any(), any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowDefinition(any(), any(), any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowStatus(any(), any(), any(), anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommandService, never()).createWorkflowTransitionRule(any(), any(), any(), any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ========== Disabled (default) ==========

    @Test
    void workflowSeedDisabledByDefault_adminBootstrapStillRuns_noWorkflowOperations() {
        // workflowProperties default'i: enabled=false. Admin bootstrap yangi
        // resurs yaratmaydi (collaborator default'lari hammasi mavjud), faqat
        // BOOTSTRAP_COMPLETED audit yoziladi va workflow yo'lining birorta ham
        // metodi chaqirilmaydi.
        initializer.run(args);

        verify(tenantConfigQueryService, never()).findWorkflowDefinition(any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowDefinition(any(), any(), any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowStatus(any(), any(), any(), anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommandService, never()).createWorkflowTransitionRule(any(), any(), any(), any());
    }

    // ========== Enabled + yangi workflow ==========

    @Test
    void workflowSeedEnabled_newWorkflow_createsDefinitionFourStatusesAndFiveTransitions() {
        workflowProperties.setEnabled(true);

        // Workflow definition yo'q — yangi yaratiladi.
        when(tenantConfigQueryService.findWorkflowDefinition(TENANT_ID,
                BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE))
                .thenReturn(Optional.empty());

        WorkflowDefinition newDefinition = mockWorkflowDefinition(List.of(), List.of());
        when(tenantConfigCommandService.createWorkflowDefinition(
                TENANT_ID, WORKFLOW_NAME, BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE, null))
                .thenReturn(newDefinition);

        WorkflowStatus bugs = mockStatus(STATUS_BUGS_ID, BootstrapAdminInitializer.STATUS_BUGS);
        WorkflowStatus processing = mockStatus(STATUS_PROCESSING_ID, BootstrapAdminInitializer.STATUS_PROCESSING);
        WorkflowStatus testing = mockStatus(STATUS_TESTING_ID, BootstrapAdminInitializer.STATUS_TESTING);
        WorkflowStatus fixed = mockStatus(STATUS_FIXED_ID, BootstrapAdminInitializer.STATUS_FIXED);

        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_BUGS),
                eq(0), eq(true), eq(false))).thenReturn(bugs);
        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_PROCESSING),
                eq(1), eq(false), eq(false))).thenReturn(processing);
        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_TESTING),
                eq(2), eq(false), eq(false))).thenReturn(testing);
        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_FIXED),
                eq(3), eq(false), eq(true))).thenReturn(fixed);

        initializer.run(args);

        verify(tenantConfigCommandService, times(1)).createWorkflowDefinition(
                TENANT_ID, WORKFLOW_NAME, BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE, null);

        // 4 ta status ham yaratiladi.
        verify(tenantConfigCommandService).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_BUGS,
                0, true, false);
        verify(tenantConfigCommandService).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_PROCESSING,
                1, false, false);
        verify(tenantConfigCommandService).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_TESTING,
                2, false, false);
        verify(tenantConfigCommandService).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_FIXED,
                3, false, true);

        // 5 ta transition rule ham yaratiladi.
        verify(tenantConfigCommandService).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_BUGS_ID, STATUS_PROCESSING_ID);
        verify(tenantConfigCommandService).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_PROCESSING_ID, STATUS_TESTING_ID);
        verify(tenantConfigCommandService).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_TESTING_ID, STATUS_FIXED_ID);
        verify(tenantConfigCommandService).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_TESTING_ID, STATUS_BUGS_ID);
        verify(tenantConfigCommandService).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_FIXED_ID, STATUS_BUGS_ID);
    }

    // ========== Enabled + to'liq mavjud (idempotent re-run) ==========

    @Test
    void workflowSeedEnabled_fullExistingWorkflow_isIdempotentNoCreates() {
        workflowProperties.setEnabled(true);

        WorkflowStatus bugs = mockStatus(STATUS_BUGS_ID, BootstrapAdminInitializer.STATUS_BUGS);
        WorkflowStatus processing = mockStatus(STATUS_PROCESSING_ID, BootstrapAdminInitializer.STATUS_PROCESSING);
        WorkflowStatus testing = mockStatus(STATUS_TESTING_ID, BootstrapAdminInitializer.STATUS_TESTING);
        WorkflowStatus fixed = mockStatus(STATUS_FIXED_ID, BootstrapAdminInitializer.STATUS_FIXED);

        List<WorkflowTransitionRule> existingRules = List.of(
                mockRule(bugs, processing),
                mockRule(processing, testing),
                mockRule(testing, fixed),
                mockRule(testing, bugs),
                mockRule(fixed, bugs));

        WorkflowDefinition existingDefinition = mockWorkflowDefinition(
                List.of(bugs, processing, testing, fixed), existingRules);
        when(tenantConfigQueryService.findWorkflowDefinition(TENANT_ID,
                BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE))
                .thenReturn(Optional.of(existingDefinition));

        initializer.run(args);

        // Hech qanday yangi resurs yaratilmasligi shart.
        verify(tenantConfigCommandService, never()).createWorkflowDefinition(any(), any(), any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowStatus(any(), any(), any(), anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommandService, never()).createWorkflowTransitionRule(any(), any(), any(), any());
    }

    // ========== Enabled + qisman mavjud (statuslar to'liq, transitionlar qisman) ==========

    @Test
    void workflowSeedEnabled_partialExistingWorkflow_fillsOnlyMissingTransitions() {
        workflowProperties.setEnabled(true);

        WorkflowStatus bugs = mockStatus(STATUS_BUGS_ID, BootstrapAdminInitializer.STATUS_BUGS);
        WorkflowStatus processing = mockStatus(STATUS_PROCESSING_ID, BootstrapAdminInitializer.STATUS_PROCESSING);
        WorkflowStatus testing = mockStatus(STATUS_TESTING_ID, BootstrapAdminInitializer.STATUS_TESTING);
        WorkflowStatus fixed = mockStatus(STATUS_FIXED_ID, BootstrapAdminInitializer.STATUS_FIXED);

        // Faqat 2 ta transition mavjud — qolgan 3 tasini to'ldirish kerak.
        List<WorkflowTransitionRule> partialRules = List.of(
                mockRule(bugs, processing),
                mockRule(processing, testing));

        WorkflowDefinition existingDefinition = mockWorkflowDefinition(
                List.of(bugs, processing, testing, fixed), partialRules);
        when(tenantConfigQueryService.findWorkflowDefinition(TENANT_ID,
                BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE))
                .thenReturn(Optional.of(existingDefinition));

        initializer.run(args);

        // Workflow definition / statuslar qaytadan yaratilmaydi.
        verify(tenantConfigCommandService, never()).createWorkflowDefinition(any(), any(), any(), any());
        verify(tenantConfigCommandService, never()).createWorkflowStatus(any(), any(), any(), anyInt(), anyBoolean(), anyBoolean());

        // Faqat yetishmagan 3 ta transition rule yaratiladi.
        verify(tenantConfigCommandService, never()).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_BUGS_ID, STATUS_PROCESSING_ID);
        verify(tenantConfigCommandService, never()).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_PROCESSING_ID, STATUS_TESTING_ID);
        verify(tenantConfigCommandService, times(1)).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_TESTING_ID, STATUS_FIXED_ID);
        verify(tenantConfigCommandService, times(1)).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_TESTING_ID, STATUS_BUGS_ID);
        verify(tenantConfigCommandService, times(1)).createWorkflowTransitionRule(
                TENANT_ID, WORKFLOW_DEFINITION_ID, STATUS_FIXED_ID, STATUS_BUGS_ID);
    }

    // ========== Enabled + statuslar qisman mavjud ==========

    @Test
    void workflowSeedEnabled_partialStatuses_fillsMissingStatusesAndAllTransitions() {
        workflowProperties.setEnabled(true);

        // BUGS va PROCESSING mavjud, TESTING va FIXED yo'q.
        WorkflowStatus bugs = mockStatus(STATUS_BUGS_ID, BootstrapAdminInitializer.STATUS_BUGS);
        WorkflowStatus processing = mockStatus(STATUS_PROCESSING_ID, BootstrapAdminInitializer.STATUS_PROCESSING);

        WorkflowDefinition existingDefinition = mockWorkflowDefinition(
                List.of(bugs, processing), List.of());
        when(tenantConfigQueryService.findWorkflowDefinition(TENANT_ID,
                BootstrapAdminInitializer.BUG_WORK_ITEM_TYPE))
                .thenReturn(Optional.of(existingDefinition));

        WorkflowStatus testing = mockStatus(STATUS_TESTING_ID, BootstrapAdminInitializer.STATUS_TESTING);
        WorkflowStatus fixed = mockStatus(STATUS_FIXED_ID, BootstrapAdminInitializer.STATUS_FIXED);

        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_TESTING),
                eq(2), eq(false), eq(false))).thenReturn(testing);
        when(tenantConfigCommandService.createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_FIXED),
                eq(3), eq(false), eq(true))).thenReturn(fixed);

        initializer.run(args);

        // Workflow definition mavjud — qaytadan yaratilmaydi.
        verify(tenantConfigCommandService, never()).createWorkflowDefinition(any(), any(), any(), any());

        // BUGS / PROCESSING qaytadan yaratilmaydi; TESTING / FIXED yaratiladi.
        verify(tenantConfigCommandService, never()).createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_BUGS),
                anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommandService, never()).createWorkflowStatus(eq(TENANT_ID),
                eq(WORKFLOW_DEFINITION_ID), eq(BootstrapAdminInitializer.STATUS_PROCESSING),
                anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommandService, times(1)).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_TESTING,
                2, false, false);
        verify(tenantConfigCommandService, times(1)).createWorkflowStatus(
                TENANT_ID, WORKFLOW_DEFINITION_ID, BootstrapAdminInitializer.STATUS_FIXED,
                3, false, true);

        // 5 ta transition rule yaratiladi.
        verify(tenantConfigCommandService, times(5)).createWorkflowTransitionRule(
                eq(TENANT_ID), eq(WORKFLOW_DEFINITION_ID), any(UUID.class), any(UUID.class));
    }

    // ========== Helpers ==========

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private WorkflowDefinition mockWorkflowDefinition(List<WorkflowStatus> statuses,
                                                       List<WorkflowTransitionRule> rules) {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(WORKFLOW_DEFINITION_ID);
        // ArrayList copy — agar production kod modify qilsa ham mock'ga ta'sir qilmasin
        // (Collections.unmodifiableList view oqim semantikasi).
        when(def.getStatuses()).thenReturn(new ArrayList<>(statuses));
        when(def.getTransitionRules()).thenReturn(new ArrayList<>(rules));
        return def;
    }

    private WorkflowStatus mockStatus(UUID id, String name) {
        WorkflowStatus s = mock(WorkflowStatus.class);
        when(s.getId()).thenReturn(id);
        when(s.getName()).thenReturn(name);
        return s;
    }

    private WorkflowTransitionRule mockRule(WorkflowStatus from, WorkflowStatus to) {
        WorkflowTransitionRule r = mock(WorkflowTransitionRule.class);
        when(r.getFromStatus()).thenReturn(from);
        when(r.getToStatus()).thenReturn(to);
        return r;
    }
}
