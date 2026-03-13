package com.engops.platform.intake;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IntakeApplicationService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class IntakeApplicationServiceTest {

    @Mock private WorkItemCommandService workItemCommandService;
    @Mock private TenantConfigQueryService tenantConfigQueryService;

    @InjectMocks
    private IntakeApplicationService intakeService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workflowDefId = UUID.randomUUID();

    // --- Happy path ---

    @Test
    void explicitWorkflowBilanMuvaffaqiyatliYaratish() {
        // Explicit workflow + explicit initial status: faqat getId() kerak
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Login xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Login xato"), eq((String) null), eq("BUGS"), eq(userId), eq("TELEGRAM")))
                .thenReturn(createdItem);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of());

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
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkflowDefinitionId()).isEqualTo(workflowDefId);
        assertThat(result.isRoutingPrepared()).isFalse();
        assertThat(result.getMatchedRoutingRuleId()).isNull();

        verify(workItemCommandService).create(tenantId, WorkItemType.BUG, workflowDefId,
                "Login xato", null, "BUGS", userId, "TELEGRAM");
    }

    @Test
    void autoResolveWorkflowVaInitialStatus() {
        // Auto-resolve: getId(), getStatuses() kerak
        WorkflowDefinition def = mockActiveWorkflowWithInitialStatus(workflowDefId, "BUGS");

        when(tenantConfigQueryService.findActiveWorkflowDefinitionsByType(tenantId, "BUG"))
                .thenReturn(List.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Server xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Server xato"), eq((String) null), eq("BUGS"), eq(userId), eq("API")))
                .thenReturn(createdItem);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of());

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Server xato")
                .createdByUserId(userId)
                .actionSource("API")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.isRoutingPrepared()).isFalse();
    }

    @Test
    void descriptionCreateContractIchidaUzatiladi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "INCIDENT-1", WorkItemType.INCIDENT,
                workflowDefId, "DB down", "OPEN", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.INCIDENT), eq(workflowDefId),
                eq("DB down"), eq("PostgreSQL server javob bermayapti"), eq("OPEN"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "INCIDENT"))
                .thenReturn(List.of());

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

        // description create chaqiruvi ichida uzatilganini verify qilamiz
        verify(workItemCommandService).create(tenantId, WorkItemType.INCIDENT, workflowDefId,
                "DB down", "PostgreSQL server javob bermayapti", "OPEN", userId, "MANUAL");
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
    }

    @Test
    void explicitInactiveWorkflowDomainDanRadEtilishi() {
        // Explicit inactive workflow — domain service (WorkItemCommandService) rad etadi
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
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

        verifyNoInteractions(workItemCommandService);
    }

    // --- Routing preparation ---

    @Test
    void routingRuleTopilgandaRoutingPreparedTrue() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Login xato", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Login xato"), eq((String) null), eq("BUGS"), eq(userId), eq("TELEGRAM")))
                .thenReturn(createdItem);

        UUID routingRuleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(routingRuleId);
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

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
        assertThat(result.getMatchedRoutingRuleId()).isEqualTo(routingRuleId);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(topicBindingId);
    }

    @Test
    void birNechtaRoutingRulePriorityBilanDeterministikTanlash() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        UUID highRuleId = UUID.randomUUID();
        UUID highTopicId = UUID.randomUUID();

        // High priority rule — winner: getId, getPriority, getTargetTopicBindingId kerak
        RoutingRule highRule = mock(RoutingRule.class);
        when(highRule.getId()).thenReturn(highRuleId);
        when(highRule.getPriority()).thenReturn(200);
        when(highRule.getTargetTopicBindingId()).thenReturn(highTopicId);

        // Low priority rule — faqat getPriority tekshiriladi
        RoutingRule lowRule = mock(RoutingRule.class);
        when(lowRule.getPriority()).thenReturn(100);

        // priority DESC tartibda qaytaradi (repository garantiyasi)
        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(highRule, lowRule));

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

        assertThat(result.isRoutingPrepared()).isTrue();
        assertThat(result.getMatchedRoutingRuleId()).isEqualTo(highRuleId);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(highTopicId);
    }

    @Test
    void birXilPrioritetdaAmbiguousRoutingRadEtilishi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        RoutingRule rule1 = mock(RoutingRule.class);
        when(rule1.getPriority()).thenReturn(100);
        RoutingRule rule2 = mock(RoutingRule.class);
        when(rule2.getPriority()).thenReturn(100);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule1, rule2));

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.BUG)
                .title("Test")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("BUGS")
                .createdByUserId(userId)
                .actionSource("MANUAL")
                .build();

        assertThatThrownBy(() -> intakeService.submit(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unconditional routing rule bir xil prioritetga");
    }

    @Test
    void conditionalRuleChetlatiladiUnconditionalYoqBolsa() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        // Faqat conditional rule bor — unconditional yo'q
        RoutingRule conditionalRule = mock(RoutingRule.class);
        when(conditionalRule.getConditionExpression()).thenReturn("severity == 'CRITICAL'");

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(conditionalRule));

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

        // Conditional rule candidate emas — routingPrepared=false
        assertThat(result.isRoutingPrepared()).isFalse();
        assertThat(result.getMatchedRoutingRuleId()).isNull();
    }

    @Test
    void mixedRulesConditionalChetlatilibUnconditionalTanlanadi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.BUG), eq(workflowDefId),
                eq("Test"), eq((String) null), eq("BUGS"), eq(userId), eq("MANUAL")))
                .thenReturn(createdItem);

        // Conditional rule — yuqori priority, lekin candidate emas
        RoutingRule conditionalHighRule = mock(RoutingRule.class);
        when(conditionalHighRule.getConditionExpression()).thenReturn("severity == 'CRITICAL'");

        // Unconditional rule — pastroq priority, lekin candidate
        UUID unconditionalRuleId = UUID.randomUUID();
        UUID unconditionalTopicId = UUID.randomUUID();
        RoutingRule unconditionalLowRule = mock(RoutingRule.class);
        when(unconditionalLowRule.getId()).thenReturn(unconditionalRuleId);
        when(unconditionalLowRule.getTargetTopicBindingId()).thenReturn(unconditionalTopicId);
        // getConditionExpression() default null — unconditional

        // priority DESC: conditional(200) birinchi, unconditional(100) ikkinchi
        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(conditionalHighRule, unconditionalLowRule));

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

        // Conditional chetlatildi — unconditional tanlanadi
        assertThat(result.isRoutingPrepared()).isTrue();
        assertThat(result.getMatchedRoutingRuleId()).isEqualTo(unconditionalRuleId);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(unconditionalTopicId);
    }

    @Test
    void routingRuleTopilmagandaRoutingPreparedFalse() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getId()).thenReturn(workflowDefId);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        WorkItem createdItem = new WorkItem(tenantId, "TASK-1", WorkItemType.TASK,
                workflowDefId, "Deploy", "TODO", userId);
        when(workItemCommandService.create(eq(tenantId), eq(WorkItemType.TASK), eq(workflowDefId),
                eq("Deploy"), eq((String) null), eq("TODO"), eq(userId), eq("API")))
                .thenReturn(createdItem);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "TASK"))
                .thenReturn(List.of());

        IntakeCommand command = IntakeCommand.builder()
                .tenantId(tenantId)
                .typeCode(WorkItemType.TASK)
                .title("Deploy")
                .workflowDefinitionId(workflowDefId)
                .initialStatusCode("TODO")
                .createdByUserId(userId)
                .actionSource("API")
                .build();

        IntakeResult result = intakeService.submit(command);

        assertThat(result.getWorkItemCode()).isEqualTo("TASK-1");
        assertThat(result.isRoutingPrepared()).isFalse();
        assertThat(result.getMatchedRoutingRuleId()).isNull();
        assertThat(result.getTargetTopicBindingId()).isNull();
    }

    // --- Helper ---

    /**
     * Auto-resolve path uchun workflow mock (getId, getStatuses kerak).
     */
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
