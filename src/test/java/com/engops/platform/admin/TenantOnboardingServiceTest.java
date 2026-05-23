package com.engops.platform.admin;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.WorkflowTemplateQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 199 — {@link TenantOnboardingService} unit testlari.
 * Hamma collaborator'lar mock qilinadi. Onboarding orchestrator'ning
 * authorization → validation → reuse-existing-write-paths → audit emit
 * zanjirini tekshiradi.
 */
class TenantOnboardingServiceTest {

    private static final UUID ACTOR_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NEW_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_MEMBERSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID BUG_TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    private static final UUID TASK_TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000004");

    private final OperationalAuthorizationService authService = mock(OperationalAuthorizationService.class);
    private final WorkflowTemplateQueryService templateQuery = mock(WorkflowTemplateQueryService.class);
    private final TenantConfigCommandService tenantConfigCommand = mock(TenantConfigCommandService.class);
    private final IdentityQueryService identityQuery = mock(IdentityQueryService.class);
    private final IdentityCommandService identityCommand = mock(IdentityCommandService.class);
    private final AuditService auditService = mock(AuditService.class);

    private final TenantOnboardingService service = new TenantOnboardingService(
            authService, templateQuery, tenantConfigCommand,
            identityQuery, identityCommand, auditService);

    private Role adminRole;
    private Tenant newTenant;
    private AppUser newUser;
    private Membership newMembership;
    private WorkflowTemplate bugTemplate;
    private WorkflowTemplate taskTemplate;

