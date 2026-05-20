package com.engops.platform.admin;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private final AuditService auditService = mock(AuditService.class);
    private final AdminAuthorizationService authService =
            new AdminAuthorizationService(identityQueryService, auditService);

    private ArgumentCaptor<String> captureDeniedAuditPayload() {
        ArgumentCaptor<String> newValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, times(1)).recordEventInNewTransaction(
                eq(TENANT_ID),
                eq("ADMIN_API"),
                eq(TENANT_ID),
                eq("ADMIN_AUTH_DENIED"),
                any(),
                eq("ADMIN_API"),
                isNull(),
                newValueCaptor.capture());
        return newValueCaptor;
    }

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

    // ===== Phase 189 — ADMIN_AUTH_DENIED audit assertions =====

    @Test
    void phase189MissingActorWritesAdminAuthDeniedAuditWithMissingActorReason() {
        assertThatThrownBy(() -> authService.authorizeRead(TENANT_ID, null))
                .isInstanceOf(AccessDeniedException.class);

        ArgumentCaptor<String> payload = captureDeniedAuditPayload();
        assertThat(payload.getValue())
                .contains("\"permission\":\"TENANT_CONFIG_READ\"")
                .contains("\"reason\":\"MISSING_ACTOR\"");
    }

    @Test
    void phase189InactiveMembershipWritesAdminAuthDeniedAuditWithPermissionDeniedReason() {
        // Inactive / no membership → resolvedPermissionCodes returns empty.
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> authService.authorizeRead(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        ArgumentCaptor<String> payload = captureDeniedAuditPayload();
        assertThat(payload.getValue())
                .contains("\"permission\":\"TENANT_CONFIG_READ\"")
                .contains("\"reason\":\"PERMISSION_DENIED\"");
    }

    @Test
    void phase189MembershipButMissingPermissionWritesAdminAuthDeniedAuditWithPermissionDeniedReason() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of("SOME_OTHER_PERMISSION"));

        assertThatThrownBy(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        ArgumentCaptor<String> payload = captureDeniedAuditPayload();
        assertThat(payload.getValue())
                .contains("\"permission\":\"TENANT_CONFIG_WRITE\"")
                .contains("\"reason\":\"PERMISSION_DENIED\"");
    }

    @Test
    void phase189AuditWriteFailureDoesNotChangeAuthorizationContract() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Collections.emptySet());
        doThrow(new RuntimeException("simulated audit persistence failure"))
                .when(auditService).recordEventInNewTransaction(
                        any(), anyString(), any(), anyString(), any(), anyString(), any(), any());

        // Audit fail-soft: AccessDeniedException baribir tashlanadi.
        assertThatThrownBy(() -> authService.authorizeWrite(TENANT_ID, ACTOR_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("TENANT_CONFIG_WRITE");

        verify(auditService, times(1)).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void phase189AuthorizedPathDoesNotWriteAdminAuthDeniedAudit() {
        when(identityQueryService.resolvePermissionCodes(TENANT_ID, ACTOR_USER_ID))
                .thenReturn(Set.of(AdminAuthorizationService.TENANT_CONFIG_READ));

        authService.authorizeRead(TENANT_ID, ACTOR_USER_ID);

        verify(auditService, never()).recordEventInNewTransaction(
                any(), anyString(), any(), anyString(), any(), anyString(), any(), any());
    }
}
