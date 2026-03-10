package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Membership repository testlari.
 * Tenant-scoped so'rovlar va unikal cheklovni tekshiradi.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class MembershipRepositoryTest {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenant;
    private AppUser user;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("Test Tenant", "test-tenant"));
        user = appUserRepository.save(new AppUser(12345L, "Test User"));
    }

    @Test
    void azolikYaratishVaTopish() {
        Membership membership = new Membership(tenant.getId(), user.getId());
        membershipRepository.save(membership);

        Optional<Membership> found = membershipRepository.findByTenantIdAndUserId(
                tenant.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo(tenant.getId());
        assertThat(found.get().getUserId()).isEqualTo(user.getId());
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void takroriyAzolikRadEtilishi() {
        Membership m1 = new Membership(tenant.getId(), user.getId());
        membershipRepository.saveAndFlush(m1);

        Membership m2 = new Membership(tenant.getId(), user.getId());
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(m2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aktivAzolarniRoyxatlash() {
        AppUser user2 = appUserRepository.save(new AppUser(67890L, "User Two"));

        Membership m1 = new Membership(tenant.getId(), user.getId());
        membershipRepository.save(m1);

        Membership m2 = new Membership(tenant.getId(), user2.getId());
        m2.setStatus(MembershipStatus.SUSPENDED);
        membershipRepository.save(m2);

        List<Membership> activeMembers = membershipRepository.findByTenantIdAndStatus(
                tenant.getId(), MembershipStatus.ACTIVE);

        assertThat(activeMembers).hasSize(1);
        assertThat(activeMembers.get(0).getUserId()).isEqualTo(user.getId());
    }

    @Test
    void azolikMavjudliginiTekshirish() {
        Membership m = new Membership(tenant.getId(), user.getId());
        membershipRepository.save(m);

        assertThat(membershipRepository.existsByTenantIdAndUserId(
                tenant.getId(), user.getId())).isTrue();
    }
}