    @BeforeEach
    void setUp() {
        adminRole = mock(Role.class);
        when(adminRole.getId()).thenReturn(ADMIN_ROLE_ID);
        when(adminRole.getCode()).thenReturn("ADMIN");

        newTenant = mock(Tenant.class);
        when(newTenant.getId()).thenReturn(NEW_TENANT_ID);
        when(newTenant.getSlug()).thenReturn("acme");
        when(newTenant.getName()).thenReturn("Acme Corp");
        when(newTenant.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-05-23T00:00:00Z"));

        newUser = mock(AppUser.class);
        when(newUser.getId()).thenReturn(NEW_USER_ID);

        newMembership = mock(Membership.class);
        when(newMembership.getId()).thenReturn(NEW_MEMBERSHIP_ID);

        bugTemplate = mock(WorkflowTemplate.class);
        when(bugTemplate.getId()).thenReturn(BUG_TEMPLATE_ID);
        when(bugTemplate.getCode()).thenReturn("BUG_MINIMAL");
        when(bugTemplate.getName()).thenReturn("Bug — Minimal lifecycle");
        when(bugTemplate.getDescription()).thenReturn("MVP bug flow");
        when(bugTemplate.getWorkItemType()).thenReturn(WorkItemType.BUG);

        taskTemplate = mock(WorkflowTemplate.class);
        when(taskTemplate.getId()).thenReturn(TASK_TEMPLATE_ID);
        when(taskTemplate.getCode()).thenReturn("TASK_BASIC");
        when(taskTemplate.getName()).thenReturn("Task — Basic lifecycle");
        when(taskTemplate.getDescription()).thenReturn("Task flow");
        when(taskTemplate.getWorkItemType()).thenReturn(WorkItemType.TASK);
    }

    private TenantOnboardingCommand validCommand(List<String> templateCodes) {
        return new TenantOnboardingCommand(
                "Acme Corp", "acme", "Asia/Tashkent",
                123456789L, "Demo Admin", "demo_admin",
                templateCodes, ACTOR_USER_ID);
    }

    private WorkflowTemplateStatus mockTemplateStatus(String code, int order, boolean initial) {
        WorkflowTemplateStatus s = mock(WorkflowTemplateStatus.class);
        when(s.getStatusCode()).thenReturn(code);
        when(s.getStatusOrder()).thenReturn(order);
        when(s.isInitial()).thenReturn(initial);
        return s;
    }

    private WorkflowTemplateTransition mockTemplateTransition(String from, String to, String label) {
        WorkflowTemplateTransition t = mock(WorkflowTemplateTransition.class);
        when(t.getFromStatusCode()).thenReturn(from);
        when(t.getToStatusCode()).thenReturn(to);
        when(t.getActionLabel()).thenReturn(label);
        return t;
    }

    private void wireBugMinimalTemplate() {
        // Build mocks FIRST so we don't open nested when() blocks while another
        // outer when()...thenReturn() chain is still in progress.
        WorkflowTemplateStatus s1 = mockTemplateStatus("BUGS", 1, true);
        WorkflowTemplateStatus s2 = mockTemplateStatus("PROCESSING", 2, false);
        WorkflowTemplateStatus s3 = mockTemplateStatus("TESTING", 3, false);
        WorkflowTemplateStatus s4 = mockTemplateStatus("FIXED", 4, false);
        WorkflowTemplateTransition t1 = mockTemplateTransition("BUGS", "PROCESSING", "Start");
        WorkflowTemplateTransition t2 = mockTemplateTransition("PROCESSING", "TESTING", "Mark Ready");
        WorkflowTemplateTransition t3 = mockTemplateTransition("TESTING", "FIXED", "Mark Fixed");
        WorkflowTemplateTransition t4 = mockTemplateTransition("TESTING", "BUGS", "Reopen");
        WorkflowTemplateTransition t5 = mockTemplateTransition("FIXED", "BUGS", "Reopen");

        when(templateQuery.findByCode("BUG_MINIMAL")).thenReturn(Optional.of(bugTemplate));
        when(templateQuery.listStatuses(BUG_TEMPLATE_ID))
                .thenReturn(List.of(s1, s2, s3, s4));
        when(templateQuery.listTransitions(BUG_TEMPLATE_ID))
                .thenReturn(List.of(t1, t2, t3, t4, t5));
    }

    private void wireTaskBasicTemplate() {
        WorkflowTemplateStatus s1 = mockTemplateStatus("TODO", 1, true);
        WorkflowTemplateStatus s2 = mockTemplateStatus("DONE", 2, false);
        WorkflowTemplateTransition t1 = mockTemplateTransition("TODO", "DONE", "Finish");

        when(templateQuery.findByCode("TASK_BASIC")).thenReturn(Optional.of(taskTemplate));
        when(templateQuery.listStatuses(TASK_TEMPLATE_ID))
                .thenReturn(List.of(s1, s2));
        when(templateQuery.listTransitions(TASK_TEMPLATE_ID))
                .thenReturn(List.of(t1));
    }

    private void wireBasicTenantAndUserCreation(boolean userExists) {
        when(identityQuery.findRoleByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(tenantConfigCommand.createTenant("Acme Corp", "acme", "Asia/Tashkent"))
                .thenReturn(newTenant);
        if (userExists) {
            when(identityQuery.findUserByTelegramUserId(123456789L)).thenReturn(Optional.of(newUser));
        } else {
            when(identityQuery.findUserByTelegramUserId(123456789L)).thenReturn(Optional.empty());
            when(identityCommand.createAppUser(123456789L, "demo_admin", "Demo Admin"))
                    .thenReturn(newUser);
        }
        when(identityCommand.createMembership(NEW_TENANT_ID, NEW_USER_ID)).thenReturn(newMembership);
        when(identityCommand.assignRoleToMembership(NEW_TENANT_ID, NEW_MEMBERSHIP_ID, ADMIN_ROLE_ID))
                .thenReturn(mock(MembershipRoleBinding.class));
    }

    private void wireWorkflowDefCreation(UUID definitionId, String workflowName) {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(definitionId);
        when(tenantConfigCommand.createWorkflowDefinition(
                eq(NEW_TENANT_ID), eq(workflowName), anyString(), anyString()))
                .thenReturn(def);
        WorkflowStatus stStub = mock(WorkflowStatus.class);
        when(stStub.getId()).thenAnswer(inv -> UUID.randomUUID());
        when(tenantConfigCommand.createWorkflowStatus(
                eq(NEW_TENANT_ID), eq(definitionId), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(stStub);
        when(tenantConfigCommand.createWorkflowTransitionRule(
                eq(NEW_TENANT_ID), eq(definitionId), any(UUID.class), any(UUID.class)))
                .thenReturn(mock(WorkflowTransitionRule.class));
    }

    // ========== Happy paths ==========

    @Test
    void onboard_happyPath_singleTemplate_createsTenantAdminAndOneWorkflow() {
        wireBugMinimalTemplate();
        wireBasicTenantAndUserCreation(false);
        UUID defId = UUID.randomUUID();
        wireWorkflowDefCreation(defId, "Bug — Minimal lifecycle");

        TenantOnboardingResult result = service.onboard(validCommand(List.of("BUG_MINIMAL")));

        assertThat(result.tenantId()).isEqualTo(NEW_TENANT_ID);
        assertThat(result.adminAppUserId()).isEqualTo(NEW_USER_ID);
        assertThat(result.adminMembershipId()).isEqualTo(NEW_MEMBERSHIP_ID);
        assertThat(result.workflowDefinitions()).hasSize(1);
        assertThat(result.workflowDefinitions().get(0).templateCode()).isEqualTo("BUG_MINIMAL");
        assertThat(result.workflowDefinitions().get(0).workItemType()).isEqualTo("BUG");

        verify(authService).authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD");
        verify(tenantConfigCommand).createWorkflowDefinition(
                NEW_TENANT_ID, "Bug — Minimal lifecycle", "BUG", "MVP bug flow");
        verify(tenantConfigCommand, times(4)).createWorkflowStatus(
                eq(NEW_TENANT_ID), eq(defId), anyString(), anyInt(), anyBoolean(), anyBoolean());
        verify(tenantConfigCommand, times(5)).createWorkflowTransitionRule(
                eq(NEW_TENANT_ID), eq(defId), any(UUID.class), any(UUID.class));
    }

    @Test
    void onboard_happyPath_multipleTemplates_createsAllWorkflowsInOrder() {
        wireBugMinimalTemplate();
        wireTaskBasicTemplate();
        wireBasicTenantAndUserCreation(false);
        UUID bugDefId = UUID.randomUUID();
        UUID taskDefId = UUID.randomUUID();
        wireWorkflowDefCreation(bugDefId, "Bug — Minimal lifecycle");
        wireWorkflowDefCreation(taskDefId, "Task — Basic lifecycle");

        TenantOnboardingResult result = service.onboard(
                validCommand(List.of("BUG_MINIMAL", "TASK_BASIC")));

        assertThat(result.workflowDefinitions()).hasSize(2);
        assertThat(result.workflowDefinitions().get(0).templateCode()).isEqualTo("BUG_MINIMAL");
        assertThat(result.workflowDefinitions().get(1).templateCode()).isEqualTo("TASK_BASIC");
    }

    @Test
    void onboard_existingTelegramUserId_reusesAppUser_noCreate() {
        wireBugMinimalTemplate();
        wireBasicTenantAndUserCreation(true /* user exists */);
        wireWorkflowDefCreation(UUID.randomUUID(), "Bug — Minimal lifecycle");

        service.onboard(validCommand(List.of("BUG_MINIMAL")));

        verify(identityCommand, never()).createAppUser(any(), any(), any());
        verify(identityQuery).findUserByTelegramUserId(123456789L);
    }

    @Test
    void onboard_newTelegramUserId_createsAppUser() {
        wireBugMinimalTemplate();
        wireBasicTenantAndUserCreation(false);
        wireWorkflowDefCreation(UUID.randomUUID(), "Bug — Minimal lifecycle");

        service.onboard(validCommand(List.of("BUG_MINIMAL")));

        verify(identityCommand).createAppUser(123456789L, "demo_admin", "Demo Admin");
    }

    @Test
    void onboard_auditTrail_emitsOnboardingLevelEvents() {
        wireBugMinimalTemplate();
        wireBasicTenantAndUserCreation(false);
        UUID defId = UUID.randomUUID();
        wireWorkflowDefCreation(defId, "Bug — Minimal lifecycle");

        service.onboard(validCommand(List.of("BUG_MINIMAL")));

        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(3)).recordEvent(
                any(UUID.class), anyString(), any(UUID.class), eventType.capture(),
                eq(ACTOR_USER_ID), eq("ADMIN_API"), any(), any());
        assertThat(eventType.getAllValues()).containsExactly(
                "TENANT_CREATED", "ADMIN_MEMBERSHIP_CREATED", "WORKFLOW_SEEDED");
    }

    @Test
    void onboard_auditTrail_emitsOneWorkflowSeededPerTemplate() {
        wireBugMinimalTemplate();
        wireTaskBasicTemplate();
        wireBasicTenantAndUserCreation(false);
        wireWorkflowDefCreation(UUID.randomUUID(), "Bug — Minimal lifecycle");
        wireWorkflowDefCreation(UUID.randomUUID(), "Task — Basic lifecycle");

        service.onboard(validCommand(List.of("BUG_MINIMAL", "TASK_BASIC")));

        verify(auditService, times(4)).recordEvent(
                any(UUID.class), anyString(), any(UUID.class), anyString(),
                eq(ACTOR_USER_ID), eq("ADMIN_API"), any(), any());
    }

    // ========== Sad paths ==========

    @Test
    void onboard_actorWithoutPermission_throwsAccessDenied_nothingCreated() {
        doThrow(new AccessDeniedException("yo'q"))
                .when(authService).authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD");

        assertThatThrownBy(() -> service.onboard(validCommand(List.of("BUG_MINIMAL"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(tenantConfigCommand, identityCommand, auditService);
    }

    @Test
    void onboard_unknownTemplateCode_throwsBusinessRuleException_nothingCreated() {
        when(templateQuery.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onboard(validCommand(List.of("UNKNOWN"))))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "UNKNOWN_WORKFLOW_TEMPLATE".equals(((BusinessRuleException) e).getErrorCode()));

        verifyNoInteractions(tenantConfigCommand, identityCommand);
    }

    @Test
    void onboard_invalidSlug_throwsBusinessRule_INVALID_SLUG() {
        TenantOnboardingCommand command = new TenantOnboardingCommand(
                "Acme", "INVALID UPPER", "UTC",
                123456789L, "Demo", null, List.of("BUG_MINIMAL"), ACTOR_USER_ID);

        assertThatThrownBy(() -> service.onboard(command))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_SLUG".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void onboard_emptyTemplateList_throwsBusinessRule_NO_TEMPLATES_REQUESTED() {
        assertThatThrownBy(() -> service.onboard(validCommand(List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "NO_TEMPLATES_REQUESTED".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void onboard_blankTenantName_throwsBusinessRule_INVALID_TENANT_NAME() {
        TenantOnboardingCommand command = new TenantOnboardingCommand(
                "  ", "acme", "UTC",
                123456789L, "Demo", null, List.of("BUG_MINIMAL"), ACTOR_USER_ID);

        assertThatThrownBy(() -> service.onboard(command))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TENANT_NAME".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void onboard_invalidTelegramUserId_throwsBusinessRule_INVALID_TELEGRAM_USER_ID() {
        TenantOnboardingCommand command = new TenantOnboardingCommand(
                "Acme", "acme", "UTC",
                0L, "Demo", null, List.of("BUG_MINIMAL"), ACTOR_USER_ID);

        assertThatThrownBy(() -> service.onboard(command))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TELEGRAM_USER_ID".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void onboard_duplicateSlugFromDownstream_translatedTo_SLUG_TAKEN() {
        wireBugMinimalTemplate();
        when(identityQuery.findRoleByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(tenantConfigCommand.createTenant("Acme Corp", "acme", "Asia/Tashkent"))
                .thenThrow(new BusinessRuleException("DUPLICATE_TENANT_SLUG", "exists"));

        assertThatThrownBy(() -> service.onboard(validCommand(List.of("BUG_MINIMAL"))))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "SLUG_TAKEN".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void onboard_duplicateWorkflowNameWithinRequest_throwsBusinessRule() {
        when(templateQuery.findByCode("BUG_MINIMAL")).thenReturn(Optional.of(bugTemplate));
        // Both template codes resolve to a template with the same `name` field.
        when(templateQuery.findByCode("BUG_FULL")).thenReturn(Optional.of(bugTemplate));

        assertThatThrownBy(() -> service.onboard(
                validCommand(List.of("BUG_MINIMAL", "BUG_FULL"))))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "DUPLICATE_WORKFLOW_NAME".equals(((BusinessRuleException) e).getErrorCode()));

        verifyNoInteractions(tenantConfigCommand, identityCommand);
    }

    @Test
    void onboard_workflowSeedFailureMidway_propagates_andTransactionRollsBack() {
        wireBugMinimalTemplate();
        wireTaskBasicTemplate();
        wireBasicTenantAndUserCreation(false);
        UUID bugDefId = UUID.randomUUID();
        wireWorkflowDefCreation(bugDefId, "Bug — Minimal lifecycle");
        // Second workflow seed: throw on createWorkflowDefinition for TASK_BASIC.
        when(tenantConfigCommand.createWorkflowDefinition(
                eq(NEW_TENANT_ID), eq("Task — Basic lifecycle"), anyString(), anyString()))
                .thenThrow(new BusinessRuleException("DUPLICATE_WORKFLOW_NAME", "boom"));

        assertThatThrownBy(() -> service.onboard(
                validCommand(List.of("BUG_MINIMAL", "TASK_BASIC"))))
                .isInstanceOf(BusinessRuleException.class);
        // The @Transactional wrapper isn't simulated in unit tests, but the exception
        // propagation contract is: the service does NOT swallow.  The Spring
        // transaction manager rolls back on RuntimeException at the outer boundary.
    }

    @Test
    void onboard_adminRoleNotFound_throwsBusinessRule() {
        wireBugMinimalTemplate();
        when(identityQuery.findRoleByCode("ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onboard(validCommand(List.of("BUG_MINIMAL"))))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "ADMIN_ROLE_NOT_FOUND".equals(((BusinessRuleException) e).getErrorCode()));
    }
}
