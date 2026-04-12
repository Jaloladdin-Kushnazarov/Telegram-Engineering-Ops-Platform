package com.engops.platform.identity;

import com.engops.platform.audit.AuditService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
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

    private static final UUID ROLE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");

    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final MembershipRoleBindingRepository membershipRoleBindingRepository =
            mock(MembershipRoleBindingRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantConfigQueryService tenantConfigQueryService =
            mock(TenantConfigQueryService.class);
    private final IdentityCommandService service =
            new IdentityCommandService(membershipRepository, membershipRoleBindingRepository,
                    roleRepository, appUserRepository, auditService, tenantConfigQueryService);

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

    // ========== createMembership tests ==========

    @Test
    void createMembershipSuccess() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(false);
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.createMembership(TENANT_ID, USER_ID);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        verify(membershipRepository).save(any(Membership.class));
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), eq(USER_ID + " | ACTIVE"));
    }

    @Test
    void createMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createMembership(badTenant, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(appUserRepository, never()).findById(any());
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createMembershipThrowsUserNotFoundWhenUserMissing() {
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createMembership(TENANT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createMembershipThrowsBusinessRuleWhenDuplicatePreCheck() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createMembership(TENANT_ID, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createMembershipTranslatesDbDuplicateConstraint() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(false);

        var cause = new ConstraintViolationException(
                "duplicate key", new SQLException(),
                "membership_tenant_id_user_id_key");
        when(membershipRepository.save(any(Membership.class)))
                .thenThrow(new DataIntegrityViolationException("unique", cause));

        assertThatThrownBy(() -> service.createMembership(TENANT_ID, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");

        verifyNoInteractions(auditService);
    }

    @Test
    void createMembershipRethrowsUnrelatedIntegrityViolation() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(TENANT_ID, USER_ID)).thenReturn(false);

        var cause = new ConstraintViolationException(
                "other", new SQLException(), "some_other_constraint");
        when(membershipRepository.save(any(Membership.class)))
                .thenThrow(new DataIntegrityViolationException("other", cause));

        assertThatThrownBy(() -> service.createMembership(TENANT_ID, USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(auditService);
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
    void activateMembershipThrowsBusinessRuleWhenRemoved() {
        Membership existing = existingMembership(MembershipStatus.REMOVED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.activateMembership(TENANT_ID, MEMBERSHIP_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REMOVED");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
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
    void suspendMembershipThrowsBusinessRuleWhenRemoved() {
        Membership existing = existingMembership(MembershipStatus.REMOVED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.suspendMembership(TENANT_ID, MEMBERSHIP_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REMOVED");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
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

    // ========== removeMembership tests ==========

    @Test
    void removeMembershipSuccessFromActive() {
        Membership existing = existingMembership(MembershipStatus.ACTIVE);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.removeMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(membershipRepository).save(existing);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP"), eq(existing.getId()),
                eq("REMOVED"), eq(null), eq("ADMIN_API"),
                eq("ACTIVE"), eq("REMOVED"));
    }

    @Test
    void removeMembershipSuccessFromSuspended() {
        Membership existing = existingMembership(MembershipStatus.SUSPENDED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = service.removeMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP"), eq(existing.getId()),
                eq("REMOVED"), eq(null), eq("ADMIN_API"),
                eq("SUSPENDED"), eq("REMOVED"));
    }

    @Test
    void removeMembershipAlreadyRemovedIsIdempotent() {
        Membership existing = existingMembership(MembershipStatus.REMOVED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        Membership result = service.removeMembership(TENANT_ID, MEMBERSHIP_ID);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void removeMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMembership(badTenant, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(membershipRepository, never()).findByIdAndTenantId(any(), any());
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void removeMembershipThrowsResourceNotFoundWhenMembershipMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMembership(TENANT_ID, MEMBERSHIP_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== assignRoleToMembership tests ==========

    private Role existingRole() {
        return new Role("BUG_TRIAGER", "Bug Triager", false);
    }

    private Membership existingMembership() {
        return existingMembership(MembershipStatus.ACTIVE);
    }

    @Test
    void assignRoleToMembershipSuccess() {
        Membership membership = existingMembership();
        Role role = existingRole();

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(membershipRoleBindingRepository.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(false);
        when(membershipRoleBindingRepository.save(any(MembershipRoleBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipRoleBinding result = service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID);

        assertThat(result.getMembership()).isSameAs(membership);
        assertThat(result.getRole()).isSameAs(role);

        verify(membershipRoleBindingRepository).save(any(MembershipRoleBinding.class));
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP_ROLE_BINDING"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), eq("BUG_TRIAGER"));
    }

    @Test
    void assignRoleToMembershipThrowsBusinessRuleWhenMembershipRemoved() {
        Membership membership = existingMembership(MembershipStatus.REMOVED);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REMOVED");

        verify(roleRepository, never()).findById(any());
        verify(membershipRoleBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipStillAllowedWhenMembershipSuspended() {
        Membership membership = existingMembership(MembershipStatus.SUSPENDED);
        Role role = existingRole();

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(membershipRoleBindingRepository.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(false);
        when(membershipRoleBindingRepository.save(any(MembershipRoleBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MembershipRoleBinding result = service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID);

        assertThat(result.getMembership()).isSameAs(membership);
        assertThat(result.getRole()).isSameAs(role);

        verify(membershipRoleBindingRepository).save(any(MembershipRoleBinding.class));
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP_ROLE_BINDING"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), eq("BUG_TRIAGER"));
    }

    @Test
    void assignRoleToMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoleToMembership(badTenant, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(membershipRepository, never()).findByIdAndTenantId(any(), any());
        verify(roleRepository, never()).findById(any());
        verify(membershipRoleBindingRepository, never()).existsByMembershipIdAndRoleId(any(), any());
        verify(membershipRoleBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipThrowsMembershipNotFoundWhenMembershipMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(roleRepository, never()).findById(any());
        verify(membershipRoleBindingRepository, never()).existsByMembershipIdAndRoleId(any(), any());
        verify(membershipRoleBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipThrowsRoleNotFoundWhenRoleMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existingMembership()));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Role");

        verify(membershipRoleBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipThrowsBusinessRuleWhenDuplicatePreCheck() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existingMembership()));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(existingRole()));
        when(membershipRoleBindingRepository.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");

        verify(membershipRoleBindingRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipTranslatesDbDuplicateConstraint() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existingMembership()));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(existingRole()));
        when(membershipRoleBindingRepository.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(false);

        var cause = new ConstraintViolationException(
                "duplicate key", new SQLException(),
                "membership_role_binding_membership_id_role_id_key");
        when(membershipRoleBindingRepository.save(any(MembershipRoleBinding.class)))
                .thenThrow(new DataIntegrityViolationException("unique", cause));

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("allaqachon");

        verifyNoInteractions(auditService);
    }

    @Test
    void assignRoleToMembershipRethrowsUnrelatedIntegrityViolation() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existingMembership()));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(existingRole()));
        when(membershipRoleBindingRepository.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(false);

        var cause = new ConstraintViolationException(
                "other", new SQLException(), "some_other_constraint");
        when(membershipRoleBindingRepository.save(any(MembershipRoleBinding.class)))
                .thenThrow(new DataIntegrityViolationException("other", cause));

        assertThatThrownBy(() -> service.assignRoleToMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(auditService);
    }

    // ========== unassignRoleFromMembership tests ==========

    @Test
    void unassignRoleFromMembershipSuccess() {
        Membership membership = existingMembership();
        Role role = existingRole();
        MembershipRoleBinding binding = new MembershipRoleBinding(membership, role);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(membershipRoleBindingRepository.findByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(Optional.of(binding));

        service.unassignRoleFromMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID);

        verify(membershipRoleBindingRepository).delete(binding);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP_ROLE_BINDING"), eq(binding.getId()),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                eq("BUG_TRIAGER"), eq(null));
    }

    @Test
    void unassignRoleFromMembershipSuccessForSuspendedMembership() {
        Membership membership = existingMembership(MembershipStatus.SUSPENDED);
        Role role = existingRole();
        MembershipRoleBinding binding = new MembershipRoleBinding(membership, role);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(membershipRoleBindingRepository.findByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(Optional.of(binding));

        service.unassignRoleFromMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID);

        verify(membershipRoleBindingRepository).delete(binding);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP_ROLE_BINDING"), eq(binding.getId()),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                eq("BUG_TRIAGER"), eq(null));
    }

    @Test
    void unassignRoleFromMembershipStillAllowedWhenMembershipRemovedCleanupPath() {
        Membership membership = existingMembership(MembershipStatus.REMOVED);
        Role role = existingRole();
        MembershipRoleBinding binding = new MembershipRoleBinding(membership, role);

        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(membership));
        when(membershipRoleBindingRepository.findByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(Optional.of(binding));

        service.unassignRoleFromMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID);

        verify(membershipRoleBindingRepository).delete(binding);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("MEMBERSHIP_ROLE_BINDING"), eq(binding.getId()),
                eq("DELETED"), eq(null), eq("ADMIN_API"),
                eq("BUG_TRIAGER"), eq(null));
    }

    @Test
    void unassignRoleFromMembershipThrowsTenantNotFoundWhenTenantMissing() {
        UUID badTenant = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tenantConfigQueryService.findTenantById(badTenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignRoleFromMembership(badTenant, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant");

        verify(membershipRepository, never()).findByIdAndTenantId(any(), any());
        verify(membershipRoleBindingRepository, never()).findByMembershipIdAndRoleId(any(), any());
        verify(membershipRoleBindingRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unassignRoleFromMembershipThrowsMembershipNotFoundWhenMembershipMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignRoleFromMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Membership");

        verify(membershipRoleBindingRepository, never()).findByMembershipIdAndRoleId(any(), any());
        verify(membershipRoleBindingRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unassignRoleFromMembershipThrowsBindingNotFoundWhenBindingMissing() {
        when(membershipRepository.findByIdAndTenantId(MEMBERSHIP_ID, TENANT_ID))
                .thenReturn(Optional.of(existingMembership()));
        when(membershipRoleBindingRepository.findByMembershipIdAndRoleId(MEMBERSHIP_ID, ROLE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignRoleFromMembership(TENANT_ID, MEMBERSHIP_ID, ROLE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MembershipRoleBinding");

        verify(membershipRoleBindingRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    // ========== createRole tests ==========

    private static final UUID ROLE_ID_FIXED = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");

    @Test
    void createRoleSuccess() {
        when(roleRepository.existsByCode("BUG_REVIEWER")).thenReturn(false);
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.createRole("bug_reviewer", " Bug Reviewer ", " Reviews bugs ");

        assertThat(result.getCode()).isEqualTo("BUG_REVIEWER");
        assertThat(result.getName()).isEqualTo("Bug Reviewer");
        assertThat(result.getDescription()).isEqualTo("Reviews bugs");
        assertThat(result.isSystemRole()).isFalse();
        assertThat(result.isActive()).isTrue();

        verify(roleRepository).save(any(Role.class));
        verify(auditService).recordEvent(
                eq(null), eq("ROLE"), eq(result.getId()),
                eq("CREATED"), eq(null), eq("ADMIN_API"),
                eq(null), eq("BUG_REVIEWER | Bug Reviewer"));
    }

    @Test
    void createRoleNormalizesCodeTrimAndUppercase() {
        when(roleRepository.existsByCode("BUG_REVIEWER")).thenReturn(false);
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.createRole("  bug_reviewer  ", "  Bug Reviewer  ", "  Reviews bugs  ");

        assertThat(result.getCode()).isEqualTo("BUG_REVIEWER");
        assertThat(result.getName()).isEqualTo("Bug Reviewer");
        assertThat(result.getDescription()).isEqualTo("Reviews bugs");

        verify(roleRepository).existsByCode("BUG_REVIEWER");
    }

    @Test
    void createRoleDuplicatePreCheckUsesNormalizedCode() {
        when(roleRepository.existsByCode("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.createRole("  admin  ", "Admin", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ADMIN");

        verify(roleRepository).existsByCode("ADMIN");
        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createRoleTranslatesDbDuplicateConstraint() {
        when(roleRepository.existsByCode("REVIEWER")).thenReturn(false);

        var cause = new ConstraintViolationException(
                "duplicate key", new SQLException(), "role_code_key");
        when(roleRepository.save(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("unique", cause));

        assertThatThrownBy(() -> service.createRole("reviewer", "Reviewer", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REVIEWER");

        verifyNoInteractions(auditService);
    }

    @Test
    void createRoleRethrowsUnrelatedIntegrityViolation() {
        when(roleRepository.existsByCode("TEST")).thenReturn(false);

        var cause = new ConstraintViolationException(
                "other", new SQLException(), "some_other_constraint");
        when(roleRepository.save(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("other", cause));

        assertThatThrownBy(() -> service.createRole("test", "Test", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void createRoleDescriptionNullOrBlankNotStored() {
        when(roleRepository.existsByCode("PLAIN")).thenReturn(false);
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.createRole("plain", "Plain Role", "  ");

        assertThat(result.getDescription()).isNull();
    }

    // ========== updateRole tests ==========

    private Role existingRoleEntity() {
        Role role = new Role("BUG_TRIAGER", "Bug Triager", false);
        role.setDescription("Old description");
        return role;
    }

    @Test
    void updateRoleTrimsProvidedName() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.updateRole(existing.getId(),
                "  New Name  ", true, null, false);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("Old description");

        verify(auditService).recordEvent(
                eq(null), eq("ROLE"), eq(existing.getId()),
                eq("UPDATED"), eq(null), eq("ADMIN_API"),
                eq("Bug Triager | Old description"), eq("New Name | Old description"));
    }

    @Test
    void updateRoleTrimsProvidedDescription() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.updateRole(existing.getId(),
                null, false, "  New desc  ", true);

        assertThat(result.getName()).isEqualTo("Bug Triager");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    void updateRoleSuccessBothFields() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.updateRole(existing.getId(),
                "Updated", true, "Updated desc", true);

        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("Updated desc");
    }

    @Test
    void updateRoleClearsDescriptionWhenExplicitNull() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.updateRole(existing.getId(),
                null, false, null, true);

        assertThat(result.getDescription()).isNull();
        verify(roleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(null), eq("ROLE"), eq(existing.getId()),
                eq("UPDATED"), eq(null), eq("ADMIN_API"),
                eq("Bug Triager | Old description"), eq("Bug Triager"));
    }

    @Test
    void updateRoleClearsDescriptionWhenBlank() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.updateRole(existing.getId(),
                null, false, "  ", true);

        assertThat(result.getDescription()).isNull();
    }

    @Test
    void updateRoleThrowsNotFoundWhenRoleMissing() {
        UUID missingId = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd1");
        when(roleRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(missingId,
                "Name", true, null, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Role");

        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== activateRole tests ==========

    @Test
    void activateRoleSuccess() {
        Role existing = existingRoleEntity();
        existing.setActive(false);
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.activateRole(existing.getId());

        assertThat(result.isActive()).isTrue();
        verify(roleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(null), eq("ROLE"), eq(existing.getId()),
                eq("ACTIVATED"), eq(null), eq("ADMIN_API"),
                eq("false"), eq("true"));
    }

    @Test
    void activateRoleIdempotent() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Role result = service.activateRole(existing.getId());

        assertThat(result.isActive()).isTrue();
        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateRoleThrowsNotFound() {
        UUID missingId = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd1");
        when(roleRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateRole(missingId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    // ========== deactivateRole tests ==========

    @Test
    void deactivateRoleSuccess() {
        Role existing = existingRoleEntity();
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Role result = service.deactivateRole(existing.getId());

        assertThat(result.isActive()).isFalse();
        verify(roleRepository).save(existing);
        verify(auditService).recordEvent(
                eq(null), eq("ROLE"), eq(existing.getId()),
                eq("DEACTIVATED"), eq(null), eq("ADMIN_API"),
                eq("true"), eq("false"));
    }

    @Test
    void deactivateRoleIdempotent() {
        Role existing = existingRoleEntity();
        existing.setActive(false);
        when(roleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Role result = service.deactivateRole(existing.getId());

        assertThat(result.isActive()).isFalse();
        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void deactivateRoleThrowsNotFound() {
        UUID missingId = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd1");
        when(roleRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateRole(missingId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roleRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }
}
