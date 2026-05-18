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
import java.util.Optional;
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

    private void saveAttemptWithOperation(Tenant tenant, WorkItem workItem, UUID chatBindingId,
                                            TelegramDeliveryOperation operation,
                                            TelegramDeliveryResult.DeliveryOutcome outcome,
                                            Long externalMessageId, Instant attemptedAt) {
        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                operation,
                chatBindingId, 42L,
                outcome, externalMessageId, null, null,
                attemptedAt));
    }

    // ==========================================================
    // Phase 177 — findLatestDeliveredSendMessage
    // ==========================================================

    @Test
    void latestDeliveredSendMessage_returnsNewestDeliveredSend() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");
        Instant t3 = Instant.parse("2026-03-18T10:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 11001L, null, null, t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 11002L, null, null, t2);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 11003L, null, null, t3);

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getAttemptedAt()).isEqualTo(t3);
        assertThat(result.get().getExternalMessageId()).isEqualTo(11003L);
    }

    @Test
    void latestDeliveredSendMessage_skipsNewerFailed() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 22001L, null, null, t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "ERR", "Newer failure", t2);

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getAttemptedAt()).isEqualTo(t1);
        assertThat(result.get().getExternalMessageId()).isEqualTo(22001L);
    }

    @Test
    void latestDeliveredSendMessage_skipsNewerRejected() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 33001L, null, null, t1);
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED, null, "REJ", "Newer reject", t2);

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getAttemptedAt()).isEqualTo(t1);
        assertThat(result.get().getExternalMessageId()).isEqualTo(33001L);
    }

    @Test
    void latestDeliveredSendMessage_skipsEditMessageRows() {
        UUID chatBindingId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-03-18T08:00:00Z");
        Instant t2 = Instant.parse("2026-03-18T09:00:00Z");

        saveAttemptWithOperation(tenant, workItem, chatBindingId,
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 44001L, t1);
        // Newer EDIT_MESSAGE row — should be skipped.
        saveAttemptWithOperation(tenant, workItem, chatBindingId,
                TelegramDeliveryOperation.EDIT_MESSAGE,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 44099L, t2);

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getOperation())
                .isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(result.get().getExternalMessageId()).isEqualTo(44001L);
    }

    @Test
    void latestDeliveredSendMessage_isTenantScoped() {
        Tenant otherTenant = tenantRepository.save(new Tenant("Iso Co", "iso-co"));
        UUID chatBindingId = UUID.randomUUID();

        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 55001L, null, null,
                Instant.parse("2026-03-18T10:00:00Z"));

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(otherTenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void latestDeliveredSendMessage_emptyWhenNoneDelivered() {
        UUID chatBindingId = UUID.randomUUID();
        saveAttempt(tenant, workItem, chatBindingId,
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "ERR", "only fail",
                Instant.parse("2026-03-18T10:00:00Z"));

        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void latestDeliveredSendMessage_emptyWhenNoAttempts() {
        Optional<TelegramDeliveryAttempt> result = historyReadAccess
                .findLatestDeliveredSendMessage(tenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }
}
