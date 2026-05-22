package com.engops.platform.workitem;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationalAuthorizationService unit testlari (Phase 139).
 *
 * AdminAuthorizationServiceTest pattern'ini hurmat qiladi — har bir authorize
 * metod uchun: ruxsat bor → o'tadi, ruxsat yo'q → AccessDeniedException,
 * actorUserId null → AccessDeniedException, faqat boshqa ruxsat bor →
 * AccessDeniedException. Permission resolution mock IdentityQueryService
 * orqali deterministik.
 */
class OperationalAuthorizationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final OperationalAuthorizationService authService =
            new OperationalAuthorizationService(identityQueryService);

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
}
