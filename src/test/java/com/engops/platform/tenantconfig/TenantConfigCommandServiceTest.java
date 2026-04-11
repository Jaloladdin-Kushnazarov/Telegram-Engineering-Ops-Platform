package com.engops.platform.tenantconfig;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.RoutingRuleRepository;
import com.engops.platform.tenantconfig.repository.TelegramChatBindingRepository;
import com.engops.platform.tenantconfig.repository.TelegramTopicBindingRepository;
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
    private final RoutingRuleRepository routingRuleRepository =
            mock(RoutingRuleRepository.class);
    private final TelegramChatBindingRepository telegramChatBindingRepository =
            mock(TelegramChatBindingRepository.class);
    private final TelegramTopicBindingRepository telegramTopicBindingRepository =
            mock(TelegramTopicBindingRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantConfigCommandService service =
            new TenantConfigCommandService(tenantRepository, workflowDefinitionRepository,
                    routingRuleRepository, telegramChatBindingRepository,
                    telegramTopicBindingRepository, auditService);

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

    // ========== updateWorkflowDefinition tests ==========

    @Test
    void updateBothNameAndDescription() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222221");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Old Name", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "New Name"))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.updateWorkflowDefinition(
                TENANT_ID, defId, "New Name", true, "New desc", true);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New desc");

        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("WORKFLOW_DEFINITION"), eq(existing.getId()),
                eq("UPDATED"), eq(null), eq("ADMIN_API"),
                eq("Old Name"), eq("New Name | New desc"));
    }

    @Test
    void updateOnlyDescription() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Kept Name", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.updateWorkflowDefinition(
                TENANT_ID, defId, null, false, "New desc", true);

        assertThat(result.getName()).isEqualTo("Kept Name");
        assertThat(result.getDescription()).isEqualTo("New desc");

        verify(workflowDefinitionRepository, never()).findByTenantIdAndName(any(), any());
    }

    @Test
    void updateOnlyName() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222223");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Old Name", "BUG");
        existing.setDescription("Kept desc");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "New Name"))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.updateWorkflowDefinition(
                TENANT_ID, defId, "New Name", true, null, false);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("Kept desc");
    }

    @Test
    void updateClearsDescriptionWhenExplicitNull() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222224");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");
        existing.setDescription("Old desc");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.updateWorkflowDefinition(
                TENANT_ID, defId, null, false, null, true);

        assertThat(result.getDescription()).isNull();
    }

    @Test
    void updateUnchangedNameSkipsDuplicateCheck() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222225");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Same Name", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updateWorkflowDefinition(
                TENANT_ID, defId, "Same Name", true, null, false);

        verify(workflowDefinitionRepository, never()).findByTenantIdAndName(any(), any());
    }

    @Test
    void updateThrowsResourceNotFoundWhenTenantMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222226");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateWorkflowDefinition(
                TENANT_ID, defId, "Name", true, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateThrowsResourceNotFoundWhenDefinitionMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222227");
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateWorkflowDefinition(
                TENANT_ID, defId, "Name", true, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateThrowsBusinessRuleForDuplicateName() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222228");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Old Name", "BUG");
        WorkflowDefinition duplicate = mock(WorkflowDefinition.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.findByTenantIdAndName(TENANT_ID, "Taken Name"))
                .thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.updateWorkflowDefinition(
                TENANT_ID, defId, "Taken Name", true, null, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Taken Name");

        verify(workflowDefinitionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== activateWorkflowDefinition tests ==========

    @Test
    void activateWorkflowDefinitionSuccess() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222231");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.activateWorkflowDefinition(TENANT_ID, defId);

        assertThat(result.isActive()).isTrue();
        verify(workflowDefinitionRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("WORKFLOW_DEFINITION"), eq(existing.getId()),
                eq("ACTIVATED"), eq(null), eq("ADMIN_API"), eq("false"), eq("true"));
    }

    @Test
    void activateAlreadyActiveIsIdempotent() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222232");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));

        WorkflowDefinition result = service.activateWorkflowDefinition(TENANT_ID, defId);

        assertThat(result.isActive()).isTrue();
        verify(workflowDefinitionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateThrowsResourceNotFoundWhenTenantMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222233");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void activateThrowsResourceNotFoundWhenDefinitionMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222234");
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    // ========== deactivateWorkflowDefinition tests ==========

    @Test
    void deactivateWorkflowDefinitionSuccess() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222241");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowDefinition result = service.deactivateWorkflowDefinition(TENANT_ID, defId);

        assertThat(result.isActive()).isFalse();
        verify(workflowDefinitionRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("WORKFLOW_DEFINITION"), eq(existing.getId()),
                eq("DEACTIVATED"), eq(null), eq("ADMIN_API"), eq("true"), eq("false"));
    }

    @Test
    void deactivateAlreadyInactiveIsIdempotent() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222242");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));

        WorkflowDefinition result = service.deactivateWorkflowDefinition(TENANT_ID, defId);

        assertThat(result.isActive()).isFalse();
        verify(workflowDefinitionRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateThrowsResourceNotFoundWhenTenantMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222243");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateThrowsResourceNotFoundWhenDefinitionMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222244");
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    // ========== deleteWorkflowDefinition tests ==========

    @Test
    void deleteWorkflowDefinitionSuccess() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222241");
        Tenant tenant = mock(Tenant.class);
        WorkflowDefinition existing = new WorkflowDefinition(TENANT_ID, "Bug Flow", "BUG");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.of(existing));

        service.deleteWorkflowDefinition(TENANT_ID, defId);

        verify(workflowDefinitionRepository).delete(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("WORKFLOW_DEFINITION"), eq(defId),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                eq("Bug Flow | type=BUG"), eq(null));
    }

    @Test
    void deleteWorkflowDefinitionThrowsResourceNotFoundWhenTenantMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222242");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(workflowDefinitionRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deleteWorkflowDefinitionThrowsResourceNotFoundWhenDefinitionMissing() {
        UUID defId = UUID.fromString("22222222-2222-2222-2222-222222222243");
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(workflowDefinitionRepository.findByTenantIdAndId(TENANT_ID, defId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWorkflowDefinition(TENANT_ID, defId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(workflowDefinitionRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    // ========== createChatBinding tests ==========

    @Test
    void createChatBindingSuccess() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByTenantIdAndChatId(TENANT_ID, -1001234567890L))
                .thenReturn(Optional.empty());
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.createChatBinding(
                TENANT_ID, -1001234567890L, "Dev Team Chat", ChatBindingType.MAIN_GROUP);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getChatId()).isEqualTo(-1001234567890L);
        assertThat(result.getChatTitle()).isEqualTo("Dev Team Chat");
        assertThat(result.getBindingType()).isEqualTo(ChatBindingType.MAIN_GROUP);
        assertThat(result.isActive()).isTrue();

        verify(telegramChatBindingRepository).save(any(TelegramChatBinding.class));
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), any(String.class));
    }

    @Test
    void createChatBindingWithNullTitle() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByTenantIdAndChatId(TENANT_ID, -1001234567891L))
                .thenReturn(Optional.empty());
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.createChatBinding(
                TENANT_ID, -1001234567891L, null, ChatBindingType.NOTIFICATION_GROUP);

        assertThat(result.getChatTitle()).isNull();
        assertThat(result.getBindingType()).isEqualTo(ChatBindingType.NOTIFICATION_GROUP);
    }

    @Test
    void createChatBindingThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createChatBinding(
                TENANT_ID, -1001234567890L, "Chat", ChatBindingType.MAIN_GROUP))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createChatBindingThrowsBusinessRuleWhenDuplicate() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = mock(TelegramChatBinding.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByTenantIdAndChatId(TENANT_ID, -1001234567890L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createChatBinding(
                TENANT_ID, -1001234567890L, "Chat", ChatBindingType.MAIN_GROUP))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chatId");

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createChatBindingRecordsAuditEvent() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByTenantIdAndChatId(TENANT_ID, -100999L))
                .thenReturn(Optional.empty());
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.createChatBinding(
                TENANT_ID, -100999L, "Ops Channel", ChatBindingType.NOTIFICATION_GROUP);

        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), eq("-100999 | NOTIFICATION_GROUP | Ops Channel"));
    }

    // ========== updateChatBinding tests ==========

    private static final UUID CHAT_BINDING_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");

    private TelegramChatBinding existingChatBinding() {
        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, "Old Title");
        binding.setBindingType(ChatBindingType.MAIN_GROUP);
        return binding;
    }

    @Test
    void updateChatBindingOnlyChatTitle() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                "New Title", true,
                null, false);

        assertThat(result.getChatTitle()).isEqualTo("New Title");
        assertThat(result.getBindingType()).isEqualTo(ChatBindingType.MAIN_GROUP);
    }

    @Test
    void updateChatBindingOnlyBindingType() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                null, false,
                ChatBindingType.NOTIFICATION_GROUP, true);

        assertThat(result.getChatTitle()).isEqualTo("Old Title");
        assertThat(result.getBindingType()).isEqualTo(ChatBindingType.NOTIFICATION_GROUP);
    }

    @Test
    void updateChatBindingBothFields() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                "Updated Title", true,
                ChatBindingType.NOTIFICATION_GROUP, true);

        assertThat(result.getChatTitle()).isEqualTo("Updated Title");
        assertThat(result.getBindingType()).isEqualTo(ChatBindingType.NOTIFICATION_GROUP);

        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(existing.getId()),
                eq("UPDATED"), eq(null), eq("ADMIN_API"),
                eq("MAIN_GROUP | Old Title"), eq("NOTIFICATION_GROUP | Updated Title"));
    }

    @Test
    void updateChatBindingExplicitNullChatTitleClearsField() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                null, true,
                null, false);

        assertThat(result.getChatTitle()).isNull();
    }

    @Test
    void updateChatBindingBlankChatTitleClearsField() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                "   ", true,
                null, false);

        assertThat(result.getChatTitle()).isNull();
    }

    @Test
    void updateChatBindingThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                "Title", true, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateChatBindingThrowsResourceNotFoundWhenBindingMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateChatBinding(
                TENANT_ID, CHAT_BINDING_ID,
                "Title", true, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    // ========== activateChatBinding tests ==========

    @Test
    void activateChatBindingSuccess() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.activateChatBinding(TENANT_ID, CHAT_BINDING_ID);

        assertThat(result.isActive()).isTrue();
        verify(telegramChatBindingRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(existing.getId()),
                eq("ACTIVATED"), eq(null), eq("ADMIN_API"), eq("false"), eq("true"));
    }

    @Test
    void activateChatBindingAlreadyActiveIsIdempotent() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        TelegramChatBinding result = service.activateChatBinding(TENANT_ID, CHAT_BINDING_ID);

        assertThat(result.isActive()).isTrue();
        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateChatBindingThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateChatBindingThrowsResourceNotFoundWhenBindingMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== deactivateChatBinding tests ==========

    @Test
    void deactivateChatBindingSuccess() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramChatBindingRepository.save(any(TelegramChatBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelegramChatBinding result = service.deactivateChatBinding(TENANT_ID, CHAT_BINDING_ID);

        assertThat(result.isActive()).isFalse();
        verify(telegramChatBindingRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(existing.getId()),
                eq("DEACTIVATED"), eq(null), eq("ADMIN_API"), eq("true"), eq("false"));
    }

    @Test
    void deactivateChatBindingAlreadyInactiveIsIdempotent() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        TelegramChatBinding result = service.deactivateChatBinding(TENANT_ID, CHAT_BINDING_ID);

        assertThat(result.isActive()).isFalse();
        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateChatBindingThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateChatBindingThrowsResourceNotFoundWhenBindingMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== deleteChatBinding tests ==========

    @Test
    void deleteChatBindingSuccess() {
        Tenant tenant = mock(Tenant.class);
        TelegramChatBinding existing = existingChatBinding();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        service.deleteChatBinding(TENANT_ID, CHAT_BINDING_ID);

        verify(telegramChatBindingRepository).delete(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("CHAT_BINDING"), eq(CHAT_BINDING_ID),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                any(String.class), eq(null));
    }

    @Test
    void deleteChatBindingThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deleteChatBindingThrowsResourceNotFoundWhenBindingMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramChatBindingRepository.findByIdAndTenantId(CHAT_BINDING_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteChatBinding(TENANT_ID, CHAT_BINDING_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(telegramChatBindingRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    // ========== createRoutingRule tests ==========

    @Test
    void createRoutingRuleSuccess() {
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Tenant tenant = mock(Tenant.class);
        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(topicBindingId, TENANT_ID))
                .thenReturn(Optional.of(topicBinding));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.createRoutingRule(
                TENANT_ID, "Route Bugs", "BUG", 10, topicBindingId, null);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getName()).isEqualTo("Route Bugs");
        assertThat(result.getWorkItemType()).isEqualTo("BUG");
        assertThat(result.getPriority()).isEqualTo(10);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(topicBindingId);
        assertThat(result.isActive()).isTrue();

        verify(routingRuleRepository).save(any(RoutingRule.class));
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("ROUTING_RULE"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"), eq(null), eq("Route Bugs"));
    }

    @Test
    void createRoutingRuleWithNullTopicBinding() {
        Tenant tenant = mock(Tenant.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.createRoutingRule(
                TENANT_ID, "Catch All", "INCIDENT", 5, null, null);

        assertThat(result.getTargetTopicBindingId()).isNull();

        verify(telegramTopicBindingRepository, never()).findByIdAndChatBinding_TenantId(any(), any());
    }

    @Test
    void createRoutingRuleThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRoutingRule(
                TENANT_ID, "Rule", "BUG", 10, null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createRoutingRuleRejectsInvalidTopicBinding() {
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444445");
        Tenant tenant = mock(Tenant.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(topicBindingId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRoutingRule(
                TENANT_ID, "Rule", "BUG", 10, topicBindingId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Topic binding");

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createRoutingRuleWithConditionExpression() {
        Tenant tenant = mock(Tenant.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.createRoutingRule(
                TENANT_ID, "Conditional", "TASK", 20, null, "severity == HIGH");

        assertThat(result.getConditionExpression()).isEqualTo("severity == HIGH");
    }

    // ========== updateRoutingRule tests ==========

    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");

    private RoutingRule existingRule() {
        RoutingRule rule = new RoutingRule(TENANT_ID, "Old Rule", "BUG");
        rule.setPriority(10);
        rule.setTargetTopicBindingId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        rule.setConditionExpression("severity == HIGH");
        return rule;
    }

    @Test
    void updateRoutingRuleAllFields() {
        UUID newTopicId = UUID.fromString("44444444-4444-4444-4444-444444444445");
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(newTopicId, TENANT_ID))
                .thenReturn(Optional.of(mock(TelegramTopicBinding.class)));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                "New Rule", true,
                20, true,
                newTopicId, true,
                "priority == LOW", true);

        assertThat(result.getName()).isEqualTo("New Rule");
        assertThat(result.getPriority()).isEqualTo(20);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(newTopicId);
        assertThat(result.getConditionExpression()).isEqualTo("priority == LOW");

        verify(routingRuleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("ROUTING_RULE"), eq(existing.getId()),
                eq("UPDATED"), eq(null), eq("ADMIN_API"),
                any(String.class), any(String.class));
    }

    @Test
    void updateRoutingRuleOnlyName() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                "Updated Name", true,
                null, false,
                null, false,
                null, false);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getPriority()).isEqualTo(10);
        assertThat(result.getTargetTopicBindingId()).isEqualTo(
                UUID.fromString("44444444-4444-4444-4444-444444444444"));
        assertThat(result.getConditionExpression()).isEqualTo("severity == HIGH");
    }

    @Test
    void updateRoutingRuleOnlyPriority() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                99, true,
                null, false,
                null, false);

        assertThat(result.getName()).isEqualTo("Old Rule");
        assertThat(result.getPriority()).isEqualTo(99);
    }

    @Test
    void updateRoutingRuleOnlyTargetTopicBindingId() {
        UUID newTopicId = UUID.fromString("44444444-4444-4444-4444-444444444446");
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(newTopicId, TENANT_ID))
                .thenReturn(Optional.of(mock(TelegramTopicBinding.class)));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                null, false,
                newTopicId, true,
                null, false);

        assertThat(result.getTargetTopicBindingId()).isEqualTo(newTopicId);
    }

    @Test
    void updateRoutingRuleExplicitNullTopicBindingClearsIt() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                null, false,
                null, true,
                null, false);

        assertThat(result.getTargetTopicBindingId()).isNull();
        verify(telegramTopicBindingRepository, never()).findByIdAndChatBinding_TenantId(any(), any());
    }

    @Test
    void updateRoutingRuleOnlyConditionExpression() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                null, false,
                null, false,
                "type == CRITICAL", true);

        assertThat(result.getConditionExpression()).isEqualTo("type == CRITICAL");
    }

    @Test
    void updateRoutingRuleExplicitNullConditionExpressionClearsIt() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                null, false,
                null, false,
                null, true);

        assertThat(result.getConditionExpression()).isNull();
    }

    @Test
    void updateRoutingRuleBlankConditionExpressionClearsIt() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false,
                null, false,
                null, false,
                "   ", true);

        assertThat(result.getConditionExpression()).isNull();
    }

    @Test
    void updateRoutingRuleThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                "Name", true, null, false, null, false, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateRoutingRuleThrowsResourceNotFoundWhenRuleMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                "Name", true, null, false, null, false, null, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateRoutingRuleRejectsInvalidTopicBinding() {
        UUID badTopicId = UUID.fromString("44444444-4444-4444-4444-444444444499");
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(badTopicId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                null, false, null, false,
                badTopicId, true,
                null, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Topic binding");

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateRoutingRuleUnchangedNameDoesNotCauseIssues() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.updateRoutingRule(
                TENANT_ID, RULE_ID,
                "Old Rule", true,
                null, false, null, false, null, false);

        assertThat(result.getName()).isEqualTo("Old Rule");
        verify(routingRuleRepository).save(existing);
    }

    // ========== activateRoutingRule tests ==========

    @Test
    void activateRoutingRuleSuccess() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.activateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.isActive()).isTrue();
        verify(routingRuleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("ROUTING_RULE"), eq(existing.getId()),
                eq("ACTIVATED"), eq(null), eq("ADMIN_API"), eq("false"), eq("true"));
    }

    @Test
    void activateRoutingRuleAlreadyActiveIsIdempotent() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        RoutingRule result = service.activateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.isActive()).isTrue();
        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateRoutingRuleThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateRoutingRuleThrowsResourceNotFoundWhenRuleMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== deactivateRoutingRule tests ==========

    @Test
    void deactivateRoutingRuleSuccess() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(routingRuleRepository.save(any(RoutingRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RoutingRule result = service.deactivateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.isActive()).isFalse();
        verify(routingRuleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("ROUTING_RULE"), eq(existing.getId()),
                eq("DEACTIVATED"), eq(null), eq("ADMIN_API"), eq("true"), eq("false"));
    }

    @Test
    void deactivateRoutingRuleAlreadyInactiveIsIdempotent() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();
        existing.setActive(false);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        RoutingRule result = service.deactivateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.isActive()).isFalse();
        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateRoutingRuleThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateRoutingRuleThrowsResourceNotFoundWhenRuleMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== deleteRoutingRule tests ==========

    @Test
    void deleteRoutingRuleSuccess() {
        Tenant tenant = mock(Tenant.class);
        RoutingRule existing = existingRule();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        service.deleteRoutingRule(TENANT_ID, RULE_ID);

        verify(routingRuleRepository).delete(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("ROUTING_RULE"), eq(RULE_ID),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                any(String.class), eq(null));
    }

    @Test
    void deleteRoutingRuleThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deleteRoutingRuleThrowsResourceNotFoundWhenRuleMissing() {
        Tenant tenant = mock(Tenant.class);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(routingRuleRepository.findByIdAndTenantId(RULE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRoutingRule(TENANT_ID, RULE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(routingRuleRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }
}
