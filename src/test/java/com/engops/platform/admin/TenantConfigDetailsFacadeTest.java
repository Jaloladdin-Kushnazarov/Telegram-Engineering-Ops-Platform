package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Permission;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.model.WorkflowTransitionRule;
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
    private static final UUID ACTOR_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final TenantConfigQueryService tenantConfigQueryService =
            mock(TenantConfigQueryService.class);
    private final IdentityQueryService identityQueryService =
            mock(IdentityQueryService.class);
    private final AdminAuthorizationService authorizationService =
            mock(AdminAuthorizationService.class);
    private final TenantConfigDetailsFacade facade =
            new TenantConfigDetailsFacade(tenantConfigQueryService, identityQueryService, authorizationService);

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

        var result = facade.getDetails(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getDetails(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getDetails(TENANT_ID, ACTOR_USER_ID);

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

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(identityQueryService);
        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
    }

    @Test
    void throwsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getDetails(null, ACTOR_USER_ID))
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

        facade.getDetails(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getWorkflowDefinitions(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getWorkflowDefinitions(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void workflowDefinitionsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getWorkflowDefinitions(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllWorkflowDefinitions(TENANT_ID);
    }

    @Test
    void workflowDefinitionsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getWorkflowDefinitions(null, ACTOR_USER_ID))
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

        facade.getWorkflowDefinitions(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getRoutingRules(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getRoutingRules(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void routingRulesThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getRoutingRules(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllRoutingRules(TENANT_ID);
    }

    @Test
    void routingRulesThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getRoutingRules(null, ACTOR_USER_ID))
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

        facade.getRoutingRules(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getChatBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getChatBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getChatBindings(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void chatBindingsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getChatBindings(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllChatBindings(TENANT_ID);
    }

    @Test
    void chatBindingsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getChatBindings(null, ACTOR_USER_ID))
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

        facade.getChatBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getTopicBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getTopicBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getTopicBindings(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void topicBindingsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getTopicBindings(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).listAllChatBindings(TENANT_ID);
    }

    @Test
    void topicBindingsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getTopicBindings(null, ACTOR_USER_ID))
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

        facade.getTopicBindings(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getMemberships(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getMemberships(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void membershipsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getMemberships(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoInteractions(identityQueryService);
    }

    @Test
    void membershipsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getMemberships(null, ACTOR_USER_ID))
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

        facade.getMemberships(TENANT_ID, ACTOR_USER_ID);

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

        var result = facade.getMemberships(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.items()).hasSize(1);
        var item = result.items().get(0);
        assertThat(item.membershipId()).isEqualTo(mId);
        assertThat(item.telegramUserId()).isNull();
        assertThat(item.displayName()).isNull();
        assertThat(item.username()).isNull();
        assertThat(item.membershipStatus()).isEqualTo("ACTIVE");
    }

    // ========== getRoles tests ==========

    @Test
    void rolesReturnsCorrectItemListOrderedByCode() {
        Tenant tenant = mockTenant();
        UUID roleId1 = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        UUID roleId2 = UUID.fromString("cc222222-2222-2222-2222-222222222222");

        Role role1 = mock(Role.class);
        when(role1.getId()).thenReturn(roleId1);
        when(role1.getCode()).thenReturn("ENGINEER");
        when(role1.getName()).thenReturn("Engineer");
        when(role1.getDescription()).thenReturn("Engineering role");
        when(role1.isSystemRole()).thenReturn(false);
        when(role1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Role role2 = mock(Role.class);
        when(role2.getId()).thenReturn(roleId2);
        when(role2.getCode()).thenReturn("ADMIN");
        when(role2.getName()).thenReturn("Administrator");
        when(role2.getDescription()).thenReturn(null);
        when(role2.isSystemRole()).thenReturn(true);
        when(role2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllRoles()).thenReturn(List.of(role1, role2));

        var result = facade.getRoles(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // code ASC — "ADMIN" birinchi, "ENGINEER" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.roleId()).isEqualTo(roleId2);
        assertThat(item1.code()).isEqualTo("ADMIN");
        assertThat(item1.name()).isEqualTo("Administrator");
        assertThat(item1.description()).isNull();
        assertThat(item1.systemRole()).isTrue();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.roleId()).isEqualTo(roleId1);
        assertThat(item2.code()).isEqualTo("ENGINEER");
        assertThat(item2.name()).isEqualTo("Engineer");
        assertThat(item2.description()).isEqualTo("Engineering role");
        assertThat(item2.systemRole()).isFalse();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-10T08:00:00Z"));
    }

    @Test
    void rolesReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllRoles()).thenReturn(List.of());

        var result = facade.getRoles(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void rolesThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getRoles(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).listAllRoles();
    }

    @Test
    void rolesThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getRoles(null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void rolesDelegatesToBothServices() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllRoles()).thenReturn(List.of());

        facade.getRoles(TENANT_ID, ACTOR_USER_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verifyNoMoreInteractions(tenantConfigQueryService);
        verify(identityQueryService).listAllRoles();
        verifyNoMoreInteractions(identityQueryService);
    }

    // ========== getPermissions tests ==========

    @Test
    void permissionsReturnsCorrectItemListOrderedByCode() {
        Tenant tenant = mockTenant();
        UUID permId1 = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID permId2 = UUID.fromString("dd222222-2222-2222-2222-222222222222");

        Permission perm1 = mock(Permission.class);
        when(perm1.getId()).thenReturn(permId1);
        when(perm1.getCode()).thenReturn("TENANT_CONFIG_WRITE");
        when(perm1.getDescription()).thenReturn("Tenant config yozish");
        when(perm1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Permission perm2 = mock(Permission.class);
        when(perm2.getId()).thenReturn(permId2);
        when(perm2.getCode()).thenReturn("TENANT_CONFIG_READ");
        when(perm2.getDescription()).thenReturn(null);
        when(perm2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllPermissions()).thenReturn(List.of(perm1, perm2));

        var result = facade.getPermissions(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).hasSize(2);

        // code ASC — "TENANT_CONFIG_READ" birinchi, "TENANT_CONFIG_WRITE" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.permissionId()).isEqualTo(permId2);
        assertThat(item1.code()).isEqualTo("TENANT_CONFIG_READ");
        assertThat(item1.description()).isNull();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.permissionId()).isEqualTo(permId1);
        assertThat(item2.code()).isEqualTo("TENANT_CONFIG_WRITE");
        assertThat(item2.description()).isEqualTo("Tenant config yozish");
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-10T08:00:00Z"));
    }

    @Test
    void permissionsReturnsEmptyListWhenNoneExist() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllPermissions()).thenReturn(List.of());

        var result = facade.getPermissions(TENANT_ID, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void permissionsThrowsResourceNotFoundWhenTenantMissing() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getPermissions(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).listAllPermissions();
    }

    @Test
    void permissionsThrowsIllegalArgumentWhenTenantIdNull() {
        assertThatThrownBy(() -> facade.getPermissions(null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(tenantConfigQueryService, identityQueryService);
    }

    @Test
    void permissionsDelegatesToBothServices() {
        Tenant tenant = mockTenant();

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllPermissions()).thenReturn(List.of());

        facade.getPermissions(TENANT_ID, ACTOR_USER_ID);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService).listAllPermissions();
    }

    @Test
    void getPermissionsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllPermissions()).thenReturn(List.of());

        facade.getPermissions(TENANT_ID, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void getPermissionsNullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getPermissions(null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void getPermissionsDeniedWhenAuthorizationFails() {
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getPermissions(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getRolePermissions tests ==========

    @Test
    void rolePermissionsReturnsCorrectItemListOrderedByCode() {
        Tenant tenant = mockTenant();
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        UUID permId1 = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID permId2 = UUID.fromString("dd222222-2222-2222-2222-222222222222");

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("ADMIN");
        when(role.getName()).thenReturn("Administrator");

        Permission perm1 = mock(Permission.class);
        when(perm1.getId()).thenReturn(permId1);
        when(perm1.getCode()).thenReturn("TENANT_CONFIG_WRITE");
        when(perm1.getDescription()).thenReturn("Tenant config yozish");
        when(perm1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Permission perm2 = mock(Permission.class);
        when(perm2.getId()).thenReturn(permId2);
        when(perm2.getCode()).thenReturn("TENANT_CONFIG_READ");
        when(perm2.getDescription()).thenReturn(null);
        when(perm2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        RolePermission b1 = mock(RolePermission.class);
        when(b1.getPermission()).thenReturn(perm1);
        RolePermission b2 = mock(RolePermission.class);
        when(b2.getPermission()).thenReturn(perm2);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findRoleById(roleId)).thenReturn(Optional.of(role));
        when(identityQueryService.findRolePermissions(roleId)).thenReturn(List.of(b1, b2));

        var result = facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.roleId()).isEqualTo(roleId);
        assertThat(result.roleCode()).isEqualTo("ADMIN");
        assertThat(result.roleName()).isEqualTo("Administrator");
        assertThat(result.items()).hasSize(2);

        // code ASC — "TENANT_CONFIG_READ" birinchi, "TENANT_CONFIG_WRITE" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.permissionId()).isEqualTo(permId2);
        assertThat(item1.code()).isEqualTo("TENANT_CONFIG_READ");
        assertThat(item1.description()).isNull();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.permissionId()).isEqualTo(permId1);
        assertThat(item2.code()).isEqualTo("TENANT_CONFIG_WRITE");
        assertThat(item2.description()).isEqualTo("Tenant config yozish");
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-10T08:00:00Z"));
    }

    @Test
    void rolePermissionsTieBreakerByPermissionId() {
        Tenant tenant = mockTenant();
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        UUID permIdA = UUID.fromString("dd000000-0000-0000-0000-00000000000a");
        UUID permIdB = UUID.fromString("dd000000-0000-0000-0000-00000000000b");

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("ADMIN");
        when(role.getName()).thenReturn("Administrator");

        // Ikkala permission bir xil code'ga ega — tie-breaker permissionId ASC bo'lishi kerak
        Permission permB = mock(Permission.class);
        when(permB.getId()).thenReturn(permIdB);
        when(permB.getCode()).thenReturn("SAME_CODE");
        when(permB.getDescription()).thenReturn(null);
        when(permB.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Permission permA = mock(Permission.class);
        when(permA.getId()).thenReturn(permIdA);
        when(permA.getCode()).thenReturn("SAME_CODE");
        when(permA.getDescription()).thenReturn(null);
        when(permA.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        RolePermission bB = mock(RolePermission.class);
        when(bB.getPermission()).thenReturn(permB);
        RolePermission bA = mock(RolePermission.class);
        when(bA.getPermission()).thenReturn(permA);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findRoleById(roleId)).thenReturn(Optional.of(role));
        when(identityQueryService.findRolePermissions(roleId)).thenReturn(List.of(bB, bA));

        var result = facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID);

        assertThat(result.items()).hasSize(2);
        // permIdA (...0a) permIdB (...0b) dan oldin kelishi kerak
        assertThat(result.items().get(0).permissionId()).isEqualTo(permIdA);
        assertThat(result.items().get(1).permissionId()).isEqualTo(permIdB);
    }

    @Test
    void rolePermissionsReturnsEmptyListWhenNoneAssigned() {
        Tenant tenant = mockTenant();
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("ADMIN");
        when(role.getName()).thenReturn("Administrator");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findRoleById(roleId)).thenReturn(Optional.of(role));
        when(identityQueryService.findRolePermissions(roleId)).thenReturn(List.of());

        var result = facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.roleId()).isEqualTo(roleId);
        assertThat(result.roleCode()).isEqualTo("ADMIN");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void rolePermissionsThrowsResourceNotFoundWhenTenantMissing() {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).findRoleById(any());
        verify(identityQueryService, never()).findRolePermissions(any());
    }

    @Test
    void rolePermissionsThrowsResourceNotFoundWhenRoleMissing() {
        Tenant tenant = mockTenant();
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findRoleById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(identityQueryService).findRoleById(roleId);
        verify(identityQueryService, never()).findRolePermissions(any());
    }

    @Test
    void rolePermissionsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getRolePermissions(null, roleId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void rolePermissionsThrowsIllegalArgumentWhenRoleIdNull() {
        assertThatThrownBy(() -> facade.getRolePermissions(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void rolePermissionsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("ADMIN");
        when(role.getName()).thenReturn("Administrator");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findRoleById(roleId)).thenReturn(Optional.of(role));
        when(identityQueryService.findRolePermissions(roleId)).thenReturn(List.of());

        facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void rolePermissionsNullTenantIdSkipsAuthorization() {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getRolePermissions(null, roleId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void rolePermissionsNullRoleIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getRolePermissions(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void rolePermissionsDeniedWhenAuthorizationFails() {
        UUID roleId = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getRolePermissions(TENANT_ID, roleId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getPermissionRoles tests ==========

    @Test
    void permissionRolesReturnsCorrectItemListOrderedByCode() {
        Tenant tenant = mockTenant();
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID roleId1 = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        UUID roleId2 = UUID.fromString("cc222222-2222-2222-2222-222222222222");

        Permission permission = mock(Permission.class);
        when(permission.getId()).thenReturn(permissionId);
        when(permission.getCode()).thenReturn("TENANT_CONFIG_READ");

        Role role1 = mock(Role.class);
        when(role1.getId()).thenReturn(roleId1);
        when(role1.getCode()).thenReturn("ENGINEER");
        when(role1.getName()).thenReturn("Engineer");
        when(role1.getDescription()).thenReturn("Engineering role");
        when(role1.isSystemRole()).thenReturn(false);
        when(role1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Role role2 = mock(Role.class);
        when(role2.getId()).thenReturn(roleId2);
        when(role2.getCode()).thenReturn("ADMIN");
        when(role2.getName()).thenReturn("Administrator");
        when(role2.getDescription()).thenReturn(null);
        when(role2.isSystemRole()).thenReturn(true);
        when(role2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        RolePermission b1 = mock(RolePermission.class);
        when(b1.getRole()).thenReturn(role1);
        RolePermission b2 = mock(RolePermission.class);
        when(b2.getRole()).thenReturn(role2);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findPermissionById(permissionId)).thenReturn(Optional.of(permission));
        when(identityQueryService.findPermissionRoles(permissionId)).thenReturn(List.of(b1, b2));

        var result = facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.permissionId()).isEqualTo(permissionId);
        assertThat(result.permissionCode()).isEqualTo("TENANT_CONFIG_READ");
        assertThat(result.items()).hasSize(2);

        // code ASC — "ADMIN" birinchi, "ENGINEER" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.roleId()).isEqualTo(roleId2);
        assertThat(item1.code()).isEqualTo("ADMIN");
        assertThat(item1.name()).isEqualTo("Administrator");
        assertThat(item1.description()).isNull();
        assertThat(item1.systemRole()).isTrue();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.roleId()).isEqualTo(roleId1);
        assertThat(item2.code()).isEqualTo("ENGINEER");
        assertThat(item2.name()).isEqualTo("Engineer");
        assertThat(item2.description()).isEqualTo("Engineering role");
        assertThat(item2.systemRole()).isFalse();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-10T08:00:00Z"));
    }

    @Test
    void permissionRolesTieBreakerByRoleId() {
        Tenant tenant = mockTenant();
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID roleIdA = UUID.fromString("cc000000-0000-0000-0000-00000000000a");
        UUID roleIdB = UUID.fromString("cc000000-0000-0000-0000-00000000000b");

        Permission permission = mock(Permission.class);
        when(permission.getId()).thenReturn(permissionId);
        when(permission.getCode()).thenReturn("TENANT_CONFIG_READ");

        // Ikkala rol bir xil code'ga ega — tie-breaker roleId ASC bo'lishi kerak
        Role roleB = mock(Role.class);
        when(roleB.getId()).thenReturn(roleIdB);
        when(roleB.getCode()).thenReturn("SAME_CODE");
        when(roleB.getName()).thenReturn("Same B");
        when(roleB.getDescription()).thenReturn(null);
        when(roleB.isSystemRole()).thenReturn(false);
        when(roleB.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Role roleA = mock(Role.class);
        when(roleA.getId()).thenReturn(roleIdA);
        when(roleA.getCode()).thenReturn("SAME_CODE");
        when(roleA.getName()).thenReturn("Same A");
        when(roleA.getDescription()).thenReturn(null);
        when(roleA.isSystemRole()).thenReturn(false);
        when(roleA.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        RolePermission bB = mock(RolePermission.class);
        when(bB.getRole()).thenReturn(roleB);
        RolePermission bA = mock(RolePermission.class);
        when(bA.getRole()).thenReturn(roleA);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findPermissionById(permissionId)).thenReturn(Optional.of(permission));
        when(identityQueryService.findPermissionRoles(permissionId)).thenReturn(List.of(bB, bA));

        var result = facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID);

        assertThat(result.items()).hasSize(2);
        // roleIdA (...0a) roleIdB (...0b) dan oldin kelishi kerak
        assertThat(result.items().get(0).roleId()).isEqualTo(roleIdA);
        assertThat(result.items().get(1).roleId()).isEqualTo(roleIdB);
    }

    @Test
    void permissionRolesReturnsEmptyListWhenNoneAssigned() {
        Tenant tenant = mockTenant();
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        Permission permission = mock(Permission.class);
        when(permission.getId()).thenReturn(permissionId);
        when(permission.getCode()).thenReturn("TENANT_CONFIG_READ");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findPermissionById(permissionId)).thenReturn(Optional.of(permission));
        when(identityQueryService.findPermissionRoles(permissionId)).thenReturn(List.of());

        var result = facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.permissionId()).isEqualTo(permissionId);
        assertThat(result.permissionCode()).isEqualTo("TENANT_CONFIG_READ");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void permissionRolesThrowsResourceNotFoundWhenTenantMissing() {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).findPermissionById(any());
        verify(identityQueryService, never()).findPermissionRoles(any());
    }

    @Test
    void permissionRolesThrowsResourceNotFoundWhenPermissionMissing() {
        Tenant tenant = mockTenant();
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findPermissionById(permissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(identityQueryService).findPermissionById(permissionId);
        verify(identityQueryService, never()).findPermissionRoles(any());
    }

    @Test
    void permissionRolesThrowsIllegalArgumentWhenTenantIdNull() {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getPermissionRoles(null, permissionId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void permissionRolesThrowsIllegalArgumentWhenPermissionIdNull() {
        assertThatThrownBy(() -> facade.getPermissionRoles(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissionId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void permissionRolesCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        Permission permission = mock(Permission.class);
        when(permission.getId()).thenReturn(permissionId);
        when(permission.getCode()).thenReturn("TENANT_CONFIG_READ");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findPermissionById(permissionId)).thenReturn(Optional.of(permission));
        when(identityQueryService.findPermissionRoles(permissionId)).thenReturn(List.of());

        facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void permissionRolesNullTenantIdSkipsAuthorization() {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getPermissionRoles(null, permissionId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void permissionRolesNullPermissionIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getPermissionRoles(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void permissionRolesDeniedWhenAuthorizationFails() {
        UUID permissionId = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getPermissionRoles(TENANT_ID, permissionId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getMembershipRoles tests ==========

    @Test
    void membershipRolesReturnsCorrectItemListOrderedByCode() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        UUID roleId1 = UUID.fromString("cc111111-1111-1111-1111-111111111111");
        UUID roleId2 = UUID.fromString("cc222222-2222-2222-2222-222222222222");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);

        Role role1 = mock(Role.class);
        when(role1.getId()).thenReturn(roleId1);
        when(role1.getCode()).thenReturn("ENGINEER");
        when(role1.getName()).thenReturn("Engineer");
        when(role1.getDescription()).thenReturn("Engineering role");
        when(role1.isSystemRole()).thenReturn(false);
        when(role1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Role role2 = mock(Role.class);
        when(role2.getId()).thenReturn(roleId2);
        when(role2.getCode()).thenReturn("ADMIN");
        when(role2.getName()).thenReturn("Administrator");
        when(role2.getDescription()).thenReturn(null);
        when(role2.isSystemRole()).thenReturn(true);
        when(role2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        MembershipRoleBinding b1 = mock(MembershipRoleBinding.class);
        when(b1.getRole()).thenReturn(role1);
        MembershipRoleBinding b2 = mock(MembershipRoleBinding.class);
        when(b2.getRole()).thenReturn(role2);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.getMembershipRoles(membershipId)).thenReturn(List.of(b1, b2));

        var result = facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.membershipId()).isEqualTo(membershipId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(result.items()).hasSize(2);

        // code ASC — "ADMIN" birinchi, "ENGINEER" ikkinchi
        var item1 = result.items().get(0);
        assertThat(item1.roleId()).isEqualTo(roleId2);
        assertThat(item1.code()).isEqualTo("ADMIN");
        assertThat(item1.name()).isEqualTo("Administrator");
        assertThat(item1.description()).isNull();
        assertThat(item1.systemRole()).isTrue();
        assertThat(item1.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        var item2 = result.items().get(1);
        assertThat(item2.roleId()).isEqualTo(roleId1);
        assertThat(item2.code()).isEqualTo("ENGINEER");
        assertThat(item2.name()).isEqualTo("Engineer");
        assertThat(item2.description()).isEqualTo("Engineering role");
        assertThat(item2.systemRole()).isFalse();
        assertThat(item2.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-10T08:00:00Z"));
    }

    @Test
    void membershipRolesTieBreakerByRoleId() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        UUID roleIdA = UUID.fromString("cc000000-0000-0000-0000-00000000000a");
        UUID roleIdB = UUID.fromString("cc000000-0000-0000-0000-00000000000b");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);

        // Ikkala rol bir xil code'ga ega — tie-breaker roleId ASC bo'lishi kerak
        Role roleB = mock(Role.class);
        when(roleB.getId()).thenReturn(roleIdB);
        when(roleB.getCode()).thenReturn("SAME_CODE");
        when(roleB.getName()).thenReturn("Same B");
        when(roleB.getDescription()).thenReturn(null);
        when(roleB.isSystemRole()).thenReturn(false);
        when(roleB.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-10T08:00:00Z"));

        Role roleA = mock(Role.class);
        when(roleA.getId()).thenReturn(roleIdA);
        when(roleA.getCode()).thenReturn("SAME_CODE");
        when(roleA.getName()).thenReturn("Same A");
        when(roleA.getDescription()).thenReturn(null);
        when(roleA.isSystemRole()).thenReturn(false);
        when(roleA.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-05T08:00:00Z"));

        MembershipRoleBinding bB = mock(MembershipRoleBinding.class);
        when(bB.getRole()).thenReturn(roleB);
        MembershipRoleBinding bA = mock(MembershipRoleBinding.class);
        when(bA.getRole()).thenReturn(roleA);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.getMembershipRoles(membershipId)).thenReturn(List.of(bB, bA));

        var result = facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID);

        assertThat(result.items()).hasSize(2);
        // roleIdA (...0a) roleIdB (...0b) dan oldin kelishi kerak
        assertThat(result.items().get(0).roleId()).isEqualTo(roleIdA);
        assertThat(result.items().get(1).roleId()).isEqualTo(roleIdB);
    }

    @Test
    void membershipRolesReturnsEmptyListWhenNoneAssigned() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.SUSPENDED);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.getMembershipRoles(membershipId)).thenReturn(List.of());

        var result = facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.membershipId()).isEqualTo(membershipId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.membershipStatus()).isEqualTo("SUSPENDED");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void membershipRolesThrowsResourceNotFoundWhenTenantMissing() {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).findMembershipByIdAndTenantId(any(), any());
        verify(identityQueryService, never()).getMembershipRoles(any());
    }

    @Test
    void membershipRolesThrowsResourceNotFoundWhenMembershipMissing() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(identityQueryService).findMembershipByIdAndTenantId(membershipId, TENANT_ID);
        verify(identityQueryService, never()).getMembershipRoles(any());
    }

    @Test
    void membershipRolesCrossTenantReturnsNotFound() {
        // membership boshqa tenantga tegishli — tenant-safe lookup empty qaytaradi
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(identityQueryService, never()).getMembershipRoles(any());
    }

    @Test
    void membershipRolesThrowsIllegalArgumentWhenTenantIdNull() {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getMembershipRoles(null, membershipId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void membershipRolesThrowsIllegalArgumentWhenMembershipIdNull() {
        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("membershipId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void membershipRolesCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff111111-1111-1111-1111-111111111111");
        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.getMembershipRoles(membershipId)).thenReturn(List.of());

        facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void membershipRolesNullTenantIdSkipsAuthorization() {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> facade.getMembershipRoles(null, membershipId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void membershipRolesNullMembershipIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void membershipRolesDeniedWhenAuthorizationFails() {
        UUID membershipId = UUID.fromString("ee111111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getMembershipRoles(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getWorkflowDefinitionDetails tests ==========

    @Test
    void workflowDetailsReturnsHeaderStatusesAndTransitionRules() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID s1Id = UUID.fromString("bb111111-1111-1111-1111-111111111111");
        UUID s2Id = UUID.fromString("bb222222-2222-2222-2222-222222222222");
        UUID s3Id = UUID.fromString("bb333333-3333-3333-3333-333333333333");
        UUID r1Id = UUID.fromString("dd111111-1111-1111-1111-111111111111");
        UUID r2Id = UUID.fromString("dd222222-2222-2222-2222-222222222222");
        UUID requiredPermId = UUID.fromString("ee111111-1111-1111-1111-111111111111");

        WorkflowStatus s1 = mock(WorkflowStatus.class);
        when(s1.getId()).thenReturn(s1Id);
        when(s1.getName()).thenReturn("BUGS");
        when(s1.getStatusOrder()).thenReturn(0);
        when(s1.isInitial()).thenReturn(true);
        when(s1.isTerminal()).thenReturn(false);

        WorkflowStatus s2 = mock(WorkflowStatus.class);
        when(s2.getId()).thenReturn(s2Id);
        when(s2.getName()).thenReturn("PROCESSING");
        when(s2.getStatusOrder()).thenReturn(1);
        when(s2.isInitial()).thenReturn(false);
        when(s2.isTerminal()).thenReturn(false);

        WorkflowStatus s3 = mock(WorkflowStatus.class);
        when(s3.getId()).thenReturn(s3Id);
        when(s3.getName()).thenReturn("FIXED");
        when(s3.getStatusOrder()).thenReturn(2);
        when(s3.isInitial()).thenReturn(false);
        when(s3.isTerminal()).thenReturn(true);

        // Sort by statusOrder ASC: s1 (0), s2 (1), s3 (2). Insertion order shuffled.
        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getTenantId()).thenReturn(TENANT_ID);
        when(definition.getName()).thenReturn("Bug Flow");
        when(definition.getWorkItemType()).thenReturn("BUG");
        when(definition.getDescription()).thenReturn("Bug workflow");
        when(definition.isActive()).thenReturn(true);
        when(definition.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(definition.getStatuses()).thenReturn(List.of(s2, s3, s1));

        // Two transition rules: BUGS -> PROCESSING, PROCESSING -> FIXED
        WorkflowTransitionRule r1 = mock(WorkflowTransitionRule.class);
        when(r1.getId()).thenReturn(r1Id);
        when(r1.getFromStatus()).thenReturn(s1);
        when(r1.getToStatus()).thenReturn(s2);
        when(r1.getRequiredPermissionId()).thenReturn(requiredPermId);

        WorkflowTransitionRule r2 = mock(WorkflowTransitionRule.class);
        when(r2.getId()).thenReturn(r2Id);
        when(r2.getFromStatus()).thenReturn(s2);
        when(r2.getToStatus()).thenReturn(s3);
        when(r2.getRequiredPermissionId()).thenReturn(null);

        when(definition.getTransitionRules()).thenReturn(List.of(r2, r1));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.of(definition));

        var result = facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID);

        // Header
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.definitionId()).isEqualTo(definitionId);
        assertThat(result.name()).isEqualTo("Bug Flow");
        assertThat(result.workItemType()).isEqualTo("BUG");
        assertThat(result.description()).isEqualTo("Bug workflow");
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // Statuses ordered by statusOrder ASC
        assertThat(result.statuses()).hasSize(3);
        assertThat(result.statuses().get(0).statusId()).isEqualTo(s1Id);
        assertThat(result.statuses().get(0).name()).isEqualTo("BUGS");
        assertThat(result.statuses().get(0).statusOrder()).isZero();
        assertThat(result.statuses().get(0).initial()).isTrue();
        assertThat(result.statuses().get(0).terminal()).isFalse();
        assertThat(result.statuses().get(1).statusId()).isEqualTo(s2Id);
        assertThat(result.statuses().get(1).name()).isEqualTo("PROCESSING");
        assertThat(result.statuses().get(2).statusId()).isEqualTo(s3Id);
        assertThat(result.statuses().get(2).name()).isEqualTo("FIXED");
        assertThat(result.statuses().get(2).terminal()).isTrue();

        // Transition rules ordered by fromStatus.name ASC: BUGS->PROCESSING, PROCESSING->FIXED
        assertThat(result.transitionRules()).hasSize(2);
        assertThat(result.transitionRules().get(0).ruleId()).isEqualTo(r1Id);
        assertThat(result.transitionRules().get(0).fromStatusId()).isEqualTo(s1Id);
        assertThat(result.transitionRules().get(0).fromStatusName()).isEqualTo("BUGS");
        assertThat(result.transitionRules().get(0).toStatusId()).isEqualTo(s2Id);
        assertThat(result.transitionRules().get(0).toStatusName()).isEqualTo("PROCESSING");
        assertThat(result.transitionRules().get(0).requiredPermissionId()).isEqualTo(requiredPermId);
        assertThat(result.transitionRules().get(1).ruleId()).isEqualTo(r2Id);
        assertThat(result.transitionRules().get(1).fromStatusName()).isEqualTo("PROCESSING");
        assertThat(result.transitionRules().get(1).toStatusName()).isEqualTo("FIXED");
        assertThat(result.transitionRules().get(1).requiredPermissionId()).isNull();
    }

    @Test
    void workflowDetailsStatusTieBreakerByNameThenId() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID sBId = UUID.fromString("bb000000-0000-0000-0000-00000000000b");
        UUID sAId = UUID.fromString("bb000000-0000-0000-0000-00000000000a");

        // Same statusOrder but different names — name ASC tie-breaker
        WorkflowStatus sB = mock(WorkflowStatus.class);
        when(sB.getId()).thenReturn(sBId);
        when(sB.getName()).thenReturn("ZETA");
        when(sB.getStatusOrder()).thenReturn(5);
        when(sB.isInitial()).thenReturn(false);
        when(sB.isTerminal()).thenReturn(false);

        WorkflowStatus sA = mock(WorkflowStatus.class);
        when(sA.getId()).thenReturn(sAId);
        when(sA.getName()).thenReturn("ALPHA");
        when(sA.getStatusOrder()).thenReturn(5);
        when(sA.isInitial()).thenReturn(false);
        when(sA.isTerminal()).thenReturn(false);

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getName()).thenReturn("Wf");
        when(definition.getWorkItemType()).thenReturn("BUG");
        when(definition.getDescription()).thenReturn(null);
        when(definition.isActive()).thenReturn(true);
        when(definition.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(definition.getStatuses()).thenReturn(List.of(sB, sA));
        when(definition.getTransitionRules()).thenReturn(List.of());

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.of(definition));

        var result = facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID);

        assertThat(result.statuses()).hasSize(2);
        assertThat(result.statuses().get(0).name()).isEqualTo("ALPHA");
        assertThat(result.statuses().get(1).name()).isEqualTo("ZETA");
    }

    @Test
    void workflowDetailsTransitionRuleTieBreakerByRuleId() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        UUID sId = UUID.fromString("bb111111-1111-1111-1111-111111111111");
        UUID rB = UUID.fromString("dd000000-0000-0000-0000-00000000000b");
        UUID rA = UUID.fromString("dd000000-0000-0000-0000-00000000000a");

        WorkflowStatus sFrom = mock(WorkflowStatus.class);
        when(sFrom.getName()).thenReturn("X");
        when(sFrom.getId()).thenReturn(sId);

        WorkflowStatus sTo = mock(WorkflowStatus.class);
        when(sTo.getName()).thenReturn("Y");
        when(sTo.getId()).thenReturn(UUID.fromString("bb222222-2222-2222-2222-222222222222"));

        // Identical from/to names — tie-breaker on ruleId
        WorkflowTransitionRule ruleB = mock(WorkflowTransitionRule.class);
        when(ruleB.getId()).thenReturn(rB);
        when(ruleB.getFromStatus()).thenReturn(sFrom);
        when(ruleB.getToStatus()).thenReturn(sTo);
        when(ruleB.getRequiredPermissionId()).thenReturn(null);

        WorkflowTransitionRule ruleA = mock(WorkflowTransitionRule.class);
        when(ruleA.getId()).thenReturn(rA);
        when(ruleA.getFromStatus()).thenReturn(sFrom);
        when(ruleA.getToStatus()).thenReturn(sTo);
        when(ruleA.getRequiredPermissionId()).thenReturn(null);

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getName()).thenReturn("Wf");
        when(definition.getWorkItemType()).thenReturn("BUG");
        when(definition.getDescription()).thenReturn(null);
        when(definition.isActive()).thenReturn(true);
        when(definition.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(definition.getStatuses()).thenReturn(List.of());
        when(definition.getTransitionRules()).thenReturn(List.of(ruleB, ruleA));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.of(definition));

        var result = facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID);

        assertThat(result.transitionRules()).hasSize(2);
        // ruleA (...0a) ruleB (...0b) dan oldin
        assertThat(result.transitionRules().get(0).ruleId()).isEqualTo(rA);
        assertThat(result.transitionRules().get(1).ruleId()).isEqualTo(rB);
    }

    @Test
    void workflowDetailsReturnsEmptyListsWhenNoneExist() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getName()).thenReturn("Empty Wf");
        when(definition.getWorkItemType()).thenReturn("TASK");
        when(definition.getDescription()).thenReturn(null);
        when(definition.isActive()).thenReturn(false);
        when(definition.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(definition.getStatuses()).thenReturn(List.of());
        when(definition.getTransitionRules()).thenReturn(List.of());

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.of(definition));

        var result = facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID);

        assertThat(result.statuses()).isEmpty();
        assertThat(result.transitionRules()).isEmpty();
        assertThat(result.active()).isFalse();
        assertThat(result.workItemType()).isEqualTo("TASK");
    }

    @Test
    void workflowDetailsThrowsResourceNotFoundWhenTenantMissing() {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).findWorkflowDefinitionById(any(), any());
    }

    @Test
    void workflowDetailsThrowsResourceNotFoundWhenDefinitionMissing() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("WorkflowDefinition");
    }

    @Test
    void workflowDetailsCrossTenantReturnsNotFound() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");

        // Tenant-safe lookup empty for foreign tenant — same NOT FOUND semantics
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("WorkflowDefinition");
    }

    @Test
    void workflowDetailsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(null, definitionId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void workflowDetailsThrowsIllegalArgumentWhenDefinitionIdNull() {
        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void workflowDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");

        WorkflowDefinition definition = mock(WorkflowDefinition.class);
        when(definition.getId()).thenReturn(definitionId);
        when(definition.getName()).thenReturn("Wf");
        when(definition.getWorkItemType()).thenReturn("BUG");
        when(definition.getDescription()).thenReturn(null);
        when(definition.isActive()).thenReturn(true);
        when(definition.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(definition.getStatuses()).thenReturn(List.of());
        when(definition.getTransitionRules()).thenReturn(List.of());

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findWorkflowDefinitionById(TENANT_ID, definitionId))
                .thenReturn(Optional.of(definition));

        facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void workflowDetailsNullTenantIdSkipsAuthorization() {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(null, definitionId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void workflowDetailsNullDefinitionIdSkipsAuthorization() {
        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void workflowDetailsDeniedWhenAuthorizationFails() {
        UUID definitionId = UUID.fromString("aa111111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() ->
                facade.getWorkflowDefinitionDetails(TENANT_ID, definitionId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getRoutingRuleDetails tests ==========

    @Test
    void routingRuleDetailsReturnsHeaderWithTargetContext() {
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        UUID topicBindingId = UUID.fromString("bb999999-9999-9999-9999-999999999991");
        UUID chatBindingId = UUID.fromString("cc999999-9999-9999-9999-999999999991");

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getTenantId()).thenReturn(TENANT_ID);
        when(rule.getName()).thenReturn("Bug Routing");
        when(rule.getWorkItemType()).thenReturn("BUG");
        when(rule.getPriority()).thenReturn(10);
        when(rule.getConditionExpression()).thenReturn(null);
        when(rule.isActive()).thenReturn(true);
        when(rule.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1001234567890L);
        when(chatBinding.getChatTitle()).thenReturn("Engineering Chat");
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.getTopicId()).thenReturn(123L);
        when(topicBinding.getTopicName()).thenReturn("Bugs Topic");
        when(topicBinding.getPurpose()).thenReturn("BUGS");
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.of(rule));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        var result = facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID);

        // Header
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.ruleId()).isEqualTo(ruleId);
        assertThat(result.name()).isEqualTo("Bug Routing");
        assertThat(result.workItemType()).isEqualTo("BUG");
        assertThat(result.priority()).isEqualTo(10);
        assertThat(result.conditionExpression()).isNull();
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // Nested target context
        assertThat(result.targetTopicBinding()).isNotNull();
        assertThat(result.targetTopicBinding().topicBindingId()).isEqualTo(topicBindingId);
        assertThat(result.targetTopicBinding().topicId()).isEqualTo(123L);
        assertThat(result.targetTopicBinding().topicName()).isEqualTo("Bugs Topic");
        assertThat(result.targetTopicBinding().purpose()).isEqualTo("BUGS");
        assertThat(result.targetTopicBinding().active()).isTrue();
        assertThat(result.targetTopicBinding().chatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.targetTopicBinding().chatId()).isEqualTo(-1001234567890L);
        assertThat(result.targetTopicBinding().chatTitle()).isEqualTo("Engineering Chat");
        assertThat(result.targetTopicBinding().chatBindingType()).isEqualTo("MAIN_GROUP");
    }

    @Test
    void routingRuleDetailsReturnsHeaderWithoutTargetWhenTargetIdNull() {
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getName()).thenReturn("Conditional Rule");
        when(rule.getWorkItemType()).thenReturn("INCIDENT");
        when(rule.getPriority()).thenReturn(5);
        when(rule.getConditionExpression()).thenReturn("priority == HIGH");
        when(rule.isActive()).thenReturn(false);
        when(rule.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-02-01T08:00:00Z"));
        when(rule.getTargetTopicBindingId()).thenReturn(null);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.of(rule));

        var result = facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID);

        assertThat(result.targetTopicBinding()).isNull();
        assertThat(result.conditionExpression()).isEqualTo("priority == HIGH");
        assertThat(result.active()).isFalse();

        // Topic binding lookup'i shaqirilmasligi kerak
        verify(tenantConfigQueryService, never()).findTopicBindingById(any(), any());
    }

    @Test
    void routingRuleDetailsThrowsResourceNotFoundWhenTenantMissing() {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).findRoutingRuleById(any(), any());
    }

    @Test
    void routingRuleDetailsThrowsResourceNotFoundWhenRuleMissing() {
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RoutingRule");
    }

    @Test
    void routingRuleDetailsThrowsResourceNotFoundWhenTargetTopicBindingMissing() {
        // Routing rule mavjud, lekin targetTopicBindingId tomonidan ko'rsatilgan
        // topic binding tenant ichida yo'q (dangling reference) — 404 bo'lishi shart,
        // silent omit qilinmasligi kerak.
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        UUID targetId = UUID.fromString("bb999999-9999-9999-9999-999999999991");

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getTargetTopicBindingId()).thenReturn(targetId);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.of(rule));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, targetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TopicBinding")
                .hasMessageContaining(targetId.toString());

        // Authorization allaqachon o'tgan bo'lishi kerak — downstream lookup ishladi
        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
        verify(tenantConfigQueryService).findRoutingRuleById(TENANT_ID, ruleId);
        verify(tenantConfigQueryService).findTopicBindingById(TENANT_ID, targetId);
    }

    @Test
    void routingRuleDetailsCrossTenantReturnsNotFound() {
        // Boshqa tenantga tegishli routing rule — tenant-safe lookup empty qaytaradi
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RoutingRule");
    }

    @Test
    void routingRuleDetailsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(null, ruleId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void routingRuleDetailsThrowsIllegalArgumentWhenRuleIdNull() {
        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void routingRuleDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getName()).thenReturn("Rule");
        when(rule.getWorkItemType()).thenReturn("BUG");
        when(rule.getPriority()).thenReturn(0);
        when(rule.getConditionExpression()).thenReturn(null);
        when(rule.isActive()).thenReturn(true);
        when(rule.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(rule.getTargetTopicBindingId()).thenReturn(null);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findRoutingRuleById(TENANT_ID, ruleId))
                .thenReturn(Optional.of(rule));

        facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void routingRuleDetailsNullTenantIdSkipsAuthorization() {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(null, ruleId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void routingRuleDetailsNullRuleIdSkipsAuthorization() {
        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void routingRuleDetailsDeniedWhenAuthorizationFails() {
        UUID ruleId = UUID.fromString("aa999999-9999-9999-9999-999999999991");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() ->
                facade.getRoutingRuleDetails(TENANT_ID, ruleId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getChatBindingDetails tests ==========

    @Test
    void chatBindingDetailsReturnsHeaderWithNestedTopicBindings() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        UUID t1Id = UUID.fromString("99991111-1111-1111-1111-111111111111");
        UUID t2Id = UUID.fromString("99992222-2222-2222-2222-222222222222");
        UUID t3Id = UUID.fromString("99993333-3333-3333-3333-333333333333");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1001234567890L);
        when(chatBinding.getChatTitle()).thenReturn("Engineering Chat");
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(chatBinding.isActive()).thenReturn(true);
        when(chatBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // purpose ASC -> topicId ASC -> id ASC
        TelegramTopicBinding t1 = mock(TelegramTopicBinding.class);
        when(t1.getId()).thenReturn(t1Id);
        when(t1.getTopicId()).thenReturn(101L);
        when(t1.getTopicName()).thenReturn("Bugs");
        when(t1.getPurpose()).thenReturn("BUGS");
        when(t1.isActive()).thenReturn(true);
        when(t1.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-16T08:00:00Z"));

        TelegramTopicBinding t2 = mock(TelegramTopicBinding.class);
        when(t2.getId()).thenReturn(t2Id);
        when(t2.getTopicId()).thenReturn(202L);
        when(t2.getTopicName()).thenReturn("Incidents");
        when(t2.getPurpose()).thenReturn("INCIDENTS");
        when(t2.isActive()).thenReturn(false);
        when(t2.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-17T08:00:00Z"));

        TelegramTopicBinding t3 = mock(TelegramTopicBinding.class);
        when(t3.getId()).thenReturn(t3Id);
        when(t3.getTopicId()).thenReturn(303L);
        when(t3.getTopicName()).thenReturn(null);
        when(t3.getPurpose()).thenReturn("TASKS");
        when(t3.isActive()).thenReturn(true);
        when(t3.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-18T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.of(chatBinding));
        when(tenantConfigQueryService.listAllTopicBindings(chatBindingId))
                .thenReturn(List.of(t3, t1, t2)); // shuffled input

        var result = facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID);

        // Header
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.chatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.chatId()).isEqualTo(-1001234567890L);
        assertThat(result.chatTitle()).isEqualTo("Engineering Chat");
        assertThat(result.bindingType()).isEqualTo("MAIN_GROUP");
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // Nested ordering by purpose ASC: BUGS, INCIDENTS, TASKS
        assertThat(result.topicBindings()).hasSize(3);
        assertThat(result.topicBindings().get(0).topicBindingId()).isEqualTo(t1Id);
        assertThat(result.topicBindings().get(0).purpose()).isEqualTo("BUGS");
        assertThat(result.topicBindings().get(0).topicId()).isEqualTo(101L);
        assertThat(result.topicBindings().get(0).topicName()).isEqualTo("Bugs");
        assertThat(result.topicBindings().get(0).active()).isTrue();
        assertThat(result.topicBindings().get(1).purpose()).isEqualTo("INCIDENTS");
        assertThat(result.topicBindings().get(1).topicId()).isEqualTo(202L);
        assertThat(result.topicBindings().get(1).active()).isFalse();
        assertThat(result.topicBindings().get(2).purpose()).isEqualTo("TASKS");
        assertThat(result.topicBindings().get(2).topicName()).isNull();
    }

    @Test
    void chatBindingDetailsTopicTieBreakerByTopicIdThenId() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        UUID tBId = UUID.fromString("99990000-0000-0000-0000-00000000000b");
        UUID tAId = UUID.fromString("99990000-0000-0000-0000-00000000000a");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1L);
        when(chatBinding.getChatTitle()).thenReturn(null);
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(chatBinding.isActive()).thenReturn(true);
        when(chatBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // Same purpose, same topicId — tie-breaker on id ASC
        TelegramTopicBinding tB = mock(TelegramTopicBinding.class);
        when(tB.getId()).thenReturn(tBId);
        when(tB.getTopicId()).thenReturn(500L);
        when(tB.getTopicName()).thenReturn("B");
        when(tB.getPurpose()).thenReturn("SAME");
        when(tB.isActive()).thenReturn(true);
        when(tB.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-16T08:00:00Z"));

        TelegramTopicBinding tA = mock(TelegramTopicBinding.class);
        when(tA.getId()).thenReturn(tAId);
        when(tA.getTopicId()).thenReturn(500L);
        when(tA.getTopicName()).thenReturn("A");
        when(tA.getPurpose()).thenReturn("SAME");
        when(tA.isActive()).thenReturn(true);
        when(tA.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-17T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.of(chatBinding));
        when(tenantConfigQueryService.listAllTopicBindings(chatBindingId))
                .thenReturn(List.of(tB, tA));

        var result = facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID);

        assertThat(result.topicBindings()).hasSize(2);
        // tA (...0a) tB (...0b) dan oldin
        assertThat(result.topicBindings().get(0).topicBindingId()).isEqualTo(tAId);
        assertThat(result.topicBindings().get(1).topicBindingId()).isEqualTo(tBId);
    }

    @Test
    void chatBindingDetailsReturnsHeaderWithEmptyTopicBindings() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-99L);
        when(chatBinding.getChatTitle()).thenReturn(null);
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.NOTIFICATION_GROUP);
        when(chatBinding.isActive()).thenReturn(false);
        when(chatBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.of(chatBinding));
        when(tenantConfigQueryService.listAllTopicBindings(chatBindingId)).thenReturn(List.of());

        var result = facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID);

        assertThat(result.bindingType()).isEqualTo("NOTIFICATION_GROUP");
        assertThat(result.active()).isFalse();
        assertThat(result.chatTitle()).isNull();
        assertThat(result.topicBindings()).isEmpty();
    }

    @Test
    void chatBindingDetailsThrowsResourceNotFoundWhenTenantMissing() {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).findChatBindingById(any(), any());
        verify(tenantConfigQueryService, never()).listAllTopicBindings(any());
    }

    @Test
    void chatBindingDetailsThrowsResourceNotFoundWhenChatBindingMissing() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ChatBinding");

        verify(tenantConfigQueryService, never()).listAllTopicBindings(any());
    }

    @Test
    void chatBindingDetailsCrossTenantReturnsNotFound() {
        // Boshqa tenantga tegishli — tenant-safe lookup empty
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ChatBinding");
    }

    @Test
    void chatBindingDetailsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getChatBindingDetails(null, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void chatBindingDetailsThrowsIllegalArgumentWhenChatBindingIdNull() {
        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatBindingId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void chatBindingDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1L);
        when(chatBinding.getChatTitle()).thenReturn(null);
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);
        when(chatBinding.isActive()).thenReturn(true);
        when(chatBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findChatBindingById(TENANT_ID, chatBindingId))
                .thenReturn(Optional.of(chatBinding));
        when(tenantConfigQueryService.listAllTopicBindings(chatBindingId)).thenReturn(List.of());

        facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void chatBindingDetailsNullTenantIdSkipsAuthorization() {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getChatBindingDetails(null, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void chatBindingDetailsNullChatBindingIdSkipsAuthorization() {
        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void chatBindingDetailsDeniedWhenAuthorizationFails() {
        UUID chatBindingId = UUID.fromString("88991111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() ->
                facade.getChatBindingDetails(TENANT_ID, chatBindingId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getTopicBindingDetails tests ==========

    @Test
    void topicBindingDetailsReturnsHeaderWithParentChatContext() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1001234567890L);
        when(chatBinding.getChatTitle()).thenReturn("Engineering Chat");
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.getTopicId()).thenReturn(101L);
        when(topicBinding.getTopicName()).thenReturn("Bugs Topic");
        when(topicBinding.getPurpose()).thenReturn("BUGS");
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        var result = facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID);

        // Header
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.topicBindingId()).isEqualTo(topicBindingId);
        assertThat(result.topicId()).isEqualTo(101L);
        assertThat(result.topicName()).isEqualTo("Bugs Topic");
        assertThat(result.purpose()).isEqualTo("BUGS");
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // Parent chat context
        assertThat(result.parentChatBinding()).isNotNull();
        assertThat(result.parentChatBinding().chatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.parentChatBinding().chatId()).isEqualTo(-1001234567890L);
        assertThat(result.parentChatBinding().chatTitle()).isEqualTo("Engineering Chat");
        assertThat(result.parentChatBinding().bindingType()).isEqualTo("MAIN_GROUP");
    }

    @Test
    void topicBindingDetailsReturnsHeaderWithNullTopicName() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-99L);
        when(chatBinding.getChatTitle()).thenReturn("Chat");
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.NOTIFICATION_GROUP);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.getTopicId()).thenReturn(202L);
        when(topicBinding.getTopicName()).thenReturn(null);
        when(topicBinding.getPurpose()).thenReturn("INCIDENTS");
        when(topicBinding.isActive()).thenReturn(false);
        when(topicBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        var result = facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID);

        assertThat(result.topicName()).isNull();
        assertThat(result.active()).isFalse();
        assertThat(result.purpose()).isEqualTo("INCIDENTS");
        assertThat(result.parentChatBinding().bindingType()).isEqualTo("NOTIFICATION_GROUP");
    }

    @Test
    void topicBindingDetailsReturnsHeaderWithNullParentChatTitle() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-99L);
        when(chatBinding.getChatTitle()).thenReturn(null);
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.getTopicId()).thenReturn(303L);
        when(topicBinding.getTopicName()).thenReturn("Tasks Topic");
        when(topicBinding.getPurpose()).thenReturn("TASKS");
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        var result = facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID);

        assertThat(result.parentChatBinding().chatTitle()).isNull();
        assertThat(result.parentChatBinding().chatId()).isEqualTo(-99L);
        assertThat(result.topicName()).isEqualTo("Tasks Topic");
    }

    @Test
    void topicBindingDetailsThrowsResourceNotFoundWhenTenantMissing() {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(tenantConfigQueryService, never()).findTopicBindingById(any(), any());
    }

    @Test
    void topicBindingDetailsThrowsResourceNotFoundWhenTopicBindingMissing() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TopicBinding");
    }

    @Test
    void topicBindingDetailsCrossTenantReturnsNotFound() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TopicBinding");
    }

    @Test
    void topicBindingDetailsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(null, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void topicBindingDetailsThrowsIllegalArgumentWhenTopicBindingIdNull() {
        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicBindingId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void topicBindingDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        UUID chatBindingId = UUID.fromString("aabb2222-2222-2222-2222-222222222222");

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.getChatId()).thenReturn(-1L);
        when(chatBinding.getChatTitle()).thenReturn(null);
        when(chatBinding.getBindingType()).thenReturn(ChatBindingType.MAIN_GROUP);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.getTopicId()).thenReturn(1L);
        when(topicBinding.getTopicName()).thenReturn(null);
        when(topicBinding.getPurpose()).thenReturn("X");
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.findTopicBindingById(TENANT_ID, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void topicBindingDetailsNullTenantIdSkipsAuthorization() {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(null, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void topicBindingDetailsNullTopicBindingIdSkipsAuthorization() {
        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void topicBindingDetailsDeniedWhenAuthorizationFails() {
        UUID topicBindingId = UUID.fromString("aabb1111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() ->
                facade.getTopicBindingDetails(TENANT_ID, topicBindingId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== getMembershipDetails tests ==========

    @Test
    void membershipDetailsReturnsHeaderWithUserIdentity() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(membership.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getTelegramUserId()).thenReturn(123456789L);
        when(user.getDisplayName()).thenReturn("Engineer One");
        when(user.getUsername()).thenReturn("eng_one");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.findUserById(userId)).thenReturn(Optional.of(user));

        var result = facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID);

        // Header
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.membershipId()).isEqualTo(membershipId);
        assertThat(result.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(result.createdAt()).isEqualTo(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        // User identity
        assertThat(result.userIdentity()).isNotNull();
        assertThat(result.userIdentity().userId()).isEqualTo(userId);
        assertThat(result.userIdentity().telegramUserId()).isEqualTo(123456789L);
        assertThat(result.userIdentity().displayName()).isEqualTo("Engineer One");
        assertThat(result.userIdentity().username()).isEqualTo("eng_one");
    }

    @Test
    void membershipDetailsReturnsHeaderWithNullDisplayNameAndUsername() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.SUSPENDED);
        when(membership.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-02-01T08:00:00Z"));

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getTelegramUserId()).thenReturn(987654321L);
        when(user.getDisplayName()).thenReturn(null);
        when(user.getUsername()).thenReturn(null);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.findUserById(userId)).thenReturn(Optional.of(user));

        var result = facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID);

        assertThat(result.membershipStatus()).isEqualTo("SUSPENDED");
        assertThat(result.userIdentity().displayName()).isNull();
        assertThat(result.userIdentity().username()).isNull();
        assertThat(result.userIdentity().telegramUserId()).isEqualTo(987654321L);
    }

    @Test
    void membershipDetailsThrowsResourceNotFoundWhenTenantMissing() {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(tenantConfigQueryService).findTenantById(TENANT_ID);
        verify(identityQueryService, never()).findMembershipByIdAndTenantId(any(), any());
        verify(identityQueryService, never()).findUserById(any());
    }

    @Test
    void membershipDetailsThrowsResourceNotFoundWhenMembershipMissing() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(identityQueryService, never()).findUserById(any());
    }

    @Test
    void membershipDetailsCrossTenantReturnsNotFound() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");
    }

    @Test
    void membershipDetailsThrowsResourceNotFoundWhenUserOrphan() {
        // Membership topildi, lekin AppUser orphan — admin'ga 404 sifatida ko'rsatiladi
        // (defensive omit emas, dangling reference invariant buzilgan)
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(membership.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.findUserById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(userId.toString());
    }

    @Test
    void membershipDetailsThrowsIllegalArgumentWhenTenantIdNull() {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getMembershipDetails(null, membershipId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void membershipDetailsThrowsIllegalArgumentWhenMembershipIdNull() {
        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("membershipId");

        verifyNoInteractions(authorizationService, tenantConfigQueryService, identityQueryService);
    }

    @Test
    void membershipDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("ff881111-1111-1111-1111-111111111111");

        Membership membership = mock(Membership.class);
        when(membership.getId()).thenReturn(membershipId);
        when(membership.getUserId()).thenReturn(userId);
        when(membership.getStatus())
                .thenReturn(com.engops.platform.identity.model.MembershipStatus.ACTIVE);
        when(membership.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-15T08:00:00Z"));

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getTelegramUserId()).thenReturn(1L);
        when(user.getDisplayName()).thenReturn(null);
        when(user.getUsername()).thenReturn(null);

        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.findMembershipByIdAndTenantId(membershipId, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.findUserById(userId)).thenReturn(Optional.of(user));

        facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void membershipDetailsNullTenantIdSkipsAuthorization() {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        assertThatThrownBy(() ->
                facade.getMembershipDetails(null, membershipId, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void membershipDetailsNullMembershipIdSkipsAuthorization() {
        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void membershipDetailsDeniedWhenAuthorizationFails() {
        UUID membershipId = UUID.fromString("ee881111-1111-1111-1111-111111111111");
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() ->
                facade.getMembershipDetails(TENANT_ID, membershipId, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
        verifyNoInteractions(identityQueryService);
    }

    // ========== Authorization enforcement ==========

    @Test
    void getDetailsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());
        when(identityQueryService.listActiveMembers(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listAllRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveRoutingRules(TENANT_ID)).thenReturn(List.of());
        when(tenantConfigQueryService.listActiveChatBindings(TENANT_ID)).thenReturn(List.of());

        facade.getDetails(TENANT_ID, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void getDetailsDeniedWhenAuthorizationFails() {
        doThrow(new com.engops.platform.sharedkernel.exception.AccessDeniedException("Ruxsat yo'q"))
                .when(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);

        assertThatThrownBy(() -> facade.getDetails(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(com.engops.platform.sharedkernel.exception.AccessDeniedException.class);

        verifyNoInteractions(tenantConfigQueryService);
    }

    @Test
    void getWorkflowDefinitionsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllWorkflowDefinitions(TENANT_ID)).thenReturn(List.of());

        facade.getWorkflowDefinitions(TENANT_ID, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void getChatBindingsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantConfigQueryService.listAllChatBindings(TENANT_ID)).thenReturn(List.of());

        facade.getChatBindings(TENANT_ID, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void getMembershipsCallsAuthorizeRead() {
        Tenant tenant = mockTenant();
        when(tenantConfigQueryService.findTenantById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(identityQueryService.listAllMembers(TENANT_ID)).thenReturn(List.of());

        facade.getMemberships(TENANT_ID, ACTOR_USER_ID);

        verify(authorizationService).authorizeRead(TENANT_ID, ACTOR_USER_ID);
    }

    // ========== Validation-before-authorization ordering contract ==========

    @Test
    void getDetailsNullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getDetails(null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
    }

    @Test
    void getWorkflowDefinitionsNullTenantIdSkipsAuthorization() {
        assertThatThrownBy(() -> facade.getWorkflowDefinitions(null, ACTOR_USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authorizationService);
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
