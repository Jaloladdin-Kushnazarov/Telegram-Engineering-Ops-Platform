package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.workflow.model.WorkItemTransition;
import com.engops.platform.workflow.repository.WorkItemTransitionRepository;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.WorkItemQueryService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowTransitionService unit testlari.
 * Status o'tkazish validatsiyasi, reopen logikasi va noto'g'ri o'tish rad etilishini tekshiradi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowTransitionServiceTest {

    @Mock private WorkItemQueryService workItemQueryService;
    @Mock private WorkItemCommandService workItemCommandService;
    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private WorkItemTransitionRepository transitionRepository;
    @Mock private AuditService auditService;
    @Mock private OperationalAuthorizationService operationalAuthorizationService;

    @InjectMocks
    private WorkflowTransitionService transitionService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID workItemId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID workflowDefId = UUID.randomUUID();

    private WorkflowDefinition workflowDef;
    private WorkflowStatus bugsStatus;
    private WorkflowStatus processingStatus;
    private WorkflowStatus testingStatus;
    private WorkflowStatus fixedStatus;
    private List<WorkflowTransitionRule> transitionRules;
    private List<WorkflowStatus> statuses;

    @BeforeEach
    void setUp() {
        workflowDef = org.mockito.Mockito.mock(WorkflowDefinition.class);

        bugsStatus = createStatus("BUGS", false);
        processingStatus = createStatus("PROCESSING", false);
        testingStatus = createStatus("TESTING", false);
        fixedStatus = createStatus("FIXED", true);

        statuses = List.of(bugsStatus, processingStatus, testingStatus, fixedStatus);

        transitionRules = List.of(
                createRule(bugsStatus, processingStatus),
                createRule(processingStatus, testingStatus),
                createRule(testingStatus, fixedStatus),
                createRule(testingStatus, bugsStatus),
                createRule(fixedStatus, bugsStatus)
        );

        when(workflowDef.getTransitionRules()).thenReturn(transitionRules);
        when(workflowDef.getStatuses()).thenReturn(statuses);
    }

    private WorkflowStatus createStatus(String name, boolean terminal) {
        WorkflowStatus status = org.mockito.Mockito.mock(WorkflowStatus.class);
        when(status.getName()).thenReturn(name);
        when(status.isTerminal()).thenReturn(terminal);
        return status;
    }

    private WorkflowTransitionRule createRule(WorkflowStatus from, WorkflowStatus to) {
        WorkflowTransitionRule rule = org.mockito.Mockito.mock(WorkflowTransitionRule.class);
        when(rule.getFromStatus()).thenReturn(from);
        when(rule.getToStatus()).thenReturn(to);
        return rule;
    }

    private WorkItem createWorkItem(String statusCode) {
        return new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test bug", statusCode, actorUserId);
    }

    private void setupMocks(WorkItem workItem) {
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.of(workItem));
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(workflowDef));
        when(workItemCommandService.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(WorkItemTransition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "STATUS_TRANSITION", actorUserId));
    }

    @Test
    void muvaffaqiyatliStatusOtkazish() {
        WorkItem workItem = createWorkItem("BUGS");
        setupMocks(workItem);

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("PROCESSING");
        verify(transitionRepository).save(any(WorkItemTransition.class));
        verify(auditService).recordEvent(tenantId, "WORK_ITEM", workItemId,
                "STATUS_TRANSITION", actorUserId, "MANUAL", "BUGS", "PROCESSING");
    }

    @Test
    void notogrioOtishRadEtilishi() {
        WorkItem workItem = createWorkItem("BUGS");
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.of(workItem));
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(workflowDef));

        assertThatThrownBy(() -> transitionService.transition(
                tenantId, workItemId, "FIXED", actorUserId, "MANUAL", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ruxsat etilmagan");
    }

    @Test
    void ayniHolatgaOtishRadEtilishi() {
        WorkItem workItem = createWorkItem("BUGS");
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.of(workItem));

        assertThatThrownBy(() -> transitionService.transition(
                tenantId, workItemId, "BUGS", actorUserId, "MANUAL", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");
    }

    @Test
    void terminalHolatgaOtgandaResolvedBelgilanadi() {
        WorkItem workItem = createWorkItem("TESTING");
        setupMocks(workItem);

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "FIXED", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("FIXED");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    void reopenLogikasi() {
        WorkItem workItem = createWorkItem("FIXED");
        workItem.markResolved();
        setupMocks(workItem);

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "BUGS", actorUserId, "MANUAL", "Qayta namoyon bo'ldi");

        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getReopenedCount()).isEqualTo(1);
        assertThat(result.getResolvedAt()).isNull();
    }

    @Test
    void testingdanBugsGaQaytarish() {
        WorkItem workItem = createWorkItem("TESTING");
        setupMocks(workItem);

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "BUGS", actorUserId, "MANUAL", "Test o'tmadi");

        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
    }

    /**
     * Frozen contract (Phase 112): inactive workflow definition NEW work item
     * yaratishni bloklaydi (WorkItemCommandService.create da INACTIVE_WORKFLOW
     * guard, WorkItemCommandServiceTest#inactiveWorkflowRadEtilishi tomonidan
     * qoplangan). Ammo mavjud in-flight work item'larning transition'ini
     * BLOKLAMAYDI — deactivation faqat creation lifecycle'ni to'xtatadi,
     * existing items o'z transition'larini davom ettiradi. Shu test contract'ni
     * ochiq fixate qiladi: agar kelgusi phase'da transition'ga inactive gate
     * kiritilsa, shu test loud sinaydi.
     */
    @Test
    void inactiveWorkflowdaTransitionRuxsatEtiladi() {
        when(workflowDef.isActive()).thenReturn(false);
        WorkItem workItem = createWorkItem("BUGS");
        setupMocks(workItem);

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("PROCESSING");
        verify(transitionRepository).save(any(WorkItemTransition.class));
    }

    // --- Phase 139: authorization denial ---

    @Test
    void transitionDeniesActorWithoutWorkItemTransitionPermission() {
        // Tenant-safe lookup birinchi navbatda bajariladi va work item topiladi —
        // 404 emas. Keyin operationalAuthorizationService.authorizeTransition
        // AccessDeniedException tashlaydi. transition shu exception'ni yuqoriga
        // uzatadi va hech qanday workflow lookup, mutation, transition save yoki
        // audit event chaqiruvi bo'lmaydi.
        WorkItem workItem = createWorkItem("BUGS");
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.of(workItem));
        org.mockito.Mockito.doThrow(new AccessDeniedException(
                        "Bu operatsiya uchun WORK_ITEM_TRANSITION ruxsati talab qilinadi"))
                .when(operationalAuthorizationService).authorizeTransition(tenantId, actorUserId);

        assertThatThrownBy(() -> transitionService.transition(
                        tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_TRANSITION");

        verify(workItemQueryService).findByTenantAndId(tenantId, workItemId);
        verify(operationalAuthorizationService).authorizeTransition(tenantId, actorUserId);
        org.mockito.Mockito.verifyNoInteractions(tenantConfigQueryService, transitionRepository,
                auditService, workItemCommandService);
    }

    @Test
    void transitionWorkItemNotFoundReturns404BeforeAuthorization() {
        // Phase 139 invariant: tenant-safe lookup AVVAL bajariladi.
        // Cross-tenant yoki mavjud bo'lmagan work item uchun ResourceNotFoundException
        // (404) qaytariladi va authorization service umuman chaqirilmaydi —
        // 404 semantikasi 403 ga aylanmaydi.
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transitionService.transition(
                        tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null))
                .isInstanceOf(ResourceNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(operationalAuthorizationService,
                tenantConfigQueryService, transitionRepository, auditService);
    }
}
