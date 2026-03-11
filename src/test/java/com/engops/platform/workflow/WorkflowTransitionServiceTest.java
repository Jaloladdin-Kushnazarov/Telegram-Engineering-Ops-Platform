package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
import com.engops.platform.workflow.model.WorkItemTransition;
import com.engops.platform.workflow.repository.WorkItemTransitionRepository;
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
}
