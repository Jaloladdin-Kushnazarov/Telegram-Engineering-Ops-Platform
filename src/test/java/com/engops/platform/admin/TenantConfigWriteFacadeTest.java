package com.engops.platform.admin;

import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TenantConfigWriteFacade unit testlari.
 *
 * Tekshiruvlar:
 * - null tenantId
 * - null request
 * - blank name
 * - blank workItemType
 * - invalid workItemType
 * - happy path delegation
 */
class TenantConfigWriteFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final TenantConfigCommandService commandService =
            mock(TenantConfigCommandService.class);
    private final TenantConfigWriteFacade facade =
            new TenantConfigWriteFacade(commandService);

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        var request = new CreateWorkflowDefinitionRequest("Bug Flow", "BUG", null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenNameNull() {
        var request = new CreateWorkflowDefinitionRequest(null, "BUG", null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenNameBlank() {
        var request = new CreateWorkflowDefinitionRequest("   ", "BUG", null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemTypeNull() {
        var request = new CreateWorkflowDefinitionRequest("Bug Flow", null, null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemType");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemTypeBlank() {
        var request = new CreateWorkflowDefinitionRequest("Bug Flow", "  ", null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemType");

        verifyNoInteractions(commandService);
    }

    @Test
    void throwsIllegalArgumentWhenWorkItemTypeInvalid() {
        var request = new CreateWorkflowDefinitionRequest("Bug Flow", "FEATURE", null);

        assertThatThrownBy(() -> facade.createWorkflowDefinition(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemType")
                .hasMessageContaining("FEATURE");

        verifyNoInteractions(commandService);
    }

    @Test
    void delegatesToCommandServiceOnValidRequest() {
        var request = new CreateWorkflowDefinitionRequest("Bug Flow", "BUG", "Bug workflow");

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Bug Flow", "BUG");
        definition.setDescription("Bug workflow");

        when(commandService.createWorkflowDefinition(TENANT_ID, "Bug Flow", "BUG", "Bug workflow"))
                .thenReturn(definition);

        var result = facade.createWorkflowDefinition(TENANT_ID, request);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.definitionId()).isEqualTo(definition.getId());
        assertThat(result.name()).isEqualTo("Bug Flow");
        assertThat(result.workItemType()).isEqualTo("BUG");
        assertThat(result.description()).isEqualTo("Bug workflow");
        assertThat(result.active()).isTrue();

        verify(commandService).createWorkflowDefinition(TENANT_ID, "Bug Flow", "BUG", "Bug workflow");
    }

    @Test
    void delegatesWithNullDescription() {
        var request = new CreateWorkflowDefinitionRequest("Incident Flow", "INCIDENT", null);

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Incident Flow", "INCIDENT");

        when(commandService.createWorkflowDefinition(TENANT_ID, "Incident Flow", "INCIDENT", null))
                .thenReturn(definition);

        var result = facade.createWorkflowDefinition(TENANT_ID, request);

        assertThat(result.description()).isNull();

        verify(commandService).createWorkflowDefinition(TENANT_ID, "Incident Flow", "INCIDENT", null);
    }

    @Test
    void acceptsAllThreeValidWorkItemTypes() {
        for (String type : new String[]{"BUG", "INCIDENT", "TASK"}) {
            var request = new CreateWorkflowDefinitionRequest("Flow " + type, type, null);
            WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Flow " + type, type);

            when(commandService.createWorkflowDefinition(TENANT_ID, "Flow " + type, type, null))
                    .thenReturn(definition);

            var result = facade.createWorkflowDefinition(TENANT_ID, request);
            assertThat(result.workItemType()).isEqualTo(type);
        }
    }

    // ========== updateWorkflowDefinition tests ==========

    private static final UUID DEF_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");

    private UpdateWorkflowDefinitionRequest updateRequest() {
        return new UpdateWorkflowDefinitionRequest();
    }

    @Test
    void updateThrowsIllegalArgumentWhenTenantIdNull() {
        var request = updateRequest();
        request.setName("Name");

        assertThatThrownBy(() -> facade.updateWorkflowDefinition(null, DEF_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateThrowsIllegalArgumentWhenDefinitionIdNull() {
        var request = updateRequest();
        request.setName("Name");

        assertThatThrownBy(() -> facade.updateWorkflowDefinition(TENANT_ID, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateThrowsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateThrowsIllegalArgumentWhenNoFieldProvided() {
        var request = updateRequest();

        assertThatThrownBy(() -> facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kamida bitta");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateThrowsIllegalArgumentWhenNameProvidedButBlank() {
        var request = updateRequest();
        request.setName("   ");

        assertThatThrownBy(() -> facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateThrowsIllegalArgumentWhenNameProvidedButNull() {
        var request = updateRequest();
        request.setName(null);

        assertThatThrownBy(() -> facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateDelegatesWithBothFields() {
        var request = updateRequest();
        request.setName("Updated Flow");
        request.setDescription("New desc");

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Updated Flow", "BUG");
        definition.setDescription("New desc");

        when(commandService.updateWorkflowDefinition(
                eq(TENANT_ID), eq(DEF_ID),
                eq("Updated Flow"), eq(true), eq("New desc"), eq(true)))
                .thenReturn(definition);

        var result = facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request);

        assertThat(result.name()).isEqualTo("Updated Flow");
        assertThat(result.description()).isEqualTo("New desc");
    }

    @Test
    void updateDelegatesWithOnlyDescription() {
        var request = updateRequest();
        request.setDescription("New desc");

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Kept Name", "BUG");
        definition.setDescription("New desc");

        when(commandService.updateWorkflowDefinition(
                eq(TENANT_ID), eq(DEF_ID),
                eq(null), eq(false), eq("New desc"), eq(true)))
                .thenReturn(definition);

        var result = facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request);

        assertThat(result.name()).isEqualTo("Kept Name");
        assertThat(result.description()).isEqualTo("New desc");
    }

    @Test
    void updateDelegatesWithOnlyName() {
        var request = updateRequest();
        request.setName("New Name");

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "New Name", "BUG");

        when(commandService.updateWorkflowDefinition(
                eq(TENANT_ID), eq(DEF_ID),
                eq("New Name"), eq(true), eq(null), eq(false)))
                .thenReturn(definition);

        var result = facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request);

        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void updateDelegatesExplicitNullDescriptionAsClear() {
        var request = updateRequest();
        request.setDescription(null);

        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");

        when(commandService.updateWorkflowDefinition(
                eq(TENANT_ID), eq(DEF_ID),
                eq(null), eq(false), eq(null), eq(true)))
                .thenReturn(definition);

        facade.updateWorkflowDefinition(TENANT_ID, DEF_ID, request);

        verify(commandService).updateWorkflowDefinition(
                eq(TENANT_ID), eq(DEF_ID),
                eq(null), eq(false), eq(null), eq(true));
    }

    // ========== activateWorkflowDefinition tests ==========

    @Test
    void activateThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.activateWorkflowDefinition(null, DEF_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void activateThrowsIllegalArgumentWhenDefinitionIdNull() {
        assertThatThrownBy(() -> facade.activateWorkflowDefinition(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionId");

        verifyNoInteractions(commandService);
    }

    @Test
    void activateDelegatesToCommandService() {
        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");

        when(commandService.activateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenReturn(definition);

        var result = facade.activateWorkflowDefinition(TENANT_ID, DEF_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.active()).isTrue();
        verify(commandService).activateWorkflowDefinition(TENANT_ID, DEF_ID);
    }

    // ========== deactivateWorkflowDefinition tests ==========

    @Test
    void deactivateThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.deactivateWorkflowDefinition(null, DEF_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deactivateThrowsIllegalArgumentWhenDefinitionIdNull() {
        assertThatThrownBy(() -> facade.deactivateWorkflowDefinition(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deactivateDelegatesToCommandService() {
        WorkflowDefinition definition = new WorkflowDefinition(TENANT_ID, "Flow", "BUG");
        definition.setActive(false);

        when(commandService.deactivateWorkflowDefinition(TENANT_ID, DEF_ID))
                .thenReturn(definition);

        var result = facade.deactivateWorkflowDefinition(TENANT_ID, DEF_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.active()).isFalse();
        verify(commandService).deactivateWorkflowDefinition(TENANT_ID, DEF_ID);
    }

    // ========== createRoutingRule tests ==========

    @Test
    void createRoutingRuleThrowsIllegalArgumentWhenTenantIdNull() {
        var request = new CreateRoutingRuleRequest("Rule", "BUG", 10, null, null);

        assertThatThrownBy(() -> facade.createRoutingRule(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void createRoutingRuleThrowsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.createRoutingRule(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void createRoutingRuleThrowsIllegalArgumentWhenNameBlank() {
        var request = new CreateRoutingRuleRequest("  ", "BUG", 10, null, null);

        assertThatThrownBy(() -> facade.createRoutingRule(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void createRoutingRuleThrowsIllegalArgumentWhenWorkItemTypeInvalid() {
        var request = new CreateRoutingRuleRequest("Rule", "FEATURE", 10, null, null);

        assertThatThrownBy(() -> facade.createRoutingRule(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemType");

        verifyNoInteractions(commandService);
    }

    @Test
    void createRoutingRuleDelegatesToCommandService() {
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var request = new CreateRoutingRuleRequest("Route Bugs", "BUG", 10, topicBindingId, null);

        RoutingRule rule = new RoutingRule(TENANT_ID, "Route Bugs", "BUG");
        rule.setPriority(10);
        rule.setTargetTopicBindingId(topicBindingId);

        when(commandService.createRoutingRule(
                TENANT_ID, "Route Bugs", "BUG", 10, topicBindingId, null))
                .thenReturn(rule);

        var result = facade.createRoutingRule(TENANT_ID, request);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.ruleId()).isEqualTo(rule.getId());
        assertThat(result.name()).isEqualTo("Route Bugs");
        assertThat(result.workItemType()).isEqualTo("BUG");
        assertThat(result.priority()).isEqualTo(10);
        assertThat(result.targetTopicBindingId()).isEqualTo(topicBindingId);
        assertThat(result.active()).isTrue();

        verify(commandService).createRoutingRule(
                TENANT_ID, "Route Bugs", "BUG", 10, topicBindingId, null);
    }
}
