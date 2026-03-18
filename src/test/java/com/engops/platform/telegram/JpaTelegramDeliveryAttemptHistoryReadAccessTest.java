package com.engops.platform.telegram;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JpaTelegramDeliveryAttemptHistoryReadAccess integration testlari.
 *
 * Tekshiruvlar:
 * - newest-first tartibda qaytariladi
 * - limit to'g'ri ishlaydi
 * - tenant isolation
 * - work item isolation
 * - bo'sh ro'yxat agar attempt yo'q
 * - tie-breaker (id DESC) bir xil attempted_at bo'lganda
 * - birinchi element latest snapshot bilan mos keladi
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class JpaTelegramDeliveryAttemptHistoryReadAccessTest {

    @Autowired private TelegramDeliveryAttemptRepository attemptRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired private WorkItemRepository workItemRepository;

    private JpaTelegramDeliveryAttemptHistoryReadAccess historyReadAccess;
    private JpaTelegramDeliveryMetricsReadAccess metricsReadAccess;

    private Tenant tenant;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        historyReadAccess = new JpaTelegramDeliveryAttemptHistoryReadAccess(attemptRepository);
        metricsReadAccess = new JpaTelegramDeliveryMetricsReadAccess(attemptRepository);

        tenant = tenantRepository.save(new Tenant("History Test Co", "history-test-co"));
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG"));
        workItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Test bug", "BUGS", null));
    }

    @Test
    void returnsNewestFirst() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");
        Instant t3 = Instant.parse("2026-03-18T10:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "ERR1", "First", t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED, null, "ERR2", "Second", t2);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 99001L, null, null, t3);

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), workItem.getId(), 10);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getAttemptedAt()).isEqualTo(t3);
        assertThat(history.get(0).getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(history.get(1).getAttemptedAt()).isEqualTo(t2);
        assertThat(history.get(1).getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(history.get(2).getAttemptedAt()).isEqualTo(t1);
        assertThat(history.get(2).getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
    }

    @Test
    void respectsLimit() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");
        Instant t3 = Instant.parse("2026-03-18T10:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "ERR1", "First", t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED, null, "ERR2", "Second", t2);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 99001L, null, null, t3);

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), workItem.getId(), 2);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAttemptedAt()).isEqualTo(t3);
        assertThat(history.get(1).getAttemptedAt()).isEqualTo(t2);
    }

    @Test
    void tenantIsolation() {
        Tenant otherTenant = tenantRepository.save(new Tenant("Other Co", "other-co"));
        UUID chatBindingId = UUID.randomUUID();

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 1L, null, null,
                Instant.parse("2026-03-18T10:00:00Z"));

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                otherTenant.getId(), workItem.getId(), 10);

        assertThat(history).isEmpty();
    }

    @Test
    void workItemIsolation() {
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Task Workflow", "TASK"));
        WorkItem otherItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "TASK-1", WorkItemType.TASK,
                workflowDef.getId(), "Other task", "OPEN", null));

        UUID chatBindingId = UUID.randomUUID();
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 1L, null, null,
                Instant.parse("2026-03-18T10:00:00Z"));

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), otherItem.getId(), 10);

        assertThat(history).isEmpty();
    }

    @Test
    void emptyListWhenNoAttempts() {
        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), workItem.getId(), 10);

        assertThat(history).isEmpty();
    }

    @Test
    void tieBreakByIdDescWhenSameAttemptedAt() {
        Instant sameTime = Instant.parse("2026-03-18T10:00:00Z");
        UUID chatBindingId = UUID.randomUUID();

        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                smallerId, tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                null, "FIRST", "First",
                sameTime));

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                largerId, tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                88001L, null, null,
                sameTime));

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), workItem.getId(), 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAttemptId()).isEqualTo(largerId);
        assertThat(history.get(1).getAttemptId()).isEqualTo(smallerId);
    }

    @Test
    void firstHistoryItemMatchesLatestSnapshot() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "ERR", "Old", t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 55001L, null, null, t2);

        List<TelegramDeliveryAttempt> history = historyReadAccess.findRecentAttempts(
                tenant.getId(), workItem.getId(), 10);
        TelegramDeliveryAttempt latest = metricsReadAccess.findLatestAttempt(
                tenant.getId(), workItem.getId()).orElseThrow();

        assertThat(history.get(0).getAttemptId()).isEqualTo(latest.getAttemptId());
        assertThat(history.get(0).getAttemptedAt()).isEqualTo(latest.getAttemptedAt());
        assertThat(history.get(0).getDeliveryOutcome()).isEqualTo(latest.getDeliveryOutcome());
    }

    private void saveAttempt(Tenant tenant, WorkItem workItem, UUID chatBindingId,
                             TelegramDeliveryResult.DeliveryOutcome outcome,
                             Long externalMessageId, String failureCode,
                             String failureReason, Instant attemptedAt) {
        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                outcome, externalMessageId, failureCode, failureReason,
                attemptedAt));
    }
}
