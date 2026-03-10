package com.engops.platform.identity;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * IdentityQueryService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class IdentityQueryServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipRoleBindingRepository membershipRoleBindingRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private IdentityQueryService identityQueryService;

    @Test
    void telegramIdBoYichaFoydalanuvchiTopish() {
        AppUser user = new AppUser(12345L, "Test User");
        when(appUserRepository.findByTelegramUserId(12345L)).thenReturn(Optional.of(user));

        Optional<AppUser> result = identityQueryService.findUserByTelegramUserId(12345L);

        assertThat(result).isPresent();
        assertThat(result.get().getTelegramUserId()).isEqualTo(12345L);
    }

    @Test
    void aktivAzolikBorliginiTekshirish() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Membership membership = new Membership(tenantId, userId);

        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.of(membership));

        assertThat(identityQueryService.hasActiveMembership(tenantId, userId)).isTrue();
    }

    @Test
    void azolikYoqBolsa_falsQaytarish() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(Optional.empty());

        assertThat(identityQueryService.hasActiveMembership(tenantId, userId)).isFalse();
    }

    @Test
    void aktivAzolarniRoyxatlash() {
        UUID tenantId = UUID.randomUUID();
        Membership m1 = new Membership(tenantId, UUID.randomUUID());
        Membership m2 = new Membership(tenantId, UUID.randomUUID());

        when(membershipRepository.findByTenantIdAndStatus(tenantId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m1, m2));

        List<Membership> result = identityQueryService.listActiveMembers(tenantId);

        assertThat(result).hasSize(2);
    }

    @Test
    void globalRollarniRoyxatlash() {
        Role r1 = new Role("ADMIN", "Administrator", true);
        Role r2 = new Role("ENGINEER", "Engineer", true);

        when(roleRepository.findAll()).thenReturn(List.of(r1, r2));

        List<Role> result = identityQueryService.listAllRoles();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("ADMIN");
    }

    @Test
    void codeBoYichaRolTopish() {
        Role role = new Role("TESTER", "Tester", true);
        when(roleRepository.findByCode("TESTER")).thenReturn(Optional.of(role));

        Optional<Role> result = identityQueryService.findRoleByCode("TESTER");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("TESTER");
    }
}
