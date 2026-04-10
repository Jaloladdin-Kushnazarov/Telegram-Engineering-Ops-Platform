package com.engops.platform.admin;

import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
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

    // ========== deleteWorkflowDefinition tests ==========

    @Test
    void deleteWorkflowDefinitionThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.deleteWorkflowDefinition(null, DEF_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deleteWorkflowDefinitionThrowsIllegalArgumentWhenDefinitionIdNull() {
        assertThatThrownBy(() -> facade.deleteWorkflowDefinition(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deleteWorkflowDefinitionDelegatesToCommandService() {
        facade.deleteWorkflowDefinition(TENANT_ID, DEF_ID);

        verify(commandService).deleteWorkflowDefinition(TENANT_ID, DEF_ID);
    }

    // ========== createChatBinding tests ==========

    @Test
    void createChatBindingThrowsIllegalArgumentWhenTenantIdNull() {
        var request = new CreateChatBindingRequest(-1001234567890L, "Chat", "MAIN_GROUP");

        assertThatThrownBy(() -> facade.createChatBinding(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void createChatBindingThrowsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.createChatBinding(TENANT_ID, (CreateChatBindingRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void createChatBindingThrowsIllegalArgumentWhenChatIdNull() {
        var request = new CreateChatBindingRequest(null, "Chat", "MAIN_GROUP");

        assertThatThrownBy(() -> facade.createChatBinding(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatId");

        verifyNoInteractions(commandService);
    }

    @Test
    void createChatBindingThrowsIllegalArgumentWhenBindingTypeBlank() {
        var request = new CreateChatBindingRequest(-1001234567890L, "Chat", "  ");

        assertThatThrownBy(() -> facade.createChatBinding(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindingType");

        verifyNoInteractions(commandService);
    }

    @Test
    void createChatBindingThrowsIllegalArgumentWhenBindingTypeInvalid() {
        var request = new CreateChatBindingRequest(-1001234567890L, "Chat", "PRIVATE_CHAT");

        assertThatThrownBy(() -> facade.createChatBinding(TENANT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindingType");

        verifyNoInteractions(commandService);
    }

    @Test
    void createChatBindingDelegatesToCommandService() {
        var request = new CreateChatBindingRequest(-1001234567890L, "Dev Chat", "MAIN_GROUP");

        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, "Dev Chat");
        binding.setBindingType(ChatBindingType.MAIN_GROUP);

        when(commandService.createChatBinding(
                TENANT_ID, -1001234567890L, "Dev Chat", ChatBindingType.MAIN_GROUP))
                .thenReturn(binding);

        var result = facade.createChatBinding(TENANT_ID, request);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.chatBindingId()).isEqualTo(binding.getId());
        assertThat(result.chatId()).isEqualTo(-1001234567890L);
        assertThat(result.chatTitle()).isEqualTo("Dev Chat");
        assertThat(result.bindingType()).isEqualTo("MAIN_GROUP");
        assertThat(result.active()).isTrue();

        verify(commandService).createChatBinding(
                TENANT_ID, -1001234567890L, "Dev Chat", ChatBindingType.MAIN_GROUP);
    }

    // ========== updateChatBinding tests ==========

    private static final UUID CB_ID = UUID.fromString("55555555-5555-5555-5555-555555555551");

    private UpdateChatBindingRequest updateChatBindingRequest() {
        return new UpdateChatBindingRequest();
    }

    @Test
    void updateChatBindingThrowsIllegalArgumentWhenTenantIdNull() {
        var request = updateChatBindingRequest();
        request.setChatTitle("Title");

        assertThatThrownBy(() -> facade.updateChatBinding(null, CB_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateChatBindingThrowsIllegalArgumentWhenChatBindingIdNull() {
        var request = updateChatBindingRequest();
        request.setChatTitle("Title");

        assertThatThrownBy(() -> facade.updateChatBinding(TENANT_ID, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatBindingId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateChatBindingThrowsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.updateChatBinding(TENANT_ID, CB_ID, (UpdateChatBindingRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateChatBindingThrowsIllegalArgumentWhenNoFieldProvided() {
        var request = updateChatBindingRequest();

        assertThatThrownBy(() -> facade.updateChatBinding(TENANT_ID, CB_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kamida bitta");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateChatBindingThrowsIllegalArgumentWhenBindingTypeInvalid() {
        var request = updateChatBindingRequest();
        request.setBindingType("PRIVATE_CHAT");

        assertThatThrownBy(() -> facade.updateChatBinding(TENANT_ID, CB_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindingType");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateChatBindingDelegatesWithChatTitleOnly() {
        var request = updateChatBindingRequest();
        request.setChatTitle("New Title");

        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, "New Title");
        binding.setBindingType(ChatBindingType.MAIN_GROUP);

        when(commandService.updateChatBinding(
                eq(TENANT_ID), eq(CB_ID),
                eq("New Title"), eq(true),
                eq(null), eq(false)))
                .thenReturn(binding);

        var result = facade.updateChatBinding(TENANT_ID, CB_ID, request);

        assertThat(result.chatTitle()).isEqualTo("New Title");
        assertThat(result.bindingType()).isEqualTo("MAIN_GROUP");
    }

    @Test
    void updateChatBindingDelegatesWithBindingTypeOnly() {
        var request = updateChatBindingRequest();
        request.setBindingType("NOTIFICATION_GROUP");

        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, "Chat");
        binding.setBindingType(ChatBindingType.NOTIFICATION_GROUP);

        when(commandService.updateChatBinding(
                eq(TENANT_ID), eq(CB_ID),
                eq(null), eq(false),
                eq(ChatBindingType.NOTIFICATION_GROUP), eq(true)))
                .thenReturn(binding);

        var result = facade.updateChatBinding(TENANT_ID, CB_ID, request);

        assertThat(result.bindingType()).isEqualTo("NOTIFICATION_GROUP");
    }

    @Test
    void updateChatBindingDelegatesWithBothFields() {
        var request = updateChatBindingRequest();
        request.setChatTitle("Updated");
        request.setBindingType("MAIN_GROUP");

        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, "Updated");
        binding.setBindingType(ChatBindingType.MAIN_GROUP);

        when(commandService.updateChatBinding(
                eq(TENANT_ID), eq(CB_ID),
                eq("Updated"), eq(true),
                eq(ChatBindingType.MAIN_GROUP), eq(true)))
                .thenReturn(binding);

        var result = facade.updateChatBinding(TENANT_ID, CB_ID, request);

        assertThat(result.chatTitle()).isEqualTo("Updated");
        assertThat(result.bindingType()).isEqualTo("MAIN_GROUP");
    }

    @Test
    void updateChatBindingExplicitNullChatTitleClearSemantics() {
        var request = updateChatBindingRequest();
        request.setChatTitle(null);

        TelegramChatBinding binding = new TelegramChatBinding(TENANT_ID, -1001234567890L, null);
        binding.setBindingType(ChatBindingType.MAIN_GROUP);

        when(commandService.updateChatBinding(
                eq(TENANT_ID), eq(CB_ID),
                eq(null), eq(true),
                eq(null), eq(false)))
                .thenReturn(binding);

        facade.updateChatBinding(TENANT_ID, CB_ID, request);

        verify(commandService).updateChatBinding(
                eq(TENANT_ID), eq(CB_ID),
                eq(null), eq(true),
                eq(null), eq(false));
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

    // ========== updateRoutingRule tests ==========

    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");

    private UpdateRoutingRuleRequest updateRuleRequest() {
        return new UpdateRoutingRuleRequest();
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenTenantIdNull() {
        var request = updateRuleRequest();
        request.setName("Name");

        assertThatThrownBy(() -> facade.updateRoutingRule(null, RULE_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenRuleIdNull() {
        var request = updateRuleRequest();
        request.setName("Name");

        assertThatThrownBy(() -> facade.updateRoutingRule(TENANT_ID, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleId");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenRequestNull() {
        assertThatThrownBy(() -> facade.updateRoutingRule(TENANT_ID, RULE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenNoFieldProvided() {
        var request = updateRuleRequest();

        assertThatThrownBy(() -> facade.updateRoutingRule(TENANT_ID, RULE_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kamida bitta");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenPriorityProvidedButNull() {
        var request = updateRuleRequest();
        request.setPriority(null);

        assertThatThrownBy(() -> facade.updateRoutingRule(TENANT_ID, RULE_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleThrowsIllegalArgumentWhenNameProvidedButBlank() {
        var request = updateRuleRequest();
        request.setName("   ");

        assertThatThrownBy(() -> facade.updateRoutingRule(TENANT_ID, RULE_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        verifyNoInteractions(commandService);
    }

    @Test
    void updateRoutingRuleDelegatesWithNameOnly() {
        var request = updateRuleRequest();
        request.setName("New Name");

        RoutingRule rule = new RoutingRule(TENANT_ID, "New Name", "BUG");
        rule.setPriority(10);

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq("New Name"), eq(true),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(false)))
                .thenReturn(rule);

        var result = facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.ruleId()).isEqualTo(rule.getId());
    }

    @Test
    void updateRoutingRuleDelegatesWithPriorityOnly() {
        var request = updateRuleRequest();
        request.setPriority(50);

        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");
        rule.setPriority(50);

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(50), eq(true),
                eq(null), eq(false),
                eq(null), eq(false)))
                .thenReturn(rule);

        var result = facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        assertThat(result.priority()).isEqualTo(50);
    }

    @Test
    void updateRoutingRuleDelegatesWithTargetTopicBindingIdOnly() {
        UUID topicId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var request = updateRuleRequest();
        request.setTargetTopicBindingId(topicId);

        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");
        rule.setTargetTopicBindingId(topicId);

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(topicId), eq(true),
                eq(null), eq(false)))
                .thenReturn(rule);

        var result = facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        assertThat(result.targetTopicBindingId()).isEqualTo(topicId);
    }

    @Test
    void updateRoutingRuleExplicitNullTopicBindingIdClearSemantics() {
        var request = updateRuleRequest();
        request.setTargetTopicBindingId(null);

        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(true),
                eq(null), eq(false)))
                .thenReturn(rule);

        facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        verify(commandService).updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(true),
                eq(null), eq(false));
    }

    @Test
    void updateRoutingRuleDelegatesWithConditionExpressionOnly() {
        var request = updateRuleRequest();
        request.setConditionExpression("severity == LOW");

        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");
        rule.setConditionExpression("severity == LOW");

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(false),
                eq("severity == LOW"), eq(true)))
                .thenReturn(rule);

        var result = facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        assertThat(result.conditionExpression()).isEqualTo("severity == LOW");
    }

    @Test
    void updateRoutingRuleExplicitNullConditionExpressionClearSemantics() {
        var request = updateRuleRequest();
        request.setConditionExpression(null);

        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");

        when(commandService.updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(true)))
                .thenReturn(rule);

        facade.updateRoutingRule(TENANT_ID, RULE_ID, request);

        verify(commandService).updateRoutingRule(
                eq(TENANT_ID), eq(RULE_ID),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(false),
                eq(null), eq(true));
    }

    // ========== activateRoutingRule tests ==========

    @Test
    void activateRoutingRuleThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.activateRoutingRule(null, RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void activateRoutingRuleThrowsIllegalArgumentWhenRuleIdNull() {
        assertThatThrownBy(() -> facade.activateRoutingRule(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleId");

        verifyNoInteractions(commandService);
    }

    @Test
    void activateRoutingRuleDelegatesToCommandService() {
        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");
        rule.setPriority(10);

        when(commandService.activateRoutingRule(TENANT_ID, RULE_ID)).thenReturn(rule);

        var result = facade.activateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.ruleId()).isEqualTo(rule.getId());
        assertThat(result.name()).isEqualTo("Rule");
        assertThat(result.active()).isTrue();

        verify(commandService).activateRoutingRule(TENANT_ID, RULE_ID);
    }

    // ========== deactivateRoutingRule tests ==========

    @Test
    void deactivateRoutingRuleThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.deactivateRoutingRule(null, RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deactivateRoutingRuleThrowsIllegalArgumentWhenRuleIdNull() {
        assertThatThrownBy(() -> facade.deactivateRoutingRule(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deactivateRoutingRuleDelegatesToCommandService() {
        RoutingRule rule = new RoutingRule(TENANT_ID, "Rule", "BUG");
        rule.setPriority(5);
        rule.setActive(false);

        when(commandService.deactivateRoutingRule(TENANT_ID, RULE_ID)).thenReturn(rule);

        var result = facade.deactivateRoutingRule(TENANT_ID, RULE_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.ruleId()).isEqualTo(rule.getId());
        assertThat(result.active()).isFalse();

        verify(commandService).deactivateRoutingRule(TENANT_ID, RULE_ID);
    }

    // ========== deleteRoutingRule tests ==========

    @Test
    void deleteRoutingRuleThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.deleteRoutingRule(null, RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deleteRoutingRuleThrowsIllegalArgumentWhenRuleIdNull() {
        assertThatThrownBy(() -> facade.deleteRoutingRule(TENANT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleId");

        verifyNoInteractions(commandService);
    }

    @Test
    void deleteRoutingRuleDelegatesToCommandService() {
        facade.deleteRoutingRule(TENANT_ID, RULE_ID);

        verify(commandService).deleteRoutingRule(TENANT_ID, RULE_ID);
    }
}
