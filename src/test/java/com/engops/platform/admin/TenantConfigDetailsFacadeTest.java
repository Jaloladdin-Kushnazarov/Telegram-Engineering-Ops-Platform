package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
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
