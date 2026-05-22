package com.engops.platform.intake;

import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IntakeApplicationService unit testlari.
 *
 * Routing policy testlari RoutingDecisionServiceTest ichida.
 * Bu yerda faqat orchestration tekshiriladi:
 * - validate → workflow resolve → status resolve → routing → create → result → event publish
 *
 * <p>Phase 164: Telegram dispatch endi {@link ApplicationEventPublisher} orqali
 * AFTER_COMMIT listener'ga delegate qilinadi. Service test bu yerda faqat
 * publish boundary'ni tekshiradi (event publish bo'ladi yoki bo'lmaydi).
 * Listener fail-soft xulqi {@link TelegramCardDispatchEventListenerTest}
 * ichida tekshiriladi.</p>
 */
@ExtendWith(MockitoExtension.class)
class IntakeApplicationServiceTest {

    @Mock private WorkItemCommandService workItemCommandService;
    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private RoutingDecisionService routingDecisionService;
    @Mock private OperationalAuthorizationService operationalAuthorizationService;
    // Phase 164: AFTER_COMMIT Telegram dispatch event publish.
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IntakeApplicationService intakeService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workflowDefId = UUID.randomUUID();

    // --- Happy path ---

    @Test
    void explicitWorkflowBilanMuvaffaqiyatliYaratish() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.none());

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Login xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Login xato"), eq((String) null), eq("BUGS"), eq(userId), eq("TELEGRAM")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Login xato")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("TELEGRAM")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getWorkItemType()).isEqualTo("BUG");
        assertThat(result.getTitle()).isEqualTo("Login xato");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkflowDefinitionId()).isEqualTo(workflowDefId);
        assertThat(result.isRoutingPrepared()).isFalse();
        assertThat(result.getMatchedRoutingRuleId()).isNull();
        assertThat(result.getTargetChatBindingId()).isNull();
        assertThat(result.getTargetTopicId()).isNull();

        verify(routingDecisionService).resolve(tenantId, "BUG");
        verify(workItemCommandService).create(tenantId, WorkItemType.BUG, workflowDefId,
                "Login xato", null, "BUGS", userId, "TELEGRAM");
    }

    @Test
    void autoResolveWorkflowVaInitialStatus() {
        WorkflowDefinition def = mockActiveWorkflowWithInitialStatus(workflowDefId, "BUGS");

        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "BUG"))
                .thenReturn(List.of(def));

        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.none());

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Server xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Server xato"), eq((String) null), eq("BUGS"), eq(userId), eq("API")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Server xato")
                .createdByUserId(userId)
                .actionSource("API")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getWorkItemType()).isEqualTo("BUG");
        assertThat(result.getTitle()).isEqualTo("Server xato");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.isRoutingPrepared()).isFalse();
    }

    @Test
    void descriptionCreateContractIchidaUzatiladi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        when(routingDecisionService.resolve(tenantId, "INCIDENT"))
                .thenReturn(RoutingDecision.none());

        WorkItem createdItem = new WorkItem(tenantId, "INCIDENT-1", WorkItemType.INCIDENT,
                workflowDefId, "DB down", "OPEN", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.INCIDENT), eq(workflowDefId),
                eq("DB down"), eq("PostgreSQL server javob bermayapti"), eq("OPEN"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.INCIDENT)
                .title("DB down")
                .description("PostgreSQL server javob bermayapti")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("OPEN")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.getWorkItemCode()).isEqualTo("INCIDENT-1");
        assertThat(result.getWorkItemType()).isEqualTo("INCIDENT");
        assertThat(result.getTitle()).isEqualTo("DB down");

        verify(workItemCommandService).create(tenantId, WorkItemType.INCIDENT, workflowDefId,
                "DB down", "PostgreSQL server javob bermayapti", "OPEN", userId, "MANUAL");
    }

    @Test
    void routingDecisionMatchedBolsaResultdaAksEtadi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.matched(routingRuleId, topicBindingId, chatBindingId, topicId));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("TELEGRAM")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("TELEGRAM")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.isRoutingPrepared()).isTrue();
        assertThat(result.getMatchedRoutingRuleId()).isEqualTo(routingRuleId);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(topicBindingId);
        assertThat(result.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.getTargetTopicId()).isEqualTo(topicId);
        assertThat(result.getWorkItemType()).isEqualTo("BUG");
        assertThat(result.getTitle()).isEqualTo("Test");
    }

    // --- Routing fail-fast ---

    @Test
    void routingFailBolsaWorkItemYaratilmaydi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenThrow(new BusinessRuleException("ROUTING_TARGET_NOT_FOUND",
                        "topic binding topilmadi"));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("TELEGRAM")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topic binding topilmadi");

        verifyNoInteractions(workItemCommandService);
        verifyNoInteractions(eventPublisher);
    }

    // --- Validation failures ---

    @Test
    void nullTenantIdRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tenantId majburiy");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void nullTypeCodeRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("typeCode majburiy");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void blankTitleRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("   ")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("title bo'sh");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void nullTitleRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("title bo'sh");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void nullCreatedByUserIdRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("createdByUserId majburiy");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void blankActionSourceRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("  ")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actionSource bo'sh");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void nullActionSourceRadEtilishi() {
        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actionSource bo'sh");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    // --- Workflow resolution failures ---

    @Test
    void explicitWorkflowTopilmasa() {
        UUID unknownDefId = UUID.randomUUID();
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, unknownDefId))
                .thenReturn(Optional.empty());

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(unknownDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("WorkflowDefinition");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void explicitInactiveWorkflowDomainDanRadEtilishi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.none());

        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("OPEN"), eq(userId), eq("MANUAL")))
                .thenThrow(new BusinessRuleException("INACTIVE_WORKFLOW",
                        "Workflow aktiv emas"));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("OPEN")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aktiv emas");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void autoResolveAktivWorkflowTopilmasa() {
        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "TASK"))
                .thenReturn(List.of());

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.TASK)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aktiv workflow ta'rifi topilmadi");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void birNechtaAktivWorkflowAmbiguousRadEtilishi() {
        WorkflowDefinition def1 = mock(WorkflowDefinition.class);
        WorkflowDefinition def2 = mock(WorkflowDefinition.class);

        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "BUG"))
                .thenReturn(List.of(def1, def2));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 ta aktiv workflow topildi");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    // --- Initial status resolution ---

    @Test
    void initialStatusTopilmasa() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Empty Workflow");
        when(def.getStatuses()).thenReturn(List.of());

        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "BUG"))
                .thenReturn(List.of(def));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("boshlang'ich status topilmadi");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    @Test
    void birNechtaInitialStatusMavjudBolsa() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Ambiguous Workflow");

        WorkflowStatus status1 = mock(WorkflowStatus.class);
        when(status1.isInitial()).thenReturn(true);
        WorkflowStatus status2 = mock(WorkflowStatus.class);
        when(status2.isInitial()).thenReturn(true);
        when(def.getStatuses()).thenReturn(List.of(status1, status2));

        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "BUG"))
                .thenReturn(List.of(def));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bir nechta boshlang'ich status");

        verifyNoInteractions(workItemCommandService, routingDecisionService, eventPublisher);
    }

    // --- PreparedDeliveryTarget conversion ---

    @Test
    void toPreparedDeliveryTargetRoutingPreparedHolatda() {
        UUID workItemId = UUID.randomUUID();
        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;

        IntakeResult result = new IntakeResult(
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                workflowDefId, tenantId,
                null, null,
                true, routingRuleId, topicBindingId, chatBindingId, topicId);

        PreparedDeliveryTarget target = result.toPreparedDeliveryTarget();

        assertThat(target.getTenantId()).isEqualTo(tenantId);
        assertThat(target.getWorkItemId()).isEqualTo(workItemId);
        assertThat(target.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(target.getWorkItemType()).isEqualTo("BUG");
        assertThat(target.getTitle()).isEqualTo("Login xato");
        assertThat(target.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(target.getPriorityCode()).isNull();
        assertThat(target.getSeverityCode()).isNull();
        assertThat(target.isDeliveryReady()).isTrue();
        assertThat(target.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(target.getTargetTopicId()).isEqualTo(topicId);
    }

    /**
     * Phase 194 — when IntakeResult carries non-null priority/severity, the
     * derived PreparedDeliveryTarget must surface them verbatim. This is the
     * publisher-side guarantee the Telegram renderer relies on.
     */
    @Test
    void toPreparedDeliveryTargetForwardsPriorityAndSeverity() {
        UUID workItemId = UUID.randomUUID();

        IntakeResult result = new IntakeResult(
                workItemId, "BUG-9", "BUG", "Crash", "BUGS",
                workflowDefId, tenantId,
                "HIGH", "CRITICAL",
                true, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 99L);

        PreparedDeliveryTarget target = result.toPreparedDeliveryTarget();

        assertThat(target.getPriorityCode()).isEqualTo("HIGH");
        assertThat(target.getSeverityCode()).isEqualTo("CRITICAL");
    }

    @Test
    void toPreparedDeliveryTargetRoutingYoqHolatda() {
        UUID workItemId = UUID.randomUUID();

        IntakeResult result = new IntakeResult(
                workItemId, "BUG-2", "BUG", "Server xato", "BUGS",
                workflowDefId, tenantId,
                null, null,
                false, null, null, null, null);

        PreparedDeliveryTarget target = result.toPreparedDeliveryTarget();

        assertThat(target.getTenantId()).isEqualTo(tenantId);
        assertThat(target.getWorkItemId()).isEqualTo(workItemId);
        assertThat(target.getWorkItemCode()).isEqualTo("BUG-2");
        assertThat(target.getWorkItemType()).isEqualTo("BUG");
        assertThat(target.getTitle()).isEqualTo("Server xato");
        assertThat(target.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(target.isDeliveryReady()).isFalse();
        assertThat(target.getTargetChatBindingId()).isNull();
        assertThat(target.getTargetTopicId()).isNull();
    }

    // --- Phase 164: Telegram card dispatch event publish boundary ---

    /**
     * Phase 164: routing prepared bo'lsa intake muvaffaqiyatdan keyin
     * {@link TelegramCardDispatchRequested} event AFTER_COMMIT listener uchun
     * publish qilinadi. Telegram HTTP I/O endi intake transaction'i ichida
     * emas — listener commit'dan keyin ishga tushadi.
     *
     * <p>Event payload'i (ichidagi {@link PreparedDeliveryTarget}) yangi
     * yaratilgan work item identitysi va resolved routing target'ini olib
     * keladi. {@code sourceFlow = INTAKE}, {@code targetStatusCode = null}.</p>
     */
    @Test
    void routingPreparedBolsaTelegramDispatchEventiPublishQilinadi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.matched(routingRuleId, topicBindingId, chatBindingId, topicId));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Login xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Login xato"), eq((String) null), eq("BUGS"), eq(userId), eq("TELEGRAM")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Login xato")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("TELEGRAM")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.isRoutingPrepared()).isTrue();
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");

        // Event publish bo'lganini va field'larini lock qilish.
        ArgumentCaptor<TelegramCardDispatchRequested> eventCaptor =
                ArgumentCaptor.forClass(TelegramCardDispatchRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TelegramCardDispatchRequested event = eventCaptor.getValue();

        assertThat(event.sourceFlow()).isEqualTo(TelegramCardDispatchRequested.SOURCE_INTAKE);
        assertThat(event.targetStatusCode()).isNull();

        PreparedDeliveryTarget target = event.target();
        assertThat(target).isNotNull();
        assertThat(target.getTenantId()).isEqualTo(tenantId);
        assertThat(target.getWorkItemId()).isEqualTo(createdItem.getId());
        assertThat(target.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(target.getWorkItemType()).isEqualTo("BUG");
        assertThat(target.getTitle()).isEqualTo("Login xato");
        assertThat(target.getCurrentStatusCode()).isEqualTo("BUGS");
        // Phase 194 — intake API does not yet accept priority/severity, so the
        // default WorkItem produced here has null attributes.
        assertThat(target.getPriorityCode()).isNull();
        assertThat(target.getSeverityCode()).isNull();
        assertThat(target.isDeliveryReady()).isTrue();
        assertThat(target.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(target.getTargetTopicId()).isEqualTo(topicId);
    }

    /**
     * Phase 194: when the created WorkItem already carries non-null
     * priorityCode and severityCode (e.g. set by a future intake field or by
     * an integration that pre-populates them), the AFTER_COMMIT
     * {@link TelegramCardDispatchRequested} payload must surface those values
     * verbatim through {@link PreparedDeliveryTarget} so the Telegram renderer
     * can emit the optional lines.
     *
     * <p>This test exercises the publisher-side snapshot capture in
     * {@link IntakeApplicationService} — it does <strong>not</strong> imply
     * intake accepts these fields on the request body today.</p>
     */
    @Test
    void priorityVaSeverityWorkItemdaBolsaEventPayloadigaUzatiladi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;
        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.matched(routingRuleId, topicBindingId, chatBindingId, topicId));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-9", WorkItemType.BUG,
                workflowDefId, "Race condition", "BUGS", userId);
        createdItem.setPriorityCode("HIGH");
        createdItem.setSeverityCode("CRITICAL");
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Race condition"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Race condition")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        intakeService.submit(command);

        ArgumentCaptor<TelegramCardDispatchRequested> eventCaptor =
                ArgumentCaptor.forClass(TelegramCardDispatchRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PreparedDeliveryTarget target = eventCaptor.getValue().target();

        assertThat(target.getPriorityCode()).isEqualTo("HIGH");
        assertThat(target.getSeverityCode()).isEqualTo("CRITICAL");
    }

    /**
     * Phase 164: routing prepared bo'lmasa Telegram dispatch eventi
     * <strong>umuman publish qilinmaydi</strong>. Intake natija normal qaytadi.
     */
    @Test
    void routingPreparedEmasBolsaEventPublishQilinmaydi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        when(routingDecisionService.resolve(tenantId, "BUG"))
                .thenReturn(RoutingDecision.none());

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.isRoutingPrepared()).isFalse();
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");

        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- Phase 139: authorization denial ---

    @Test
    void submitDeniesActorWithoutWorkItemCreatePermission() {
        // validateCommand muvaffaqiyatli o'tadi (barcha field'lar to'ldirilgan).
        // Keyin operationalAuthorizationService.authorizeIntake AccessDeniedException
        // tashlaydi — submit shu exception'ni yuqoriga uzatishi va hech qanday
        // workflow lookup, routing decision, work item create yoki event publish
        // chaqiruvi bo'lmasligi shart.
        org.mockito.Mockito.doThrow(new AccessDeniedException(
                        "Bu operatsiya uchun WORK_ITEM_CREATE ruxsati talab qilinadi"))
                .when(operationalAuthorizationService).authorizeIntake(tenantId, userId);

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_CREATE");

        verify(operationalAuthorizationService).authorizeIntake(tenantId, userId);
        verifyNoInteractions(workItemCommandService, routingDecisionService,
                tenantConfigQueryService, eventPublisher);
    }

    // --- Helper ---

    private WorkflowDefinition mockActiveWorkflowWithInitialStatus(UUID defId,
                                                                     String initialStatusName) {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(defId);

        WorkflowStatus initialStatus = mock(WorkflowStatus.class);
        when(initialStatus.getName()).thenReturn(initialStatusName);
        when(initialStatus.isInitial()).thenReturn(true);
        when(def.getStatuses()).thenReturn(List.of(initialStatus));

        return def;
    }
}
