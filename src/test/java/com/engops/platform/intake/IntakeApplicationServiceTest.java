package com.engops.platform.intake;

import com.engops.platform.routing.RoutingDecision;
import com.engops.platform.routing.RoutingDecisionService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.telegram.TelegramCardDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import com.engops.platform.telegram.TelegramCardViewService;
import com.engops.platform.telegram.TelegramDeliveryAttempt;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * - validate → workflow resolve → status resolve → routing → create → result
 */
@ExtendWith(MockitoExtension.class)
class IntakeApplicationServiceTest {

    @Mock private WorkItemCommandService workItemCommandService;
    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private RoutingDecisionService routingDecisionService;
    @Mock private OperationalAuthorizationService operationalAuthorizationService;
    // Phase 160 — Telegram card outbound dispatch zanjir collaborator'lari.
    @Mock private ProjectionAssembler projectionAssembler;
    @Mock private TelegramCardViewService telegramCardViewService;
    @Mock private TelegramCardDispatchService telegramCardDispatchService;

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

        // Phase 160 mini-fix: routingPrepared=true bo'lganda Telegram dispatch
        // chain Mockito default null'lariga emas, explicit clean stub'larga
        // tayanishi kerak — fail-soft catch yo'liga tushib qolmasligi uchun.
        stubSuccessfulTelegramDispatch();

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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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

        verifyNoInteractions(workItemCommandService, routingDecisionService);
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
                true, routingRuleId, topicBindingId, chatBindingId, topicId);

        PreparedDeliveryTarget target = result.toPreparedDeliveryTarget();

        assertThat(target.getTenantId()).isEqualTo(tenantId);
        assertThat(target.getWorkItemId()).isEqualTo(workItemId);
        assertThat(target.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(target.getWorkItemType()).isEqualTo("BUG");
        assertThat(target.getTitle()).isEqualTo("Login xato");
        assertThat(target.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(target.isDeliveryReady()).isTrue();
        assertThat(target.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(target.getTargetTopicId()).isEqualTo(topicId);
    }

    @Test
    void toPreparedDeliveryTargetRoutingYoqHolatda() {
        UUID workItemId = UUID.randomUUID();

        IntakeResult result = new IntakeResult(
                workItemId, "BUG-2", "BUG", "Server xato", "BUGS",
                workflowDefId, tenantId,
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

    // --- Phase 160: Telegram card dispatch integration ---

    /**
     * Phase 160: routing prepared bo'lsa intake muvaffaqiyatdan keyin
     * Telegram card dispatch zanjiri ishga tushadi:
     * ProjectionAssembler → TelegramCardViewService → TelegramCardDispatchService.
     * Intake natija normal qaytadi.
     */
    @Test
    void routingPreparedBolsaTelegramCardDispatchChaqiriladi() {
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

        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt attempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(any(PreparedDeliveryTarget.class))).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchService.dispatchAttempt(cardView)).thenReturn(attempt);

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

        verify(projectionAssembler).assemble(any(PreparedDeliveryTarget.class));
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }

    /**
     * Phase 160: routing prepared bo'lmasa Telegram dispatch zanjiri umuman
     * chaqirilmaydi. Intake natija normal qaytadi.
     */
    @Test
    void routingPreparedEmasBolsaTelegramDispatchChaqirilmaydi() {
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

        verify(projectionAssembler, never()).assemble(any());
        verify(telegramCardViewService, never()).buildCardView(any());
        verify(telegramCardDispatchService, never()).dispatchAttempt(any());
    }

    /**
     * Phase 160: dispatch service kutilmagan {@link RuntimeException} tashlasa,
     * intake fail-soft kontrakti bo'yicha natijani sifatida qaytaradi.
     * Work item allaqachon yaratilgan — manba-haqiqat saqlanadi.
     */
    @Test
    void telegramDispatchExceptionTashlasaIntakeMuvaffaqiyatliQaytadi() {
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

        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(any(PreparedDeliveryTarget.class))).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchService.dispatchAttempt(cardView))
                .thenThrow(new RuntimeException("simulated outbound dispatch failure"));

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
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        verify(workItemCommandService).create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", null, "BUGS", userId, "TELEGRAM");
        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }

    /**
     * Phase 160: Telegram dispatch FAILED yoki REJECTED outcome bilan attempt
     * qaytarsa (gateway reject sharoitida — masalan invalid chat), intake
     * baribir muvaffaqiyatli qaytadi. Failed attempt allaqachon DB'da
     * (TelegramCardDispatchService ichidagi attemptPersistence orqali).
     */
    @Test
    void telegramDispatchFailedAttemptQaytarsaIntakeMuvaffaqiyatliQaytadi() {
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

        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt failedAttempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(any(PreparedDeliveryTarget.class))).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        // Mock failed/rejected attempt — production code natijani o'qimaydi,
        // faqat dispatch chaqirilganini va exception emas qaytarganini
        // kutadi (failed attempt allaqachon attemptPersistence ichida yozilgan).
        when(telegramCardDispatchService.dispatchAttempt(cardView)).thenReturn(failedAttempt);

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
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }

    // --- Phase 139: authorization denial ---

    @Test
    void submitDeniesActorWithoutWorkItemCreatePermission() {
        // validateCommand muvaffaqiyatli o'tadi (barcha field'lar to'ldirilgan).
        // Keyin operationalAuthorizationService.authorizeIntake AccessDeniedException
        // tashlaydi — submit shu exception'ni yuqoriga uzatishi va hech qanday
        // workflow lookup, routing decision yoki work item create chaqiruvi
        // bo'lmasligi shart.
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
                tenantConfigQueryService);
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

    /**
     * Phase 160 mini-fix: routingPrepared=true sharoiti uchun Telegram dispatch
     * zanjirini explicit clean stub'lar bilan to'ldiradi. Bu helper Mockito
     * default null/no-stub yo'lining tasodifan fail-soft catch'ga tushib
     * qolishini oldini oladi va dispatch invariantini aniq lock qiladi.
     * Test'da {@code projectionAssembler.assemble(...)} yoki
     * {@code telegramCardViewService.buildCardView(...)} stub'lari ataylab
     * boshqa qiymat qaytarishi uchun yozilgan bo'lsa, helper'dan oldin
     * stublash mumkin (Mockito so'nggi stubni saqlaydi).
     */
    private void stubSuccessfulTelegramDispatch() {
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt attempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(any(PreparedDeliveryTarget.class))).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchService.dispatchAttempt(cardView)).thenReturn(attempt);
    }
}
