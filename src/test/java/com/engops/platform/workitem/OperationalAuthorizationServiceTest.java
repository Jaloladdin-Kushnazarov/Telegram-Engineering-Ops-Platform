package com.engops.platform.workitem;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUserRoleBinding;
import com.engops.platform.identity.model.Permission;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.model.RolePermission;
import com.engops.platform.identity.repository.AppUserRoleBindingRepository;
import com.engops.platform.identity.repository.RolePermissionRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationalAuthorizationService unit testlari (Phase 139 + Phase 216).
 *
 * <p>Per-tenant authorize metodlari (Phase 139 baseline): har biri uchun
 * ruxsat bor → o'tadi, ruxsat yo'q → AccessDeniedException, actorUserId
 * null → AccessDeniedException. Permission resolution mock
 * {@link IdentityQueryService} orqali deterministik.</p>
 *
 * <p>Phase 216 authorizeGlobal rewrite: platform-level
 * {@link AppUserRoleBindingRepository} + {@link RolePermissionRepository}
 * orqali tekshiruv. Eski membership cascade olib tashlandi.</p>
 */
class OperationalAuthorizationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLATFORM_OWNER_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000005");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000099");

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final AppUserRoleBindingRepository appUserRoleBindingRepository =
            mock(AppUserRoleBindingRepository.class);
    private final RolePermissionRepository rolePermissionRepository =
            mock(RolePermissionRepository.class);
    private final OperationalAuthorizationService authService =
            new OperationalAuthorizationService(identityQueryService,
                    appUserRoleBindingRepository, rolePermissionRepository);

    // ========== authorizeIntake ==========

    @Test
    void authorizeIntakePassesWhenWorkItemCreatePresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_CREATE));

        assertThatCode(() -> authService.authorizeIntake(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeIntakeDeniedWhenPermissionSetEmpty() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeIntake(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_CREATE");
    }

    @Test
    void authorizeIntakeDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeIntake(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeIntakeDeniedWhenOnlyTransitionPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_TRANSITION));

        assertThatThrownBy(() -> authService.authorizeIntake(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_CREATE");
    }

    // ========== authorizeTransition ==========

    @Test
    void authorizeTransitionPassesWhenWorkItemTransitionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_TRANSITION));

        assertThatCode(() -> authService.authorizeTransition(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeTransitionDeniedWhenPermissionSetEmpty() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeTransition(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_TRANSITION");
    }

    @Test
    void authorizeTransitionDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeTransition(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeTransitionDeniedWhenOnlyCreatePermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_CREATE));

        assertThatThrownBy(() -> authService.authorizeTransition(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_TRANSITION");
    }

    @Test
    void authorizeTransitionPassesWhenMultiplePermissionsIncludeTransition() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(
                        OperationalAuthorizationService.WORK_ITEM_CREATE,
                        OperationalAuthorizationService.WORK_ITEM_TRANSITION));

        assertThatCode(() -> authService.authorizeTransition(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();
    }

    // ========== authorizeUpdate (Phase 190) ==========

    @Test
    void authorizeUpdatePassesWhenWorkItemUpdatePresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_UPDATE));

        assertThatCode(() -> authService.authorizeUpdate(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeUpdateDeniedWhenPermissionSetEmpty() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeUpdate(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_UPDATE");
    }

    @Test
    void authorizeUpdateDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeUpdate(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeUpdateDeniedWhenOnlyAssignPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_ASSIGN));

        assertThatThrownBy(() -> authService.authorizeUpdate(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_UPDATE");
    }

    // ========== authorizeAssignOwner (Phase 190) ==========

    @Test
    void authorizeAssignOwnerPassesWhenWorkItemAssignPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_ASSIGN));

        assertThatCode(() -> authService.authorizeAssignOwner(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeAssignOwnerDeniedWhenPermissionSetEmpty() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeAssignOwner(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_ASSIGN");
    }

    @Test
    void authorizeAssignOwnerDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeAssignOwner(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeAssignOwnerDeniedWhenOnlyUpdatePermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(OperationalAuthorizationService.WORK_ITEM_UPDATE));

        assertThatThrownBy(() -> authService.authorizeAssignOwner(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WORK_ITEM_ASSIGN");
    }

    // ========== authorizeGlobal (Phase 216 — platform-level binding only) ==========

    @Test
    void authorizeGlobal_nullActor_throwsAccessDenied() {
        assertThatThrownBy(() -> authService.authorizeGlobal(null, "TENANT_ONBOARD"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeGlobal_noBindings_throwsAccessDenied() {
        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_ONBOARD");

        verify(appUserRoleBindingRepository).findByUserId(ACTOR_USER_ID);
    }

    @Test
    void authorizeGlobal_bindingsExist_butNoPermission_throwsAccessDenied() {
        // Pre-build mocks BEFORE any when() chain — avoids Mockito
        // unfinished stubbing detection from nested when() inside argument
        // expressions.
        Role activeRole = activeRole();
        Permission viewPerm = permission("WORK_ITEM_VIEW");
        RolePermission rp = buildRolePermission(activeRole, viewPerm);
        AppUserRoleBinding binding = new AppUserRoleBinding(ACTOR_USER_ID, OTHER_ROLE_ID);

        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(binding));
        when(rolePermissionRepository.findByRoleId(OTHER_ROLE_ID)).thenReturn(List.of(rp));

        assertThatThrownBy(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_ONBOARD");
    }

    @Test
    void authorizeGlobal_platformOwnerBinding_withTenantOnboardPerm_allows() {
        Role activeRole = activeRole();
        Permission onboardPerm = permission("TENANT_ONBOARD");
        RolePermission rp = buildRolePermission(activeRole, onboardPerm);
        AppUserRoleBinding binding = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);

        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(binding));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID)).thenReturn(List.of(rp));

        assertThatCode(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .doesNotThrowAnyException();
    }

    @Test
    void authorizeGlobal_multipleBindings_anyHasPerm_allows() {
        Role activeRole = activeRole();
        RolePermission rpOther = buildRolePermission(activeRole, permission("WORK_ITEM_VIEW"));
        RolePermission rpPlatform = buildRolePermission(activeRole, permission("TENANT_ONBOARD"));
        AppUserRoleBinding bindOther = new AppUserRoleBinding(ACTOR_USER_ID, OTHER_ROLE_ID);
        AppUserRoleBinding bindPlatform = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);

        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID))
                .thenReturn(List.of(bindOther, bindPlatform));
        when(rolePermissionRepository.findByRoleId(OTHER_ROLE_ID)).thenReturn(List.of(rpOther));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID)).thenReturn(List.of(rpPlatform));

        assertThatCode(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .doesNotThrowAnyException();
    }

    @Test
    void authorizeGlobal_inactiveRole_isIgnored_throwsAccessDenied() {
        Role inactive = mock(Role.class);
        when(inactive.isActive()).thenReturn(false);
        Permission onboardPerm = permission("TENANT_ONBOARD");
        RolePermission rp = buildRolePermission(inactive, onboardPerm);
        AppUserRoleBinding binding = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);

        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(binding));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID)).thenReturn(List.of(rp));

        assertThatThrownBy(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_ONBOARD");
    }

    @Test
    void authorizeGlobal_roleWithNoPermissions_throwsAccessDenied() {
        // Edge: binding mavjud, role aktiv, lekin role_permission rows bo'sh.
        AppUserRoleBinding binding = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);
        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(binding));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_ONBOARD");
    }

    @Test
    void authorizeGlobal_doesNotCallIdentityQueryService_orMembershipRepository() {
        // Regression: yangi yo'l membership cascade'ni ishlatmasligi shart.
        // identityQueryService va membershipRepository chaqirilmasligini
        // tasdiqlaymiz (membershipRepository hatto endi inject ham qilinmagan).
        AppUserRoleBinding binding = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);
        Role activeRole = activeRole();
        RolePermission rp = buildRolePermission(activeRole, permission("TENANT_ONBOARD"));
        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(binding));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID)).thenReturn(List.of(rp));

        authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD");

        // identityQueryService — per-tenant metodlar uchun mavjud, lekin
        // authorizeGlobal yo'lida chaqirilmaydi. Test'da @Mock bo'sh,
        // chaqirilsa default return qiladi — verifyNoInteractions strict.
        verify(appUserRoleBindingRepository).findByUserId(ACTOR_USER_ID);
        verify(rolePermissionRepository).findByRoleId(PLATFORM_OWNER_ROLE_ID);
        org.mockito.Mockito.verifyNoInteractions(identityQueryService);
    }

    @Test
    void authorizeGlobal_secondMatchingRoleSkipped_afterFirstAllows() {
        // 2 ta binding, ikkala roleda ham TENANT_ONBOARD — birinchi
        // match yetarli, allow.
        AppUserRoleBinding b1 = new AppUserRoleBinding(ACTOR_USER_ID, PLATFORM_OWNER_ROLE_ID);
        AppUserRoleBinding b2 = new AppUserRoleBinding(ACTOR_USER_ID, OTHER_ROLE_ID);
        Role activeRole = activeRole();
        RolePermission rp1 = buildRolePermission(activeRole, permission("TENANT_ONBOARD"));

        when(appUserRoleBindingRepository.findByUserId(ACTOR_USER_ID)).thenReturn(List.of(b1, b2));
        when(rolePermissionRepository.findByRoleId(PLATFORM_OWNER_ROLE_ID)).thenReturn(List.of(rp1));

        assertThatCode(() -> authService.authorizeGlobal(ACTOR_USER_ID, "TENANT_ONBOARD"))
                .doesNotThrowAnyException();
        // Second role permissions ne'er fetched — short-circuit on first match
        verify(rolePermissionRepository).findByRoleId(PLATFORM_OWNER_ROLE_ID);
        org.mockito.Mockito.verify(rolePermissionRepository,
                org.mockito.Mockito.never()).findByRoleId(OTHER_ROLE_ID);
    }

    // ========== Helpers (Phase 216 authorizeGlobal tests) ==========

    private static Role activeRole() {
        Role role = mock(Role.class);
        when(role.isActive()).thenReturn(true);
        return role;
    }

    private static Permission permission(String code) {
        Permission p = mock(Permission.class);
        when(p.getCode()).thenReturn(code);
        return p;
    }

    private static RolePermission buildRolePermission(Role role, Permission permission) {
        RolePermission rp = mock(RolePermission.class);
        when(rp.getRole()).thenReturn(role);
        when(rp.getPermission()).thenReturn(permission);
        return rp;
    }
}
