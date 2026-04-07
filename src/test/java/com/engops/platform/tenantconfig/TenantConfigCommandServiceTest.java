package com.engops.platform.tenantconfig;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TenantConfigCommandService unit testlari.
 *
 * Tekshiruvlar:
 * - success path: workflow definition yaratiladi va audit yoziladi
 * - tenant not found: ResourceNotFoundException
 * - duplicate name: BusinessRuleException (application-level)
 * - duplicate name from DB constraint: BusinessRuleException (DB fallback)
 * - audit yozilganini verify qilish
 */
class TenantConfigCommandServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final WorkflowDefinitionRepository workflowDefinitionRepository =
            mock(WorkflowDefinitionRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantConfigCommandService service =
            new TenantConfigCommandService(tenantRepository, workflowDefinitionRepository, auditService);

    @Test
    void createWorkflowDefinitionSuccess() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Bug Flow"))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.createWorkflowDefinition(
                TENANT_ID, "Bug Flow", "BUG", "Bug workflow description");

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getName()).isEqualTo("Bug Flow");
        assertThat(result.getWorkItemType()).isEqualTo("BUG");
        assertThat(result.getDescription()).isEqualTo("Bug workflow description");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getId()).isNotNull();

        verify(tenantRepository).findById(TENANT_ID);
        verify(workflowDefinitionRepository).findByTenantIdAndName(TENANT_ID, "Bug Flow");
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }

    @Test
    void createWorkflowDefinitionWithNullDescription() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Incident Flow"))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.createWorkflowDefinition(
                TENANT_ID, "Incident Flow", "INCIDENT", null);

        assertThat(result.getDescription()).isNull();
    }

    @Test
    void createWorkflowDefinitionThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkflowDefinition(
                TENANT_ID, "Bug Flow", "BUG", null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantRepository).findById(TENANT_ID);
        verify(workflowDefinitionRepository, never()).findByTenantIdAndName(any(), any());
        verify(workflowDefinitionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createWorkflowDefinitionThrowsBusinessRuleForDuplicateName() {
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = mock(WorkflowDefinition.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Bug Flow"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createWorkflowDefinition(
                TENANT_ID, "Bug Flow", "BUG", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Bug Flow");

        verify(workflowDefinitionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createWorkflowDefinitionTranslatesDbDuplicateNameConstraint() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Bug Flow"))
                .thenReturn(Optional.empty());

        var cause = new ConstraintViolationException(
                "duplicate key", new SQLException(),
                "workflow_definition_tenant_id_name_key");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint", cause));

        assertThatThrownBy(() -> service.createWorkflowDefinition(
                TENANT_ID, "Bug Flow", "BUG", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Bug Flow");

        verifyNoInteractions(auditService);
    }

    @Test
    void createWorkflowDefinitionRethrowsUnrelatedIntegrityViolation() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Bug Flow"))
                .thenReturn(Optional.empty());

        var cause = new ConstraintViolationException(
                "other error", new SQLException(),
                "some_other_constraint");
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenThrow(new DataIntegrityViolationException("other violation", cause));

        assertThatThrownBy(() -> service.createWorkflowDefinition(
                TENANT_ID, "Bug Flow", "BUG", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void createWorkflowDefinitionRecordsAuditEvent() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Task Flow"))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.createWorkflowDefinition(
                TENANT_ID, "Task Flow", "TASK", null);

        verify(auditService).recordEvent(
                eq(TENANT_ID),
                eq("WORKFLOW_DEFINITION"),
                eq(result.getId()),
                eq("CREATED"),
                eq(null),
                eq("ADMIN_API"),
                eq(null),
                eq("Task Flow"));
    }
}
