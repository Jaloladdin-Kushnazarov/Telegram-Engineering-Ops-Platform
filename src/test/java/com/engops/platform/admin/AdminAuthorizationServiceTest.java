package com.engops.platform.admin;

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
 * AdminAuthorizationService unit testlari.
 *
 * Tekshiruvlar:
 * - authorizeRead: TENANT_CONFIG_READ ruxsati bor bo'lsa o'tadi
 * - authorizeRead: ruxsat yo'q bo'lsa AccessDeniedException
 * - authorizeRead: actorUserId null bo'lsa AccessDeniedException
 * - authorizeWrite: TENANT_CONFIG_WRITE ruxsati bor bo'lsa o'tadi
 * - authorizeWrite: ruxsat yo'q bo'lsa AccessDeniedException
 * - authorizeWrite: actorUserId null bo'lsa AccessDeniedException
 * - bo'sh permission seti rad etiladi
 * - boshqa ruxsat bor bo'lsa ham kerakli ruxsat yo'q bo'lsa rad etiladi
 */
class AdminAuthorizationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final IdentityQueryService identityQueryService = mock(IdentityQueryService.class);
    private final AdminAuthorizationService authService =
            new AdminAuthorizationService(identityQueryService);

    // ========== authorizeRead ==========

    @Test
    void authorizeReadPassesWhenPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(AdminAuthorizationService.TENANT_CONFIG_READ));

        assertThatCode(() -> authService.authorizeRead(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeReadDeniedWhenPermissionAbsent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeRead(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_CONFIG_READ");
    }

    @Test
    void authorizeReadDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeRead(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeReadDeniedWhenWrongPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of("SOME_OTHER_PERMISSION"));

        assertThatThrownBy(() -> authService.authorizeRead(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_CONFIG_READ");
    }

    // ========== authorizeWrite ==========

    @Test
    void authorizeWritePassesWhenPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(AdminAuthorizationService.TENANT_CONFIG_WRITE));

        assertThatCode(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();

        verify(identityQueryService).resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID);
    }

    @Test
    void authorizeWriteDeniedWhenPermissionAbsent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_CONFIG_WRITE");
    }

    @Test
    void authorizeWriteDeniedWhenActorUserIdNull() {
        assertThatThrownBy(() -> authService.authorizeWrite(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void authorizeWriteDeniedWhenOnlyReadPermissionPresent() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(AdminAuthorizationService.TENANT_CONFIG_READ));

        assertThatThrownBy(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_CONFIG_WRITE");
    }

    @Test
    void authorizeWritePassesWhenMultiplePermissionsIncludeWrite() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(
                        AdminAuthorizationService.TENANT_CONFIG_READ,
                        AdminAuthorizationService.TENANT_CONFIG_WRITE));

        assertThatCode(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .doesNotThrowAnyException();
    }
}
