package com.engops.platform.dev;

import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 211 — DevBootstrapInitializer seed + idempotency tests.
 *
 * <p>Test profile H2 + Flyway off bo'lgani uchun V2 ADMIN role seed
 * avtomatik kelmaydi — {@link #ensureAdminRole()} setUp'da qo'lda
 * yaratiladi (Hibernate create-drop schema mavjud).</p>
 *
 * <p>{@code ApplicationRunner} startup'da ishlaydi va birinchi @Test'gacha
 * butun seed allaqachon bajarilgan bo'ladi. Idempotency test
 * {@code initializer.run(null)}'ni 2-marta qo'lda chaqirib o'zgarish
 * yo'qligini tasdiqlaydi.</p>
 */
@SpringBootTest(classes = com.engops.platform.EngOpsPlatformApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.dev-mode.enabled=true",
        "app.security.jwt.hmac-secret=test-only-secret-padded-to-be-32-bytes-long-enough"
})
class DevBootstrapInitializerTest {

    @Autowired
    private DevBootstrapInitializer initializer;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private MembershipRoleBindingRepository membershipRoleBindingRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private WorkItemRepository workItemRepository;

    @BeforeEach
    void seedAdminRoleAndBootstrap() {
        // Hibernate H2 schema mavjud, Flyway off — V2 ADMIN role seed
        // bo'lmagan. Bootstrap startup'da graceful skip qildi (log warn).
        // Bu yerda ADMIN row'ni qo'lda yaratamiz va initializer.run(null) ni
        // qayta chaqiramiz — endi to'liq seed bajariladi (admin user fixed
        // UUID'da yo'qligi sababli idempotency guard ham o'tkazib yubormaydi).
        if (roleRepository.findByCode("ADMIN").isEmpty()) {
            roleRepository.save(new Role("ADMIN", "Administrator", true));
        }
        initializer.run(null);
    }

    @Test
    void bootstrap_createsAdminUserWithFixedUuid() {
        assertThat(appUserRepository.findById(
                DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID)).isPresent();
    }

    @Test
    void bootstrap_createsTenantWithFixedUuid() {
        assertThat(tenantRepository.findById(
                DevBootstrapInitializer.BOOTSTRAP_TENANT_ID)).isPresent();
    }

    @Test
    void bootstrap_createsExactly10WorkItems() {
        List<WorkItem> items = workItemRepository.findByTenantIdAndArchivedFalse(
                DevBootstrapInitializer.BOOTSTRAP_TENANT_ID);
        assertThat(items).hasSize(DevBootstrapInitializer.DEMO_WORK_ITEM_COUNT);
    }

    @Test
    @Transactional
    void bootstrap_adminHasAdminRoleBinding() {
        // @Transactional — MembershipRoleBinding.role LAZY ManyToOne, kod
        // chaqirilganda session ochiq bo'lishi shart (LazyInitializationException
        // aks holda).
        var membership = membershipRepository.findByTenantIdAndUserId(
                DevBootstrapInitializer.BOOTSTRAP_TENANT_ID,
                DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID);
        assertThat(membership).isPresent();
        List<MembershipRoleBinding> bindings =
                membershipRoleBindingRepository.findByMembershipId(membership.get().getId());
        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).getRole().getCode()).isEqualTo("ADMIN");
    }

    @Test
    @Transactional
    void bootstrap_idempotent_secondInvocationIsNoOp() {
        long usersBefore = appUserRepository.count();
        long workItemsBefore = workItemRepository.count();

        initializer.run(null);

        assertThat(appUserRepository.count()).isEqualTo(usersBefore);
        assertThat(workItemRepository.count()).isEqualTo(workItemsBefore);
    }

    @Test
    void bootstrap_workItemDistributionMatchesSpec() {
        List<WorkItem> items = workItemRepository.findByTenantIdAndArchivedFalse(
                DevBootstrapInitializer.BOOTSTRAP_TENANT_ID);

        long bugs = items.stream().filter(w -> w.getTypeCode() == WorkItemType.BUG).count();
        long incidents = items.stream().filter(w -> w.getTypeCode() == WorkItemType.INCIDENT).count();
        long tasks = items.stream().filter(w -> w.getTypeCode() == WorkItemType.TASK).count();
        assertThat(bugs).isEqualTo(4);
        assertThat(incidents).isEqualTo(3);
        assertThat(tasks).isEqualTo(3);

        long critical = items.stream().filter(w -> "CRITICAL".equals(w.getSeverityCode())).count();
        long high = items.stream().filter(w -> "HIGH".equals(w.getSeverityCode())).count();
        long medium = items.stream().filter(w -> "MEDIUM".equals(w.getSeverityCode())).count();
        long low = items.stream().filter(w -> "LOW".equals(w.getSeverityCode())).count();
        assertThat(critical).isEqualTo(1);
        assertThat(high).isEqualTo(3);
        assertThat(medium).isEqualTo(4);
        assertThat(low).isEqualTo(2);
    }
}
