package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TenantConfigDetailsFacade unit testlari.
 *
 * Tekshiruvlar:
 * - success path: tenant + barcha summary countlar to'g'ri qaytariladi
 * - tenant not found: ResourceNotFoundException
 * - null tenantId: IllegalArgumentException
 * - count aggregation correctness
 * - topic binding count multiple chat bindinglar bo'ylab to'g'ri jamlanadi
 * - bo'sh listlar 0 count qaytaradi
 * - delegation verify qilinadi
 */
class TenantConfigDetailsFacadeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final TenantConfigQueryService tenantConfigQueryService =
            mock(TenantConfigQueryService.class);
    private final IdentityQueryService identityQueryService =
            mock(IdentityQueryService.class);
    private final TenantConfigDetailsFacade facade =
            new TenantConfigDetailsFacade(tenantConfigQueryService, identityQueryService);

    @Test
    void returnsCompactDetailsWithAllSections() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of(
                mock(Membership.class), mock(Membership.class), mock(Membership.class)));
        when(identityQueryService.listActiveMembers(TENANT_ID)).thenReturn(List.of(
                mock(Membership.class), mock(Membership.class)));
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of(
                mock(WorkflowDefinition.class), mock(WorkflowDefinition.class)));
        when(tenantConfigQueryService.listActiveWorkflowDefinitions(TENANT_ID)).thenReturn(List.of(
                mock(WorkflowDefinition.class)));
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID)).thenReturn(List.of(
                mock(RoutingRule.class), mock(RoutingRule.class), mock(RoutingRule.class)));
        when(tenantConfigQueryService.listActiveRoutingRules(TENANT_ID)).thenReturn(List.of(
                mock(RoutingRule.class), mock(RoutingRule.class)));
        when(tenantConfigQueryService.listActiveChatBindings(TENANT_ID)).thenReturn(List.of(chatBinding));
        when(tenantConfigQueryService.listActiveTopicBindings(chatBindingId)).thenReturn(List.of(
                mock(TelegramTopicBinding.class), mock(TelegramTopicBinding.class)));

        var result = facade.getDetails(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.name()).isEqualTo("Test Tenant");
        assertThat(result.slug()).isEqualTo("test-tenant");
        assertThat(result.timezone()).isEqualTo("Asia/Tashkent");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.totalMembershipCount()).isEqualTo(3);
        assertThat(result.activeMembershipCount()).isEqualTo(2);
        assertThat(result.totalWorkflowDefinitionCount()).isEqualTo(2);
        assertThat(result.activeWorkflowDefinitionCount()).isEqualTo(1);
        assertThat(result.totalRoutingRuleCount()).isEqualTo(3);
        assertThat(result.activeRoutingRuleCount()).isEqualTo(2);
        assertThat(result.activeChatBindingCount()).isEqualTo(1);
        assertThat(result.activeTopicBindingCount()).isEqualTo(2);
    }

    @Test
    void topicBindingCountSumsAcrossMultipleChatBindings() {
        Tenant tenant = mockTenant();
        UUID cb1Id = UUID.fromString("88888888-8888-8888-8888-888888888881");
        UUID cb2Id = UUID.fromString("88888888-8888-8888-8888-888888888882");
        TelegramChatBinding cb1 = mock(TelegramChatBinding.class);
        TelegramChatBinding cb2 = mock(TelegramChatBinding.class);
        when(cb1.getId()).thenReturn(cb1Id);
        when(cb2.getId()).thenReturn(cb2Id);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());
        when(identityQueryService.listActiveMembers(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveChatBindings(TENANT_ID)).thenReturn(List.of(cb1, cb2));
        when(tenantConfigQueryService.listActiveTopicBindings(cb1Id)).thenReturn(List.of(
                mock(TelegramTopicBinding.class), mock(TelegramTopicBinding.class),
                mock(TelegramTopicBinding.class)));
        when(tenantConfigQueryService.listActiveTopicBindings(cb2Id)).thenReturn(List.of(
                mock(TelegramTopicBinding.class)));

        var result = facade.getDetails(TENANT_ID);

        assertThat(result.activeChatBindingCount()).isEqualTo(2);
        assertThat(result.activeTopicBindingCount()).isEqualTo(4);

        verify(tenantConfigQueryService).listActiveTopicBindings(cb1Id);
        verify(tenantConfigQueryService).listActiveTopicBindings(cb2Id);
    }

    @Test
    void returnsZeroCountsWhenNoConfigurationExists() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());
        when(identityQueryService.listActiveMembers(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveChatBindings(TENANT_ID)).thenReturn(List.of());

        var result = facade.getDetails(TENANT_ID);

        assertThat(result.totalMembershipCount()).isZero();
        assertThat(result.activeMembershipCount()).isZero();
        assertThat(result.totalWorkflowDefinitionCount()).isZero();
        assertThat(result.activeWorkflowDefinitionCount()).isZero();
        assertThat(result.totalRoutingRuleCount()).isZero();
        assertThat(result.activeRoutingRuleCount()).isZero();
        assertThat(result.activeChatBindingCount()).isZero();
        assertThat(result.activeTopicBindingCount()).isZero();
    }

    @Test
    void throwsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(identityQueryService);
        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getDetails(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void verifyDelegationToCorrectQueryServices() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());
        when(identityQueryService.listActiveMembers(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveChatBindings(TENANT_ID)).thenReturn(List.of());

        facade.getDetails(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService).listAllWorkflowDefinitions(TENANT_ID);
        verify(tenantConfigQueryService).listActiveWorkflowDefinitions(TENANT_ID);
        verify(tenantConfigQueryService).listAllRoutingRules(TENANT_ID);
        verify(tenantConfigQueryService).listActiveRoutingRules(TENANT_ID);
        verify(tenantConfigQueryService).listActiveChatBindings(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);

        verify(identityQueryService).listAllMembers(TENANT_ID);
        verify(identityQueryService).listActiveMembers(TENANT_ID);
        verifyNoMoreInteractions(identityQueryService);
    }

    // ========== getWorkflowDefinitions tests ==========

    @Test
    void workflowDefinitionsReturnsCorrectItemList() {
        Tenant tenant = mockTenant();
        UUID defId1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
        UUID defId2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        WorkflowDefinition wd1 = mock(WorkflowDefinition.class);
        when(wd1.getId()).thenReturn(defId1);
        when(wd1.getName()).thenReturn("Bug Flow");
        when(wd1.getWorkItemType()).thenReturn("BUG");
        when(wd1.getDescription()).thenReturn("Bug workflow");
        when(wd1.isActive()).thenReturn(true);
        when(wd1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-02-01T10:00:00Z"));

        WorkflowDefinition wd2 = mock(WorkflowDefinition.class);
        when(wd2.getId()).thenReturn(defId2);
        when(wd2.getName()).thenReturn("Incident Flow");
        when(wd2.getWorkItemType()).thenReturn("INCIDENT");
        when(wd2.getDescription()).thenReturn(null);
        when(wd2.isActive()).thenReturn(false);
        when(wd2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-02-10T12:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID))
                .thenReturn(List.of(wd1, wd2));

        var result = facade.getWorkflowDefinitions(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        var item1 = result.items().get(0);
        assertThat(item1.definitionId()).isEqualTo(defId1);
        assertThat(item1.name()).isEqualTo("Bug Flow");
        assertThat(item1.workItemType()).isEqualTo("BUG");
        assertThat(item1.description()).isEqualTo("Bug workflow");
        assertThat(item1.active()).isTrue();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-02-01T10:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.definitionId()).isEqualTo(defId2);
        assertThat(item2.name()).isEqualTo("Incident Flow");
        assertThat(item2.workItemType()).isEqualTo("INCIDENT");
        assertThat(item2.description()).isNull();
        assertThat(item2.active()).isFalse();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-02-10T12:00:00Z"));
    }

    @Test
    void workflowDefinitionsReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID))
                .thenReturn(List.of());

        var result = facade.getWorkflowDefinitions(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void workflowDefinitionsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getWorkflowDefinitions(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllWorkflowDefinitions(TENANT_ID);
    }

    @Test
    void workflowDefinitionsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getWorkflowDefinitions(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void workflowDefinitionsDelegatesToTenantConfigQueryServiceOnly() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID))
                .thenReturn(List.of());

        facade.getWorkflowDefinitions(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService).listAllWorkflowDefinitions(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getRoutingRules tests ==========

    @Test
    void routingRulesReturnsCorrectItemListOrderedByPriorityDesc() {
        Tenant tenant = mockTenant();
        UUID ruleId1 = UUID.fromString("33333333-3333-3333-3333-333333333331");
        UUID ruleId2 = UUID.fromString("33333333-3333-3333-3333-333333333332");
        UUID topicBindingId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        RoutingRule rule1 = mock(RoutingRule.class);
        when(rule1.getId()).thenReturn(ruleId1);
        when(rule1.getName()).thenReturn("Route Bugs");
        when(rule1.getWorkItemType()).thenReturn("BUG");
        when(rule1.getPriority()).thenReturn(10);
        when(rule1.getTargetTopicBindingId()).thenReturn(topicBindingId);
        when(rule1.isActive()).thenReturn(true);
        when(rule1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-01T10:00:00Z"));

        RoutingRule rule2 = mock(RoutingRule.class);
        when(rule2.getId()).thenReturn(ruleId2);
        when(rule2.getName()).thenReturn("Route Incidents");
        when(rule2.getWorkItemType()).thenReturn("INCIDENT");
        when(rule2.getPriority()).thenReturn(20);
        when(rule2.getTargetTopicBindingId()).thenReturn(null);
        when(rule2.isActive()).thenReturn(false);
        when(rule2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-05T12:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID))
                .thenReturn(List.of(rule1, rule2));

        var result = facade.getRoutingRules(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // priority DESC — rule2 (20) birinchi, rule1 (10) ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.ruleId()).isEqualTo(ruleId2);
        assertThat(item1.name()).isEqualTo("Route Incidents");
        assertThat(item1.workItemType()).isEqualTo("INCIDENT");
        assertThat(item1.priority()).isEqualTo(20);
        assertThat(item1.targetTopicBindingId()).isNull();
        assertThat(item1.active()).isFalse();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-05T12:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.ruleId()).isEqualTo(ruleId1);
        assertThat(item2.name()).isEqualTo("Route Bugs");
        assertThat(item2.workItemType()).isEqualTo("BUG");
        assertThat(item2.priority()).isEqualTo(10);
        assertThat(item2.targetTopicBindingId()).isEqualTo(topicBindingId);
        assertThat(item2.active()).isTrue();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-01T10:00:00Z"));
    }

    @Test
    void routingRulesReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID))
                .thenReturn(List.of());

        var result = facade.getRoutingRules(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void routingRulesThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getRoutingRules(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllRoutingRules(TENANT_ID);
    }

    @Test
    void routingRulesThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getRoutingRules(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void routingRulesDelegatesToTenantConfigQueryServiceOnly() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID))
                .thenReturn(List.of());

        facade.getRoutingRules(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService).listAllRoutingRules(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getChatBindings tests ==========

    @Test
    void chatBindingsReturnsCorrectItemListOrderedByBindingType() {
        Tenant tenant = mockTenant();
        UUID cbId1 = UUID.fromString("55555555-5555-5555-5555-555555555551");
        UUID cbId2 = UUID.fromString("55555555-5555-5555-5555-555555555552");

        TelegramChatBinding cb1 = mock(TelegramChatBinding.class);
        when(cb1.getId()).thenReturn(cbId1);
        when(cb1.getChatId()).thenReturn(100L);
        when(cb1.getChatTitle()).thenReturn("Notification Group");
        when(cb1.getBindingType()).thenReturn(ChatBindingType.NOTIFICATION_GROUP);
        when(cb1.isActive()).thenReturn(false);
        when(cb1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-10T10:00:00Z"));

        TelegramChatBinding cb2 = mock(TelegramChatBinding.class);
        when(cb2.getId()).thenReturn(cbId2);
        when(cb2.getChatId()).thenReturn(200L);
        when(cb2.getChatTitle()).thenReturn("Main Group");
        when(cb2.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(cb2.isActive()).thenReturn(true);
        when(cb2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-15T12:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID))
                .thenReturn(List.of(cb1, cb2));
        when(tenantConfigQueryService.listActiveTopicBindings(cbId1))
                .thenReturn(List.of(mock(TelegramTopicBinding.class)));
        when(tenantConfigQueryService.listActiveTopicBindings(cbId2))
                .thenReturn(List.of(
                        mock(TelegramTopicBinding.class),
                        mock(TelegramTopicBinding.class),
                        mock(TelegramTopicBinding.class)));

        var result = facade.getChatBindings(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // bindingType ASC — MAIN_GROUP birinchi, NOTIFICATION_GROUP ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.chatBindingId()).isEqualTo(cbId2);
        assertThat(item1.chatId()).isEqualTo(200L);
        assertThat(item1.chatTitle()).isEqualTo("Main Group");
        assertThat(item1.bindingType()).isEqualTo("MAIN_GROUP");
        assertThat(item1.active()).isTrue();
        assertThat(item1.activeTopicBindingCount()).isEqualTo(3);
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-15T12:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.chatBindingId()).isEqualTo(cbId1);
        assertThat(item2.chatId()).isEqualTo(100L);
        assertThat(item2.chatTitle()).isEqualTo("Notification Group");
        assertThat(item2.bindingType()).isEqualTo("NOTIFICATION_GROUP");
        assertThat(item2.active()).isFalse();
        assertThat(item2.activeTopicBindingCount()).isEqualTo(1);
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-10T10:00:00Z"));
    }

    @Test
    void chatBindingsTopicCountAggregatesCorrectlyPerBinding() {
        Tenant tenant = mockTenant();
        UUID cbId1 = UUID.fromString("55555555-5555-5555-5555-555555555561");
        UUID cbId2 = UUID.fromString("55555555-5555-5555-5555-555555555562");

        TelegramChatBinding cb1 = mock(TelegramChatBinding.class);
        when(cb1.getId()).thenReturn(cbId1);
        when(cb1.getChatId()).thenReturn(300L);
        when(cb1.getChatTitle()).thenReturn(null);
        when(cb1.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(cb1.isActive()).thenReturn(true);
        when(cb1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-01T08:00:00Z"));

        TelegramChatBinding cb2 = mock(TelegramChatBinding.class);
        when(cb2.getId()).thenReturn(cbId2);
        when(cb2.getChatId()).thenReturn(400L);
        when(cb2.getChatTitle()).thenReturn("Second");
        when(cb2.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(cb2.isActive()).thenReturn(true);
        when(cb2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-02T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID))
                .thenReturn(List.of(cb1, cb2));
        when(tenantConfigQueryService.listActiveTopicBindings(cbId1))
                .thenReturn(List.of(
                        mock(TelegramTopicBinding.class),
                        mock(TelegramTopicBinding.class)));
        when(tenantConfigQueryService.listActiveTopicBindings(cbId2))
                .thenReturn(List.of());

        var result = facade.getChatBindings(TENANT_ID);

        // id tie-breaker (same bindingType MAIN_GROUP): cbId1 < cbId2
        assertThat(result.items().get(0).activeTopicBindingCount()).isEqualTo(2);
        assertThat(result.items().get(1).activeTopicBindingCount()).isZero();

        verify(tenantConfigQueryService).listActiveTopicBindings(cbId1);
        verify(tenantConfigQueryService).listActiveTopicBindings(cbId2);
    }

    @Test
    void chatBindingsReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID))
                .thenReturn(List.of());

        var result = facade.getChatBindings(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void chatBindingsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getChatBindings(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllChatBindings(TENANT_ID);
    }

    @Test
    void chatBindingsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getChatBindings(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void chatBindingsDelegatesToTenantConfigQueryServiceOnly() {
        Tenant tenant = mockTenant();
        UUID cbId = UUID.fromString("55555555-5555-5555-5555-555555555571");

        TelegramChatBinding cb = mock(TelegramChatBinding.class);
        when(cb.getId()).thenReturn(cbId);
        when(cb.getChatId()).thenReturn(500L);
        when(cb.getChatTitle()).thenReturn("Test");
        when(cb.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(cb.isActive()).thenReturn(true);
        when(cb.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-01T00:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID))
                .thenReturn(List.of(cb));
        when(tenantConfigQueryService.listActiveTopicBindings(cbId))
                .thenReturn(List.of());

        facade.getChatBindings(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService).listAllChatBindings(TENANT_ID);
        verify(tenantConfigQueryService).listActiveTopicBindings(cbId);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getTopicBindings tests ==========

    @Test
    void topicBindingsReturnsCorrectFlatListOrderedByPurpose() {
        Tenant tenant = mockTenant();
        UUID cbId = UUID.fromString("66666666-6666-6666-6666-666666666661");
        UUID tbId1 = UUID.fromString("77777777-7777-7777-7777-777777777771");
        UUID tbId2 = UUID.fromString("77777777-7777-7777-7777-777777777772");

        TelegramChatBinding cb = mock(TelegramChatBinding.class);
        when(cb.getId()).thenReturn(cbId);
        when(cb.getChatId()).thenReturn(100L);
        when(cb.getChatTitle()).thenReturn("Main Group");

        TelegramTopicBinding tb1 = mock(TelegramTopicBinding.class);
        when(tb1.getId()).thenReturn(tbId1);
        when(tb1.getTopicId()).thenReturn(10L);
        when(tb1.getTopicName()).thenReturn("Incidents Topic");
        when(tb1.getPurpose()).thenReturn("incidents");
        when(tb1.isActive()).thenReturn(true);
        when(tb1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-20T10:00:00Z"));

        TelegramTopicBinding tb2 = mock(TelegramTopicBinding.class);
        when(tb2.getId()).thenReturn(tbId2);
        when(tb2.getTopicId()).thenReturn(20L);
        when(tb2.getTopicName()).thenReturn(null);
        when(tb2.getPurpose()).thenReturn("bugs");
        when(tb2.isActive()).thenReturn(false);
        when(tb2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-25T12:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID)).thenReturn(List.of(cb));
        when(tenantConfigQueryService.listAllTopicBindings(cbId)).thenReturn(List.of(tb1, tb2));

        var result = facade.getTopicBindings(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // purpose ASC — "bugs" birinchi, "incidents" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.topicBindingId()).isEqualTo(tbId2);
        assertThat(item1.chatBindingId()).isEqualTo(cbId);
        assertThat(item1.chatId()).isEqualTo(100L);
        assertThat(item1.chatTitle()).isEqualTo("Main Group");
        assertThat(item1.topicId()).isEqualTo(20L);
        assertThat(item1.topicName()).isNull();
        assertThat(item1.purpose()).isEqualTo("bugs");
        assertThat(item1.active()).isFalse();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-25T12:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.topicBindingId()).isEqualTo(tbId1);
        assertThat(item2.chatBindingId()).isEqualTo(cbId);
        assertThat(item2.chatId()).isEqualTo(100L);
        assertThat(item2.chatTitle()).isEqualTo("Main Group");
        assertThat(item2.topicId()).isEqualTo(10L);
        assertThat(item2.topicName()).isEqualTo("Incidents Topic");
        assertThat(item2.purpose()).isEqualTo("incidents");
        assertThat(item2.active()).isTrue();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-20T10:00:00Z"));
    }

    @Test
    void topicBindingsFlattenedAcrossMultipleChatBindings() {
        Tenant tenant = mockTenant();
        UUID cbId1 = UUID.fromString("66666666-6666-6666-6666-666666666671");
        UUID cbId2 = UUID.fromString("66666666-6666-6666-6666-666666666672");
        UUID tbId1 = UUID.fromString("77777777-7777-7777-7777-777777777781");
        UUID tbId2 = UUID.fromString("77777777-7777-7777-7777-777777777782");

        TelegramChatBinding cb1 = mock(TelegramChatBinding.class);
        when(cb1.getId()).thenReturn(cbId1);
        when(cb1.getChatId()).thenReturn(100L);
        when(cb1.getChatTitle()).thenReturn("Chat A");

        TelegramChatBinding cb2 = mock(TelegramChatBinding.class);
        when(cb2.getId()).thenReturn(cbId2);
        when(cb2.getChatId()).thenReturn(200L);
        when(cb2.getChatTitle()).thenReturn("Chat B");

        TelegramTopicBinding tb1 = mock(TelegramTopicBinding.class);
        when(tb1.getId()).thenReturn(tbId1);
        when(tb1.getTopicId()).thenReturn(10L);
        when(tb1.getTopicName()).thenReturn("Topic X");
        when(tb1.getPurpose()).thenReturn("bugs");
        when(tb1.isActive()).thenReturn(true);
        when(tb1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-01T08:00:00Z"));

        TelegramTopicBinding tb2 = mock(TelegramTopicBinding.class);
        when(tb2.getId()).thenReturn(tbId2);
        when(tb2.getTopicId()).thenReturn(20L);
        when(tb2.getTopicName()).thenReturn("Topic Y");
        when(tb2.getPurpose()).thenReturn("bugs");
        when(tb2.isActive()).thenReturn(true);
        when(tb2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-02T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID)).thenReturn(List.of(cb1, cb2));
        when(tenantConfigQueryService.listAllTopicBindings(cbId1)).thenReturn(List.of(tb1));
        when(tenantConfigQueryService.listAllTopicBindings(cbId2)).thenReturn(List.of(tb2));

        var result = facade.getTopicBindings(TENANT_ID);

        assertThat(result.items()).hasSize(2);

        // same purpose "bugs" — id tie-breaker: tbId1 < tbId2
        assertThat(result.items().get(0).topicBindingId()).isEqualTo(tbId1);
        assertThat(result.items().get(0).chatId()).isEqualTo(100L);
        assertThat(result.items().get(1).topicBindingId()).isEqualTo(tbId2);
        assertThat(result.items().get(1).chatId()).isEqualTo(200L);

        verify(tenantConfigQueryService).listAllTopicBindings(cbId1);
        verify(tenantConfigQueryService).listAllTopicBindings(cbId2);
    }

    @Test
    void topicBindingsReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID)).thenReturn(List.of());

        var result = facade.getTopicBindings(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void topicBindingsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getTopicBindings(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllChatBindings(TENANT_ID);
    }

    @Test
    void topicBindingsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getTopicBindings(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void topicBindingsDelegatesToTenantConfigQueryServiceOnly() {
        Tenant tenant = mockTenant();
        UUID cbId = UUID.fromString("66666666-6666-6666-6666-666666666691");

        TelegramChatBinding cb = mock(TelegramChatBinding.class);
        when(cb.getId()).thenReturn(cbId);
        when(cb.getChatId()).thenReturn(500L);
        when(cb.getChatTitle()).thenReturn("Test");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID)).thenReturn(List.of(cb));
        when(tenantConfigQueryService.listAllTopicBindings(cbId)).thenReturn(List.of());

        facade.getTopicBindings(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService).listAllChatBindings(TENANT_ID);
        verify(tenantConfigQueryService).listAllTopicBindings(cbId);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getMemberships tests ==========

    @Test
    void membershipsReturnsCorrectItemListOrderedByStatusThenDisplayName() {
        Tenant tenant = mockTenant();
        UUID mId1 = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID mId2 = UUID.fromString("aa222222-2222-2222-2222-222222222222");
        UUID uId1 = UUID.fromString("bb111111-1111-1111-1111-111111111111");
        UUID uId2 = UUID.fromString("bb222222-2222-2222-2222-222222222222");

        Membership m1 = mock(Membership.class);
        when(m1.getId()).thenReturn(mId1);
        when(m1.getUserId()).thenReturn(uId1);
        when(m1.getStatus()).thenReturn(com.engops.platform.identity.model.MembershipStatus.SUSPENDED);
        when(m1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-02-01T08:00:00Z"));

        Membership m2 = mock(Membership.class);
        when(m2.getId()).thenReturn(mId2);
        when(m2.getUserId()).thenReturn(uId2);
        when(m2.getStatus()).thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(m2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-03-01T08:00:00Z"));

        AppUser user1 = mock(AppUser.class);
        when(user1.getTelegramUserId()).thenReturn(1001L);
        when(user1.getDisplayName()).thenReturn("Zafar");
        when(user1.getUsername()).thenReturn("zafar_dev");

        AppUser user2 = mock(AppUser.class);
        when(user2.getTelegramUserId()).thenReturn(1002L);
        when(user2.getDisplayName()).thenReturn("Anvar");
        when(user2.getUsername()).thenReturn(null);

        Role adminRole = mock(Role.class);
        when(adminRole.getName()).thenReturn("Administrator");
        MembershipRoleBinding roleBinding = mock(MembershipRoleBinding.class);
        when(roleBinding.getRole()).thenReturn(adminRole);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of(m1, m2));
        when(identityQueryService.findUserById(uId1)).thenReturn(Optional.of(user1));
        when(identityQueryService.findUserById(uId2)).thenReturn(Optional.of(user2));
        when(identityQueryService.getMembershipRoles(mId1)).thenReturn(List.of());
        when(identityQueryService.getMembershipRoles(mId2)).thenReturn(List.of(roleBinding));

        var result = facade.getMemberships(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // ACTIVE birinchi (m2), SUSPENDED ikkinchi (m1)
        var item1 = result.items().get(0);
        assertThat(item1.membershipId()).isEqualTo(mId2);
        assertThat(item1.userId()).isEqualTo(uId2);
        assertThat(item1.telegramUserId()).isEqualTo(1002L);
        assertThat(item1.displayName()).isEqualTo("Anvar");
        assertThat(item1.username()).isNull();
        assertThat(item1.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(item1.roleNames()).containsExactly("Administrator");
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-03-01T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.membershipId()).isEqualTo(mId1);
        assertThat(item2.userId()).isEqualTo(uId1);
        assertThat(item2.telegramUserId()).isEqualTo(1001L);
        assertThat(item2.displayName()).isEqualTo("Zafar");
        assertThat(item2.username()).isEqualTo("zafar_dev");
        assertThat(item2.membershipStatus()).isEqualTo("SUSPENDED");
        assertThat(item2.roleNames()).isNull();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-02-01T08:00:00Z"));
    }

    @Test
    void membershipsReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());

        var result = facade.getMemberships(TENANT_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void membershipsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getMemberships(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoInteractions(identityQueryService);
    }

    @Test
    void membershipsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getMemberships(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void membershipsDelegatesToBothQueryServices() {
        Tenant tenant = mockTenant();
        UUID mId = UUID.fromString("aa333333-3333-3333-3333-333333333333");
        UUID uId = UUID.fromString("bb333333-3333-3333-3333-333333333333");

        Membership m = mock(Membership.class);
        when(m.getId()).thenReturn(mId);
        when(m.getUserId()).thenReturn(uId);
        when(m.getStatus()).thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(m.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-01T00:00:00Z"));

        AppUser user = mock(AppUser.class);
        when(user.getTelegramUserId()).thenReturn(9999L);
        when(user.getDisplayName()).thenReturn("Test");
        when(user.getUsername()).thenReturn(null);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of(m));
        when(identityQueryService.findUserById(uId)).thenReturn(Optional.of(user));
        when(identityQueryService.getMembershipRoles(mId)).thenReturn(List.of());

        facade.getMemberships(TENANT_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verify(identityQueryService).listAllMembers(TENANT_ID);
        verify(identityQueryService).findUserById(uId);
        verify(identityQueryService).getMembershipRoles(mId);
        verifyNoMoreInteractions(identityQueryService);
    }

    @Test
    void membershipsHandlesMissingUserGracefully() {
        Tenant tenant = mockTenant();
        UUID mId = UUID.fromString("aa444444-4444-4444-4444-444444444444");
        UUID uId = UUID.fromString("bb444444-4444-4444-4444-444444444444");

        Membership m = mock(Membership.class);
        when(m.getId()).thenReturn(mId);
        when(m.getUserId()).thenReturn(uId);
        when(m.getStatus()).thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(m.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-04-01T00:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of(m));
        when(identityQueryService.findUserById(uId)).thenReturn(Optional.empty());
        when(identityQueryService.getMembershipRoles(mId)).thenReturn(List.of());

        var result = facade.getMemberships(TENANT_ID);

        assertThat(result.items()).hasSize(1);
        var item = result.items().get(0);
        assertThat(item.membershipId()).isEqualTo(mId);
        assertThat(item.telegramUserId()).isNull();
        assertThat(item.displayName()).isNull();
        assertThat(item.username()).isNull();
        assertThat(item.membershipStatus()).isEqualTo("ACTIVE");
    }

    // ========== Helpers ==========

    private Tenant mockTenant() {
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(tenant.getName()).thenReturn("Test Tenant");
        when(tenant.getSlug()).thenReturn("test-tenant");
        when(tenant.getTimezone()).thenReturn("Asia/Tashkent");
        when(tenant.getStatus()).thenReturn(
                com.engops.platform.tenantconfig.model.TenantStatus.ACTIVE);
        when(tenant.getCreatedAt()).thenReturn(
                java.time.Instant.parse("2026-01-15T08:00:00Z"));
        return tenant;
    }
}
