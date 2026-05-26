package com.engops.platform.dev;

import com.engops.platform.identity.model.MembershipRoleBinding;
import com.engops.platform.identity.model.Role;
import com.engops.platform.identity.repository.AppUserRepository;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.identity.repository.MembershipRoleBindingRepository;
import com.engops.platform.identity.repository.RoleRepository;
import com.engops.platform.tenantconfig.model.WorkflowStatus;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.tenantconfig.repository.WorkflowStatusRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemCounter;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemCounterRepository;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired
    private WorkflowStatusRepository workflowStatusRepository;
    @Autowired
    private WorkItemCounterRepository workItemCounterRepository;

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

    // ========== Phase 221 — workflow status seed ==========

    @Test
    @Transactional
    void bootstrap_seedsExpectedStatusesPerWorkflow() {
        assertWorkflowStatuses(WorkItemType.BUG,
                List.of("REPORTED", "IN_PROGRESS", "RESOLVED", "CLOSED"), "REPORTED", "CLOSED");
        assertWorkflowStatuses(WorkItemType.INCIDENT,
                List.of("REPORTED", "IN_PROGRESS", "RESOLVED"), "REPORTED", "RESOLVED");
        assertWorkflowStatuses(WorkItemType.TASK,
                List.of("REPORTED", "IN_PROGRESS", "DONE"), "REPORTED", "DONE");
    }

    @Test
    @Transactional
    void bootstrap_idempotent_statusCountUnchangedOnSecondRun() {
        long before = workflowStatusRepository.count();
        assertThat(before).isEqualTo(10);  // 4 (bug) + 3 (incident) + 3 (task)

        initializer.run(null);

        assertThat(workflowStatusRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void bootstrap_repairsMissingStatuses_onExistingData() {
        // Simulate "DB has workflows but no statuses" — Phase 211 holati.
        UUID bugWfId = workflowDefinitionRepository
                .findByTenantIdAndWorkItemType(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, WorkItemType.BUG.name())
                .orElseThrow().getId();
        workflowStatusRepository.deleteAll(statusesOf(WorkItemType.BUG));
        workflowStatusRepository.flush();
        assertThat(workflowStatusRepository
                .existsByWorkflowDefinition_IdAndName(bugWfId, "REPORTED")).isFalse();

        // Repair path: run() → repairDemoWorkflowStatuses() backfill qiladi.
        initializer.run(null);

        assertWorkflowStatuses(WorkItemType.BUG,
                List.of("REPORTED", "IN_PROGRESS", "RESOLVED", "CLOSED"), "REPORTED", "CLOSED");
    }

    /** Berilgan type'ning demo workflow status'larini DB'dan toza o'qiydi. */
    private List<WorkflowStatus> statusesOf(WorkItemType type) {
        UUID wfId = workflowDefinitionRepository
                .findByTenantIdAndWorkItemType(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, type.name())
                .orElseThrow().getId();
        return workflowStatusRepository.findAll().stream()
                .filter(s -> wfId.equals(s.getWorkflowDefinition().getId()))
                .toList();
    }

    private void assertWorkflowStatuses(WorkItemType type, List<String> expectedNames,
                                        String expectedInitial, String expectedTerminal) {
        List<WorkflowStatus> statuses = statusesOf(type);
        assertThat(statuses).extracting(WorkflowStatus::getName)
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(statuses).filteredOn(WorkflowStatus::isInitial)
                .extracting(WorkflowStatus::getName)
                .containsExactly(expectedInitial);
        assertThat(statuses).filteredOn(WorkflowStatus::isTerminal)
                .extracting(WorkflowStatus::getName)
                .containsExactly(expectedTerminal);
    }

    // ========== Phase 222 — work_item_counter advance ==========

    @Test
    @Transactional
    void bootstrap_seedsCounterAdvancedPastBugCodes() {
        // BUG-1, BUG-3, BUG-6, BUG-9 → counter.nextValue >= 10
        assertCounterNextValueAtLeast(WorkItemType.BUG, 10);
    }

    @Test
    @Transactional
    void bootstrap_seedsCounterAtDefaultForIncidentBecausePrefixMismatch() {
        // demoSpecs "INC-N" ishlatadi, generator "INCIDENT-N" beradi — advance shart emas.
        // Counter qatori baribir yaratiladi (symmetry), nextValue = 1.
        assertCounterNextValueEquals(WorkItemType.INCIDENT, 1);
    }

    @Test
    @Transactional
    void bootstrap_seedsCounterAdvancedPastTaskCodes() {
        // TASK-4, TASK-7, TASK-10 → counter.nextValue >= 11
        assertCounterNextValueAtLeast(WorkItemType.TASK, 11);
    }

    @Test
    @Transactional
    void bootstrap_idempotent_counterNotRolledBackOnSecondRun() {
        WorkItemCounter bugCounter = workItemCounterRepository
                .findByTenantIdAndTypeCode(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, WorkItemType.BUG)
                .orElseThrow();
        bugCounter.advanceTo(50L);
        workItemCounterRepository.saveAndFlush(bugCounter);

        initializer.run(null);

        WorkItemCounter after = workItemCounterRepository
                .findByTenantIdAndTypeCode(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, WorkItemType.BUG)
                .orElseThrow();
        assertThat(after.getNextValue()).isEqualTo(50L);
    }

    @Test
    @Transactional
    void bootstrap_repairsMissingCounter_onExistingData() {
        // "DB'da work item bor, lekin counter yo'q" holatini simulyatsiya — Phase 211 holati.
        workItemCounterRepository.deleteAll();
        workItemCounterRepository.flush();

        initializer.run(null);

        assertCounterNextValueAtLeast(WorkItemType.BUG, 10);
        assertCounterNextValueAtLeast(WorkItemType.TASK, 11);
        assertCounterNextValueEquals(WorkItemType.INCIDENT, 1);
    }

    private void assertCounterNextValueAtLeast(WorkItemType type, long min) {
        WorkItemCounter c = workItemCounterRepository
                .findByTenantIdAndTypeCode(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, type)
                .orElseThrow(() -> new AssertionError(type + " counter not found"));
        assertThat(c.getNextValue()).isGreaterThanOrEqualTo(min);
    }

    private void assertCounterNextValueEquals(WorkItemType type, long expected) {
        WorkItemCounter c = workItemCounterRepository
                .findByTenantIdAndTypeCode(
                        DevBootstrapInitializer.BOOTSTRAP_TENANT_ID, type)
                .orElseThrow(() -> new AssertionError(type + " counter not found"));
        assertThat(c.getNextValue()).isEqualTo(expected);
    }
}
