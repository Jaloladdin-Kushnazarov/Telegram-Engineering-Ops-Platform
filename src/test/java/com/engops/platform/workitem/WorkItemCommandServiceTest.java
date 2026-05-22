package com.engops.platform.workitem;

import com.engops.platform.audit.AuditService;
import com.engops.platform.audit.model.AuditEvent;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.workitem.model.UpdateType;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.model.WorkItemUpdate;
import com.engops.platform.workitem.repository.WorkItemRepository;
import com.engops.platform.workitem.repository.WorkItemUpdateRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkItemCommandService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkItemCommandServiceTest {

    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemUpdateRepository workItemUpdateRepository;
    @Mock private WorkItemCodeGenerator codeGenerator;
    @Mock private AuditService auditService;
    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private IdentityQueryService identityQueryService;

    @InjectMocks
    private WorkItemCommandService commandService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workflowDefId = UUID.randomUUID();

    private WorkflowDefinition mockWorkflowDef(String workItemType, String initialStatusName) {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Bug Workflow");
        when(def.isActive()).thenReturn(true);
        when(def.getWorkItemType()).thenReturn(workItemType);

        WorkflowStatus initialStatus = mock(WorkflowStatus.class);
        when(initialStatus.getName()).thenReturn(initialStatusName);
        when(initialStatus.isInitial()).thenReturn(true);

        when(def.getStatuses()).thenReturn(List.of(initialStatus));
        return def;
    }

    @Test
    void workItemYaratish() {
        WorkflowDefinition def = mockWorkflowDef("BUG", "BUGS");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));
        when(codeGenerator.generate(tenantId, WorkItemType.BUG)).thenReturn("BUG-1");
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", UUID.randomUUID(), "CREATED", userId));

        WorkItem result = commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Login sahifada xato", null, "BUGS", userId, "MANUAL");

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getTitle()).isEqualTo("Login sahifada xato");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getTypeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(result.getDescription()).isNull();

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), any(),
                eq("CREATED"), eq(userId), eq("MANUAL"), eq(null), eq("BUG-1"));
    }

    @Test
    void descriptionBilanWorkItemYaratish() {
        WorkflowDefinition def = mockWorkflowDef("BUG", "BUGS");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));
        when(codeGenerator.generate(tenantId, WorkItemType.BUG)).thenReturn("BUG-2");
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", UUID.randomUUID(), "CREATED", userId));

        WorkItem result = commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Login sahifada xato", "Sahifa 500 xato qaytaradi", "BUGS", userId, "MANUAL");

        assertThat(result.getDescription()).isEqualTo("Sahifa 500 xato qaytaradi");
        assertThat(result.getWorkItemCode()).isEqualTo("BUG-2");
    }

    @Test
    void inactiveWorkflowRadEtilishi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Old Bug Workflow");
        when(def.isActive()).thenReturn(false);

        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", null, "BUGS", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aktiv emas");
    }

    @Test
    void workflowTypeMismatchRadEtilishi() {
        // Workflow INCIDENT uchun, lekin BUG yaratmoqchi
        WorkflowDefinition def = mockWorkflowDef("INCIDENT", "OPEN");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", null, "OPEN", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mos kelmaydi");
    }

    @Test
    void notogriBoshlangichStatusRadEtilishi() {
        WorkflowDefinition def = mockWorkflowDef("BUG", "BUGS");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", null, "PROCESSING", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topilmadi");
    }

    @Test
    void initialEmasBoshlangichStatusRadEtilishi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Bug Workflow");
        when(def.isActive()).thenReturn(true);
        when(def.getWorkItemType()).thenReturn("BUG");

        WorkflowStatus nonInitialStatus = mock(WorkflowStatus.class);
        when(nonInitialStatus.getName()).thenReturn("PROCESSING");
        when(nonInitialStatus.isInitial()).thenReturn(false);

        when(def.getStatuses()).thenReturn(List.of(nonInitialStatus));
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", null, "PROCESSING", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("boshlang'ich holat emas");
    }

    @Test
    void ownerTayinlash() {
        UUID workItemId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);

        // Active membership — facade orqali
        when(identityQueryService.hasActiveMembership(tenantId, ownerUserId)).thenReturn(true);

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "OWNER_ASSIGNED", userId));

        WorkItem result = commandService.assignOwner(tenantId, workItemId, ownerUserId, userId, "MANUAL");

        assertThat(result.getCurrentOwnerUserId()).isEqualTo(ownerUserId);
        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("OWNER_ASSIGNED"), eq(userId), eq("MANUAL"), eq(null), eq(ownerUserId.toString()));
    }

    @Test
    void membershipYoqOwnerRadEtilishi() {
        UUID workItemId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(identityQueryService.hasActiveMembership(tenantId, ownerUserId)).thenReturn(false);

        assertThatThrownBy(() -> commandService.assignOwner(tenantId, workItemId, ownerUserId, userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("faol a'zo emas");
    }

    @Test
    void yangilanishQoshish() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "UPDATE_ADDED", userId));

        WorkItemUpdate update = commandService.addUpdate(tenantId, workItemId, userId,
                UpdateType.COMMENT, "Bug qayta namoyon bo'ldi", "MANUAL");

        assertThat(update.getBody()).isEqualTo("Bug qayta namoyon bo'ldi");
        assertThat(update.getUpdateTypeCode()).isEqualTo(UpdateType.COMMENT);

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("UPDATE_ADDED"), eq(userId), eq("MANUAL"), eq(null), eq("COMMENT"));
    }

    // ========== Phase 190 — updatePriority ==========

    @Test
    void priorityYangilashMuvaffaqiyatli() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        existing.setPriorityCode("LOW");

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "PRIORITY_CHANGED", userId));

        WorkItem result = commandService.updatePriority(tenantId, workItemId, "HIGH",
                userId, "ADMIN_API");

        assertThat(result.getPriorityCode()).isEqualTo("HIGH");
        assertThat(result.getUpdatedByUserId()).isEqualTo(userId);

        // WorkItemUpdate yozildi (PRIORITY_CHANGE)
        org.mockito.ArgumentCaptor<WorkItemUpdate> updateCaptor =
                org.mockito.ArgumentCaptor.forClass(WorkItemUpdate.class);
        verify(workItemUpdateRepository).save(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getUpdateTypeCode()).isEqualTo(UpdateType.PRIORITY_CHANGE);
        assertThat(updateCaptor.getValue().getBody()).isEqualTo("HIGH");
        assertThat(updateCaptor.getValue().getAuthorUserId()).isEqualTo(userId);

        // Audit qatori PRIORITY_CHANGED — old "LOW" → new "HIGH"
        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("PRIORITY_CHANGED"), eq(userId), eq("ADMIN_API"), eq("LOW"), eq("HIGH"));
    }

    @Test
    void priorityYangilashOldNullBoladi() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        // priorityCode null (boshlang'ich holat)

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "PRIORITY_CHANGED", userId));

        commandService.updatePriority(tenantId, workItemId, "CRITICAL", userId, "ADMIN_API");

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("PRIORITY_CHANGED"), eq(userId), eq("ADMIN_API"), eq(null), eq("CRITICAL"));
    }

    @Test
    void priorityYangilashNotogriQiymatRadEtilishi() {
        UUID workItemId = UUID.randomUUID();
        // Repository lookup chaqirilmasligi kerak — validation ilgariroq yiqilishi shart.

        assertThatThrownBy(() -> commandService.updatePriority(
                tenantId, workItemId, "URGENT", userId, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("priorityCode");
    }

    @Test
    void priorityYangilashBoshPriorityRadEtilishi() {
        UUID workItemId = UUID.randomUUID();
        assertThatThrownBy(() -> commandService.updatePriority(
                tenantId, workItemId, "  ", userId, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void priorityYangilashWorkItemTopilmasaRadEtilishi() {
        UUID workItemId = UUID.randomUUID();
        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.updatePriority(
                tenantId, workItemId, "HIGH", userId, "ADMIN_API"))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.ResourceNotFoundException.class);
    }

    // ========== Phase 190 — updateSeverity ==========

    @Test
    void severityYangilashMuvaffaqiyatli() {
        UUID workItemId = UUID.randomUUID();
        WorkItem existing = new WorkItem(tenantId, "BUG-1", WorkItemType.BUG,
                workflowDefId, "Test", "BUGS", userId);
        existing.setSeverityCode("LOW");

        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.of(existing));
        when(workItemRepository.save(any(WorkItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workItemUpdateRepository.save(any(WorkItemUpdate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AuditEvent(tenantId, "WORK_ITEM", workItemId, "SEVERITY_CHANGED", userId));

        WorkItem result = commandService.updateSeverity(tenantId, workItemId, "CRITICAL",
                userId, "ADMIN_API");

        assertThat(result.getSeverityCode()).isEqualTo("CRITICAL");
        assertThat(result.getUpdatedByUserId()).isEqualTo(userId);

        org.mockito.ArgumentCaptor<WorkItemUpdate> updateCaptor =
                org.mockito.ArgumentCaptor.forClass(WorkItemUpdate.class);
        verify(workItemUpdateRepository).save(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getUpdateTypeCode()).isEqualTo(UpdateType.SEVERITY_CHANGE);
        assertThat(updateCaptor.getValue().getBody()).isEqualTo("CRITICAL");
        assertThat(updateCaptor.getValue().getAuthorUserId()).isEqualTo(userId);

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), eq(workItemId),
                eq("SEVERITY_CHANGED"), eq(userId), eq("ADMIN_API"), eq("LOW"), eq("CRITICAL"));
    }

    @Test
    void severityYangilashNotogriQiymatRadEtilishi() {
        UUID workItemId = UUID.randomUUID();

        assertThatThrownBy(() -> commandService.updateSeverity(
                tenantId, workItemId, "BLOCKER", userId, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("severityCode");
    }

    @Test
    void severityYangilashNullQiymatRadEtilishi() {
        UUID workItemId = UUID.randomUUID();

        assertThatThrownBy(() -> commandService.updateSeverity(
                tenantId, workItemId, null, userId, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void severityYangilashWorkItemTopilmasaRadEtilishi() {
        UUID workItemId = UUID.randomUUID();
        when(workItemRepository.findByTenantIdAndId(tenantId, workItemId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.updateSeverity(
                tenantId, workItemId, "HIGH", userId, "ADMIN_API"))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.ResourceNotFoundException.class);
    }

    // ========== Phase 190 — argument validation ==========

    @Test
    void updatePriorityNullTenantRadEtilishi() {
        assertThatThrownBy(() -> commandService.updatePriority(
                null, UUID.randomUUID(), "HIGH", userId, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void updatePriorityNullActorRadEtilishi() {
        assertThatThrownBy(() -> commandService.updatePriority(
                tenantId, UUID.randomUUID(), "HIGH", null, "ADMIN_API"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actorUserId");
    }

    @Test
    void updateSeverityBoshActionSourceRadEtilishi() {
        assertThatThrownBy(() -> commandService.updateSeverity(
                tenantId, UUID.randomUUID(), "HIGH", userId, "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actionSource");
    }
}
