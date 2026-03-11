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
                "Login sahifada xato", "BUGS", userId, "MANUAL");

        assertThat(result.getWorkItemCode()).isEqualTo("BUG-1");
        assertThat(result.getTitle()).isEqualTo("Login sahifada xato");
        assertThat(result.getCurrentStatusCode()).isEqualTo("BUGS");
        assertThat(result.getTypeCode()).isEqualTo(WorkItemType.BUG);

        verify(auditService).recordEvent(eq(tenantId), eq("WORK_ITEM"), any(),
                eq("CREATED"), eq(userId), eq("MANUAL"), eq(null), eq("BUG-1"));
    }

    @Test
    void workflowTypeMismatchRadEtilishi() {
        // Workflow INCIDENT uchun, lekin BUG yaratmoqchi
        WorkflowDefinition def = mockWorkflowDef("INCIDENT", "OPEN");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", "OPEN", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mos kelmaydi");
    }

    @Test
    void notogriBoshlangichStatusRadEtilishi() {
        WorkflowDefinition def = mockWorkflowDef("BUG", "BUGS");
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", "PROCESSING", userId, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topilmadi");
    }

    @Test
    void initialEmasBoshlangichStatusRadEtilishi() {
        WorkflowDefinition def = mock(WorkflowDefinition.class);
        when(def.getName()).thenReturn("Bug Workflow");
        when(def.getWorkItemType()).thenReturn("BUG");

        WorkflowStatus nonInitialStatus = mock(WorkflowStatus.class);
        when(nonInitialStatus.getName()).thenReturn("PROCESSING");
        when(nonInitialStatus.isInitial()).thenReturn(false);

        when(def.getStatuses()).thenReturn(List.of(nonInitialStatus));
        when(tenantConfigQueryService.findWorkflowDefinitionById(tenantId, workflowDefId))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> commandService.create(tenantId, WorkItemType.BUG, workflowDefId,
                "Test", "PROCESSING", userId, "MANUAL"))
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
}
