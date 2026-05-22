package com.engops.platform.workflow;

import com.engops.platform.audit.AuditService;
import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.intake.PreparedDeliveryTarget;
import com.engops.platform.intake.TelegramCardDispatchRequested;
import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowTransitionService unit testlari.
 * Status o'tkazish validatsiyasi, reopen logikasi va noto'g'ri o'tish rad etilishini tekshiradi.
 *
 * <p>Phase 164: Telegram dispatch endi {@link ApplicationEventPublisher} orqali
 * AFTER_COMMIT listener'ga delegate qilinadi. Service test bu yerda faqat
 * routing resolve + event publish boundary'ni tekshiradi. Listener fail-soft
 * xulqi {@code TelegramCardDispatchEventListenerTest} ichida tekshiriladi.</p>
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
    @Mock private RoutingDecisionService routingDecisionService;
    // Phase 164: AFTER_COMMIT Telegram dispatch event publish.
    @Mock private ApplicationEventPublisher eventPublisher;

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
        // Default routing — none. Routing-prepared testlar bu stub'ni override qiladi.
        when(routingDecisionService.resolve(any(UUID.class), any(String.class)))
                .thenReturn(RoutingDecision.none());
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
                auditService, workItemCommandService, eventPublisher);
    }

    @Test
    void transitionWorkItemNotFoundReturns404BeforeAuthorization() {
        when(workItemQueryService.findByTenantAndId(tenantId, workItemId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transitionService.transition(
                        tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null))
                .isInstanceOf(ResourceNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(operationalAuthorizationService,
                tenantConfigQueryService, transitionRepository, auditService,
                routingDecisionService, eventPublisher);
    }

    // --- Phase 164: Telegram update card dispatch event publish boundary ---

    /**
     * Phase 164: routing prepared bo'lsa transition muvaffaqiyatdan keyin
     * {@link TelegramCardDispatchRequested} eventi AFTER_COMMIT listener
     * uchun publish qilinadi. Telegram HTTP I/O endi workflow transaction'i
     * ichida emas — listener commit'dan keyin ishga tushadi.
     *
     * <p>Event payload'i {@link PreparedDeliveryTarget} yangi (post-transition)
     * statusCode bilan to'ldiriladi va resolved chat/topic target'ini
     * o'z ichiga oladi. {@code sourceFlow = WORKFLOW_TRANSITION},
     * {@code targetStatusCode = "PROCESSING"}.</p>
     */
    @Test
    void routingPreparedBolsaTelegramDispatchEventiPublishQilinadi() {
        WorkItem workItem = createWorkItem("BUGS");
        setupMocks(workItem);

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.matched(routingRuleId, topicBindingId, chatBindingId, topicId));

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("PROCESSING");
        verify(routingDecisionService).resolve(tenantId, "BUG");

        // Event publish bo'lganini va field'larini lock qilish.
        ArgumentCaptor<TelegramCardDispatchRequested> eventCaptor =
                ArgumentCaptor.forClass(TelegramCardDispatchRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TelegramCardDispatchRequested event = eventCaptor.getValue();

        assertThat(event.sourceFlow()).isEqualTo(TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION);
        assertThat(event.targetStatusCode()).isEqualTo("PROCESSING");

        PreparedDeliveryTarget target = event.target();
        assertThat(target).isNotNull();
        assertThat(target.getTenantId()).isEqualTo(tenantId);
        assertThat(target.getWorkItemId()).isEqualTo(workItem.getId());
        assertThat(target.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(target.getWorkItemType()).isEqualTo("BUG");
        assertThat(target.getTitle()).isEqualTo("Test bug");
        assertThat(target.getCurrentStatusCode())
                .as("PreparedDeliveryTarget yangi statusCode'ni saqlashi shart (transitionTo'dan keyin)")
                .isEqualTo("PROCESSING");
        // Phase 194 — default WorkItem in this test path has no priority/severity.
        assertThat(target.getPriorityCode()).isNull();
        assertThat(target.getSeverityCode()).isNull();
        assertThat(target.isDeliveryReady()).isTrue();
        assertThat(target.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(target.getTargetTopicId()).isEqualTo(topicId);
    }

    /**
     * Phase 194: when the transitioned WorkItem carries non-null priorityCode
     * and severityCode (e.g. set previously via the Phase 190 admin write
     * surface), the AFTER_COMMIT {@link TelegramCardDispatchRequested} payload
     * must forward them through {@link PreparedDeliveryTarget} so the
     * Telegram renderer can append the optional lines on the refreshed card.
     */
    @Test
    void priorityVaSeverityWorkItemdaBolsaEventPayloadigaUzatiladi() {
        WorkItem workItem = createWorkItem("BUGS");
        workItem.setPriorityCode("HIGH");
        workItem.setSeverityCode("CRITICAL");
        setupMocks(workItem);

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.matched(routingRuleId, topicBindingId, chatBindingId, topicId));

        transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        ArgumentCaptor<TelegramCardDispatchRequested> eventCaptor =
                ArgumentCaptor.forClass(TelegramCardDispatchRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PreparedDeliveryTarget target = eventCaptor.getValue().target();

        assertThat(target.getPriorityCode()).isEqualTo("HIGH");
        assertThat(target.getSeverityCode()).isEqualTo("CRITICAL");
    }

    /**
     * Phase 164: routing resolution {@link RuntimeException} tashlasa
     * (masalan ambiguous rule {@code BusinessRuleException} yoki kutilmagan
     * exception), transition fail-soft kontrakti bo'yicha muvaffaqiyatli
     * qaytadi va event <strong>umuman publish qilinmaydi</strong>.
     * Transition history va audit oldindan yozilgan — manba-haqiqat saqlanadi.
     */
    @Test
    void routingExceptionTashlasaTransitionMuvaffaqiyatliQoladi() {
        WorkItem workItem = createWorkItem("BUGS");
        setupMocks(workItem);
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenThrow(new BusinessRuleException("ROUTING_AMBIGUOUS",
                        "bir nechta unconditional rule eng yuqori priority'da"));

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("PROCESSING");
        verify(transitionRepository).save(any(WorkItemTransition.class));
        verify(auditService).recordEvent(tenantId, "WORK_ITEM", workItemId,
                "STATUS_TRANSITION", actorUserId, "MANUAL", "BUGS", "PROCESSING");
        verify(routingDecisionService).resolve(tenantId, "BUG");
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * Phase 164: routing prepared bo'lmasa (mos rule yo'q) event umuman
     * publish qilinmaydi. Transition baribir muvaffaqiyatli qaytadi.
     */
    @Test
    void routingPreparedEmasBolsaEventPublishQilinmaydi() {
        WorkItem workItem = createWorkItem("BUGS");
        setupMocks(workItem);
        // setupMocks RoutingDecision.none() default'ini o'rnatadi.

        WorkItem result = transitionService.transition(
                tenantId, workItemId, "PROCESSING", actorUserId, "MANUAL", null);

        assertThat(result.getCurrentStatusCode()).isEqualTo("PROCESSING");
        verify(routingDecisionService).resolve(tenantId, "BUG");
        verify(eventPublisher, never()).publishEvent(any());
    }
}
