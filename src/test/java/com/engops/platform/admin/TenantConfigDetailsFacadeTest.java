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
