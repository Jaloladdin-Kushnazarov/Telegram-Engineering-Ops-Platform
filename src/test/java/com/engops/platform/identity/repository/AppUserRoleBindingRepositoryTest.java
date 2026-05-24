package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.AppUser;
import com.engops.platform.identity.model.AppUserRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 215 — {@link AppUserRoleBindingRepository} testlari.
 *
 * <p>Platform-level (tenantsiz) role binding xulq-atvorini tekshiradi:</p>
 * <ul>
 *   <li>save / find / delete</li>
 *   <li>findByUserId — bir foydalanuvchining barcha global rollari</li>
 *   <li>findByUserIdAndRoleId / existsByUserIdAndRoleId — idempotent
 *       seed pattern uchun</li>
 *   <li>UNIQUE(user_id, role_id) constraint enforcement</li>
 * </ul>
 *
 * <p>{@code MembershipRepositoryTest} pattern'ini takrorlaydi: {@code @DataJpaTest}
 * + H2 in-memory (test profile) + {@link JpaAuditingConfig} import.</p>
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class AppUserRoleBindingRepositoryTest {

    @Autowired
    private AppUserRoleBindingRepository appUserRoleBindingRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    private AppUser user;
    private Role roleA;
    private Role roleB;

    @BeforeEach
    void setUp() {
        user = appUserRepository.save(new AppUser(11_000_001L, "Test User"));
        roleA = roleRepository.save(new Role("ROLE_A", "Role A", true));
        roleB = roleRepository.save(new Role("ROLE_B", "Role B", true));
    }

    @Test
    void save_persistsBinding() {
        AppUserRoleBinding binding = new AppUserRoleBinding(user.getId(), roleA.getId());
        AppUserRoleBinding saved = appUserRoleBindingRepository.save(binding);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getRoleId()).isEqualTo(roleA.getId());
    }

    @Test
    void save_setsCreatedAt() {
        Instant before = Instant.now().minusSeconds(1);
        AppUserRoleBinding binding = new AppUserRoleBinding(user.getId(), roleA.getId());
        appUserRoleBindingRepository.save(binding);

        assertThat(binding.getCreatedAt()).isNotNull();
        assertThat(binding.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(binding.getCreatedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    @Test
    void findByUserId_returnsAllBindingsForUser() {
        appUserRoleBindingRepository.save(new AppUserRoleBinding(user.getId(), roleA.getId()));
        appUserRoleBindingRepository.save(new AppUserRoleBinding(user.getId(), roleB.getId()));

        List<AppUserRoleBinding> bindings = appUserRoleBindingRepository.findByUserId(user.getId());

        assertThat(bindings).hasSize(2);
        assertThat(bindings).extracting(AppUserRoleBinding::getRoleId)
                .containsExactlyInAnyOrder(roleA.getId(), roleB.getId());
    }

    @Test
    void findByUserId_returnsEmptyList_whenNoneExist() {
        List<AppUserRoleBinding> bindings = appUserRoleBindingRepository.findByUserId(user.getId());

        assertThat(bindings).isEmpty();
    }

    @Test
    void findByUserId_isUserScoped() {
        AppUser otherUser = appUserRepository.save(new AppUser(11_000_002L, "Other User"));
        appUserRoleBindingRepository.save(new AppUserRoleBinding(user.getId(), roleA.getId()));
        appUserRoleBindingRepository.save(new AppUserRoleBinding(otherUser.getId(), roleA.getId()));

        List<AppUserRoleBinding> userBindings = appUserRoleBindingRepository.findByUserId(user.getId());

        assertThat(userBindings).hasSize(1);
        assertThat(userBindings.get(0).getUserId()).isEqualTo(user.getId());
    }

    @Test
    void existsByUserIdAndRoleId_returnsTrue_whenExists() {
        appUserRoleBindingRepository.save(new AppUserRoleBinding(user.getId(), roleA.getId()));

        boolean exists = appUserRoleBindingRepository.existsByUserIdAndRoleId(
                user.getId(), roleA.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByUserIdAndRoleId_returnsFalse_whenAbsent() {
        boolean exists = appUserRoleBindingRepository.existsByUserIdAndRoleId(
                user.getId(), roleA.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void findByUserIdAndRoleId_returnsBinding_whenExists() {
        AppUserRoleBinding saved = appUserRoleBindingRepository.save(
                new AppUserRoleBinding(user.getId(), roleA.getId()));

        Optional<AppUserRoleBinding> found =
                appUserRoleBindingRepository.findByUserIdAndRoleId(user.getId(), roleA.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByUserIdAndRoleId_returnsEmpty_whenAbsent() {
        Optional<AppUserRoleBinding> found =
                appUserRoleBindingRepository.findByUserIdAndRoleId(user.getId(), roleA.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void uniqueConstraint_preventsDuplicate_userIdAndRoleId() {
        AppUserRoleBinding b1 = new AppUserRoleBinding(user.getId(), roleA.getId());
        appUserRoleBindingRepository.saveAndFlush(b1);

        AppUserRoleBinding b2 = new AppUserRoleBinding(user.getId(), roleA.getId());
        assertThatThrownBy(() -> appUserRoleBindingRepository.saveAndFlush(b2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUser_orDifferentRole_canBindIndependently() {
        AppUser otherUser = appUserRepository.save(new AppUser(11_000_003L, "Other"));
        appUserRoleBindingRepository.saveAndFlush(new AppUserRoleBinding(user.getId(), roleA.getId()));
        // bir xil role boshqa user — OK
        appUserRoleBindingRepository.saveAndFlush(new AppUserRoleBinding(otherUser.getId(), roleA.getId()));
        // bir xil user boshqa role — OK
        appUserRoleBindingRepository.saveAndFlush(new AppUserRoleBinding(user.getId(), roleB.getId()));

        assertThat(appUserRoleBindingRepository.findAll()).hasSize(3);
    }

    @Test
    void delete_removesBinding() {
        AppUserRoleBinding saved = appUserRoleBindingRepository.save(
                new AppUserRoleBinding(user.getId(), roleA.getId()));

        appUserRoleBindingRepository.deleteById(saved.getId());

        assertThat(appUserRoleBindingRepository.findById(saved.getId())).isEmpty();
        assertThat(appUserRoleBindingRepository.findByUserId(user.getId())).isEmpty();
    }
}
