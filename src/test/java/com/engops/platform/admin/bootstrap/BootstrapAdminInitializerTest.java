package com.engops.platform.admin.bootstrap;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 143 — bootstrap admin initializer testlari.
 *
 * <p>Mockito-based unit testlar — service collaborator'lar mock qilinadi va
 * BootstrapAdminInitializer'ning idempotensiyasi, fail-fast validatsiyasi va
 * audit kontrakti tekshiriladi. Spring context bootstrap qilinmaydi (small,
 * deterministic, fast).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapAdminInitializerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final Long TELEGRAM_USER_ID = 12345L;
    private static final String TENANT_NAME = "Acme";
    private static final String TENANT_SLUG = "acme";
    private static final String DISPLAY_NAME = "Operator Admin";

    @Mock private TenantConfigQueryService tenantConfigQueryService;
    @Mock private TenantConfigCommandService tenantConfigCommandService;
    @Mock private IdentityQueryService identityQueryService;
    @Mock private IdentityCommandService identityCommandService;
    @Mock private AuditService auditService;

    private BootstrapProperties properties;
    private BootstrapAdminInitializer initializer;
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @BeforeEach
    void setUp() {
        properties = new BootstrapProperties();
        initializer = new BootstrapAdminInitializer(properties,
                tenantConfigQueryService, tenantConfigCommandService,
                identityQueryService, identityCommandService, auditService);
    }

    private void fillValidProperties() {
        properties.setEnabled(true);
        properties.setTenantName(TENANT_NAME);
        properties.setTenantSlug(TENANT_SLUG);
        properties.setTenantTimezone("UTC");
        properties.setAppUserId(ADMIN_USER_ID);
        properties.setTelegramUserId(TELEGRAM_USER_ID);
        properties.setDisplayName(DISPLAY_NAME);
    }

    private Tenant mockTenant() {
        Tenant t = mock(Tenant.class);
        when(t.getId()).thenReturn(TENANT_ID);
        return t;
    }

    private AppUser mockAppUser() {
        AppUser u = mock(AppUser.class);
        when(u.getId()).thenReturn(ADMIN_USER_ID);
        return u;
    }

    private Membership mockMembership() {
        Membership m = mock(Membership.class);
        when(m.getId()).thenReturn(MEMBERSHIP_ID);
        return m;
    }

    private Role mockAdminRole() {
        Role r = mock(Role.class);
        when(r.getId()).thenReturn(ADMIN_ROLE_ID);
        when(r.getCode()).thenReturn(BootstrapAdminInitializer.ADMIN_ROLE_CODE);
        return r;
    }

    // ========== Disabled / fail-fast validation ==========

    @Test
    void disabledDoesNothing() {
        properties.setEnabled(false);

        initializer.run(args);

        verifyNoInteractions(tenantConfigQueryService, tenantConfigCommandService,
                identityQueryService, identityCommandService, auditService);
    }

    @Test
    void enabledWithMissingTenantNameFailsFast() {
        fillValidProperties();
        properties.setTenantName(null);

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant-name");

        verifyNoInteractions(tenantConfigCommandService, identityCommandService, auditService);
    }

    @Test
    void enabledWithMissingTenantSlugFailsFast() {
        fillValidProperties();
        properties.setTenantSlug("   ");

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant-slug");

        verifyNoInteractions(tenantConfigCommandService, identityCommandService, auditService);
    }

    @Test
    void enabledWithMissingAppUserIdFailsFast() {
        fillValidProperties();
        properties.setAppUserId(null);

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app-user-id");

        verifyNoInteractions(tenantConfigCommandService, identityCommandService, auditService);
    }

    @Test
    void enabledWithMissingTelegramUserIdFailsFast() {
        fillValidProperties();
        properties.setTelegramUserId(null);

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("telegram-user-id");

        verifyNoInteractions(tenantConfigCommandService, identityCommandService, auditService);
    }

    @Test
    void enabledWithMissingDisplayNameFailsFast() {
        fillValidProperties();
        properties.setDisplayName(null);

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("display-name");

        verifyNoInteractions(tenantConfigCommandService, identityCommandService, auditService);
    }

    // ========== Happy path ==========

    @Test
    void enabledCreatesTenantUserMembershipAndAdminBindingWhenAbsent() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG)).thenReturn(Optional.empty());
        when(tenantConfigCommandService.createTenant(TENANT_NAME, TENANT_SLUG, "UTC"))
                .thenReturn(tenant);
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.empty());
        when(identityCommandService.createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME)))
                .thenReturn(user);
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID))
                .thenReturn(List.of()); // hech qanday role hali biriktirilmagan

        initializer.run(args);

        verify(tenantConfigCommandService).createTenant(TENANT_NAME, TENANT_SLUG, "UTC");
        verify(identityCommandService).createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME));
        verify(identityCommandService).createMembership(TENANT_ID, ADMIN_USER_ID);
        verify(identityCommandService).assignRoleToMembership(
                TENANT_ID, MEMBERSHIP_ID, ADMIN_ROLE_ID);
        verify(auditService).recordEvent(
                eq(TENANT_ID),
                eq(BootstrapAdminInitializer.AUDIT_AGGREGATE_TYPE),
                eq(TENANT_ID),
                eq(BootstrapAdminInitializer.AUDIT_ACTION),
                eq(ADMIN_USER_ID),
                eq(BootstrapAdminInitializer.AUDIT_ACTION_SOURCE),
                eq(null),
                eq(TENANT_SLUG));
    }

    // ========== Idempotency ==========

    @Test
    void secondRunIsIdempotentAndDoesNotDuplicate() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        // Hammasi allaqachon mavjud (ikkinchi run)
        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG))
                .thenReturn(Optional.of(tenant));
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.of(user));
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.of(membership));
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        MembershipRoleBinding existingBinding = mock(MembershipRoleBinding.class);
        when(existingBinding.getRole()).thenReturn(adminRole);
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID))
                .thenReturn(List.of(existingBinding));

        initializer.run(args);

        // Hech qanday yangi resurs yaratilmasligi shart.
        verify(tenantConfigCommandService, never()).createTenant(any(), any(), any());
        verify(identityCommandService, never()).createAppUserWithId(any(), any(), any(), any());
        verify(identityCommandService, never()).createMembership(any(), any());
        verify(identityCommandService, never()).assignRoleToMembership(any(), any(), any());
        // Audit BOOTSTRAP_COMPLETED hamon yoziladi — bootstrap "ko'rib chiqildi"
        // signalining audit trail'da paydo bo'lishi muhim (log-friendly contract).
        verify(auditService, times(1)).recordEvent(
                eq(TENANT_ID),
                eq(BootstrapAdminInitializer.AUDIT_AGGREGATE_TYPE),
                eq(TENANT_ID),
                eq(BootstrapAdminInitializer.AUDIT_ACTION),
                eq(ADMIN_USER_ID),
                eq(BootstrapAdminInitializer.AUDIT_ACTION_SOURCE),
                eq(null),
                eq(TENANT_SLUG));
    }

    @Test
    void existingTenantSlugSkipsTenantCreationButEnsuresUserMembershipAndRoleBindingIfNeeded() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        // Tenant mavjud, lekin user/membership/binding yo'q (e.g., admin previously
        // suspended va yangidan tayinlanmoqda).
        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG))
                .thenReturn(Optional.of(tenant));
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.empty());
        when(identityCommandService.createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME)))
                .thenReturn(user);
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID)).thenReturn(List.of());

        initializer.run(args);

        verify(tenantConfigCommandService, never()).createTenant(any(), any(), any());
        verify(identityCommandService).createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME));
        verify(identityCommandService).createMembership(TENANT_ID, ADMIN_USER_ID);
        verify(identityCommandService).assignRoleToMembership(
                TENANT_ID, MEMBERSHIP_ID, ADMIN_ROLE_ID);
    }

    @Test
    void existingAppUserWithSameIdIsReused() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        // Tenant yangi, AppUser mavjud (boshqa tenant'da admin bo'lgan operator),
        // membership va binding yangi kerak (multi-tenant admin support).
        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG)).thenReturn(Optional.empty());
        when(tenantConfigCommandService.createTenant(TENANT_NAME, TENANT_SLUG, "UTC"))
                .thenReturn(tenant);
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.of(user));
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID)).thenReturn(List.of());

        initializer.run(args);

        verify(identityCommandService, never()).createAppUserWithId(
                any(), any(), any(), any());
        verify(tenantConfigCommandService).createTenant(TENANT_NAME, TENANT_SLUG, "UTC");
        verify(identityCommandService).createMembership(TENANT_ID, ADMIN_USER_ID);
        verify(identityCommandService).assignRoleToMembership(
                TENANT_ID, MEMBERSHIP_ID, ADMIN_ROLE_ID);
    }

    // ========== Failure ==========

    @Test
    void failureDuringRoleBindingPropagatesAndAuditEventNotEmitted() {
        // assignRoleToMembership exception tashlasa, audit BOOTSTRAP_COMPLETED
        // yozilmaydi va exception caller'ga (Spring) propagate bo'ladi —
        // @Transactional run() darajasida rollback bo'ladi.
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG)).thenReturn(Optional.empty());
        when(tenantConfigCommandService.createTenant(TENANT_NAME, TENANT_SLUG, "UTC"))
                .thenReturn(tenant);
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.empty());
        when(identityCommandService.createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME)))
                .thenReturn(user);
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID)).thenReturn(List.of());
        when(identityCommandService.assignRoleToMembership(
                TENANT_ID, MEMBERSHIP_ID, ADMIN_ROLE_ID))
                .thenThrow(new RuntimeException("simulated DB failure"));

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated DB failure");

        verifyNoInteractions(auditService);
    }

    @Test
    void enabledFailsFastIfAdminRoleMissingFromCatalog() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();

        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG)).thenReturn(Optional.empty());
        when(tenantConfigCommandService.createTenant(TENANT_NAME, TENANT_SLUG, "UTC"))
                .thenReturn(tenant);
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.empty());
        when(identityCommandService.createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME)))
                .thenReturn(user);
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN role not found");

        verify(identityCommandService, never()).assignRoleToMembership(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    // ========== Audit ==========

    @Test
    void bootstrapWritesCompletedAuditEventWithExactShape() {
        fillValidProperties();
        Tenant tenant = mockTenant();
        AppUser user = mockAppUser();
        Membership membership = mockMembership();
        Role adminRole = mockAdminRole();

        when(tenantConfigQueryService.findTenantBySlug(TENANT_SLUG)).thenReturn(Optional.empty());
        when(tenantConfigCommandService.createTenant(TENANT_NAME, TENANT_SLUG, "UTC"))
                .thenReturn(tenant);
        when(identityQueryService.findUserById(ADMIN_USER_ID)).thenReturn(Optional.empty());
        when(identityCommandService.createAppUserWithId(
                eq(ADMIN_USER_ID), eq(TELEGRAM_USER_ID), any(), eq(DISPLAY_NAME)))
                .thenReturn(user);
        when(identityQueryService.findMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(Optional.empty());
        when(identityCommandService.createMembership(TENANT_ID, ADMIN_USER_ID))
                .thenReturn(membership);
        when(identityQueryService.findRoleByCode(BootstrapAdminInitializer.ADMIN_ROLE_CODE))
                .thenReturn(Optional.of(adminRole));
        when(identityQueryService.getMembershipRoles(MEMBERSHIP_ID)).thenReturn(List.of());

        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();

        ArgumentCaptor<UUID> tenantIdCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> aggregateTypeCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> aggregateIdCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> actionCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> actorCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> sourceCap = ArgumentCaptor.forClass(String.class);

        verify(auditService).recordEvent(
                tenantIdCap.capture(), aggregateTypeCap.capture(), aggregateIdCap.capture(),
                actionCap.capture(), actorCap.capture(), sourceCap.capture(),
                any(), eq(TENANT_SLUG));

        assertThat(tenantIdCap.getValue()).isEqualTo(TENANT_ID);
        assertThat(aggregateTypeCap.getValue()).isEqualTo("TENANT");
        assertThat(aggregateIdCap.getValue()).isEqualTo(TENANT_ID);
        assertThat(actionCap.getValue()).isEqualTo("BOOTSTRAP_COMPLETED");
        assertThat(actorCap.getValue()).isEqualTo(ADMIN_USER_ID);
        assertThat(sourceCap.getValue()).isEqualTo("BOOTSTRAP");
    }
}
