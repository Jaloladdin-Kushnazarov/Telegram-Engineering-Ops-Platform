package com.engops.platform.identity.membership;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 219a — {@link MembershipQueryService} unit testlari (Mockito).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipQueryServiceTest {

    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TENANT = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock private IdentityQueryService identityQueryService;
    @Mock private MembershipRepository membershipRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private MembershipRoleBindingRepository membershipRoleBindingRepository;

    @InjectMocks private MembershipQueryService service;

    private void authorized() {
        when(identityQueryService.resolvePermissionCodes(TENANT, ACTOR))
                .thenReturn(Set.of("MEMBER_MANAGE"));
    }

    /** Mocked membership — createdAt'ni (joinedAt manbai) testda boshqarish uchun. */
    private Membership member(UUID userId, Instant createdAt) {
        Membership m = mock(Membership.class);
        when(m.getId()).thenReturn(UUID.randomUUID());
        when(m.getUserId()).thenReturn(userId);
        when(m.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(m.getCreatedAt()).thenReturn(createdAt);
        AppUser user = new AppUser(100L, "User-" + userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        return m;
    }

    @Test
    void listMembers_returnsActiveMembers_sortedByJoinedAt() {
        authorized();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        Membership later = member(u1, Instant.parse("2026-05-20T00:00:00Z"));
        Membership earlier = member(u2, Instant.parse("2026-05-10T00:00:00Z"));
        // Repository unsorted tartibda qaytaradi (later avval).
        when(membershipRepository.findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE))
                .thenReturn(List.of(later, earlier));
        when(membershipRoleBindingRepository.findByMembershipId(any())).thenReturn(List.of());

        List<MemberSummary> result = service.listMembers(ACTOR, TENANT);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).joinedAt()).isBefore(result.get(1).joinedAt());
    }

    @Test
    void listMembers_includesRoleCodeAndName() {
        authorized();
        UUID userId = UUID.randomUUID();
        Membership m = member(userId, Instant.parse("2026-05-10T00:00:00Z"));
        when(membershipRepository.findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m));
        when(membershipRoleBindingRepository.findByMembershipId(m.getId()))
                .thenReturn(List.of(new MembershipRoleBinding(m, new Role("ENGINEER", "Engineer"))));

        List<MemberSummary> result = service.listMembers(ACTOR, TENANT);

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.roleCode()).isEqualTo("ENGINEER");
            assertThat(s.roleName()).isEqualTo("Engineer");
            assertThat(s.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void listMembers_excludesRemovedMembers() {
        authorized();
        when(membershipRepository.findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        service.listMembers(ACTOR, TENANT);

        // Faqat ACTIVE status bo'yicha so'rov — REMOVED/SUSPENDED chiqarib tashlanadi.
        verify(membershipRepository).findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE);
    }

    @Test
    void listMembers_emptyTenant_returnsEmpty() {
        authorized();
        when(membershipRepository.findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(service.listMembers(ACTOR, TENANT)).isEmpty();
    }

    @Test
    void listMembers_unauthorized_throwsAccessDenied() {
        when(identityQueryService.resolvePermissionCodes(TENANT, ACTOR)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.listMembers(ACTOR, TENANT))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(membershipRepository, appUserRepository, membershipRoleBindingRepository);
    }

    @Test
    void listMembers_memberWithoutRole_returnsNone() {
        authorized();
        UUID userId = UUID.randomUUID();
        Membership m = member(userId, Instant.parse("2026-05-10T00:00:00Z"));
        when(membershipRepository.findByTenantIdAndStatus(TENANT, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m));
        when(membershipRoleBindingRepository.findByMembershipId(m.getId())).thenReturn(List.of());

        List<MemberSummary> result = service.listMembers(ACTOR, TENANT);

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.roleCode()).isEqualTo("NONE");
            assertThat(s.roleName()).isEqualTo("No role");
        });
    }
}
