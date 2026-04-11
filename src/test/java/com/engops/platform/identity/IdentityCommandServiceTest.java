package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IdentityCommandService unit testlari.
 *
 * Tekshiruvlar:
 * - membership activate/suspend muvaffaqiyatli yo'li
 * - membership topilmaganda ResourceNotFoundException
 * - idempotent yo'l (no-op, audit chaqirilmaydi)
 * - audit payload qiymatlari
 */
class IdentityCommandServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888881");
    private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");

    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantConfigQueryService tenantConfigQueryService =
            mock(TenantConfigQueryService.class);
    private final IdentityCommandService service =
            new IdentityCommandService(membershipRepository, auditService, tenantConfigQueryService);

    @BeforeEach
    void stubTenantExists() {
        when(tenantConfigQueryService.findTenantById(TENANT_ID))
                .thenReturn(Optional.of(mock(Tenant.class)));
    }

    private Membership existingMembership(MembershipStatus status) {
        Membership m = new Membership(TENANT_ID, USER_ID);
        m.setStatus(status);
        return m;
    }

    // ========== activateMembership ==========

    @Test
    void activateMembershipSuccessFromSuspended() {
        Membership existing = existingMembership(MembershipStatus.SUSPENDED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.activateMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(membershipRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP"), eq(existing.getId()),
                eq("ACTIVATED"), eq(null), eq("ADMIN_API"),
                eq("SUSPENDED"), eq("ACTIVE"));
    }

    @Test
    void activateMembershipAlreadyActiveIsIdempotent() {
        Membership existing = existingMembership(MembershipStatus.ACTIVE);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        Membership result = service.activateMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateMembershipThrowsResourceNotFoundWhenMembershipMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateMembership(TENANT_ID, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateMembershipUsesTenantSafeLookup() {
        UUID otherTenant = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(tenantConfigQueryService.findTenantById(otherTenant))
                .thenReturn(Optional.of(mock(Tenant.class)));
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, otherTenant))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateMembership(otherTenant, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(membershipRepository).findByIdAndTenantId(MEMBERSHIP_ID, otherTenant);
        verifyNoInteractions(auditService);
    }

    @Test
    void activateMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateMembership(badTenant, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(membershipRepository, never()).findByIdAndTenantId(any(), any());
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void suspendMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendMembership(badTenant, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(membershipRepository, never()).findByIdAndTenantId(any(), any());
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== suspendMembership ==========

    @Test
    void suspendMembershipSuccessFromActive() {
        Membership existing = existingMembership(MembershipStatus.ACTIVE);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.suspendMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        verify(membershipRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP"), eq(existing.getId()),
                eq("SUSPENDED"), eq(null), eq("ADMIN_API"),
                eq("ACTIVE"), eq("SUSPENDED"));
    }

    @Test
    void suspendMembershipAlreadySuspendedIsIdempotent() {
        Membership existing = existingMembership(MembershipStatus.SUSPENDED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        Membership result = service.suspendMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void suspendMembershipThrowsResourceNotFoundWhenMembershipMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendMembership(TENANT_ID, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }
}
