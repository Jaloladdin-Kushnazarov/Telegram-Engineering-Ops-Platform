package com.engops.platform.telegram;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.TenantRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryAttemptPersistence integration testlari.
 *
 * Tekshiruvlar:
 * - DELIVERED attempt to'g'ri saqlanadi
 * - FAILED attempt to'g'ri saqlanadi
 * - REJECTED attempt to'g'ri saqlanadi
 * - saqlangan attempt read path orqali o'qiladi
 * - null attempt rad etiladi
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class TelegramDeliveryAttemptPersistenceTest {

    @Autowired private TelegramDeliveryAttemptRepository attemptRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired private WorkItemRepository workItemRepository;

    private JpaTelegramDeliveryAttemptPersistence persistence;
    private JpaTelegramDeliveryMetricsReadAccess readAccess;

    private Tenant tenant;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        persistence = new JpaTelegramDeliveryAttemptPersistence(attemptRepository);
        readAccess = new JpaTelegramDeliveryMetricsReadAccess(attemptRepository);

        tenant = tenantRepository.save(new Tenant("Persist Test Co", "persist-test-co"));
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG"));
        workItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Test bug", "BUGS", null));
    }

    @Test
    void deliveredAttemptPersisted() {
        TelegramDeliveryAttempt attempt = buildAttempt(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 88001L, null, null);

        persistence.save(attempt);

        TelegramDeliveryAttemptEntity entity = attemptRepository.findById(attempt.getAttemptId())
                .orElseThrow();
        assertThat(entity.getTenantId()).isEqualTo(tenant.getId());
        assertThat(entity.getWorkItemId()).isEqualTo(workItem.getId());
        assertThat(entity.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(entity.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(entity.getExternalMessageId()).isEqualTo(88001L);
        assertThat(entity.getFailureCode()).isNull();
        assertThat(entity.getFailureReason()).isNull();
        assertThat(entity.getAttemptedAt()).isEqualTo(attempt.getAttemptedAt());
    }

    @Test
    void failedAttemptPersisted() {
        TelegramDeliveryAttempt attempt = buildAttempt(
                TelegramDeliveryResult.DeliveryOutcome.FAILED, null, "NETWORK_ERROR", "Timeout");

        persistence.save(attempt);

        TelegramDeliveryAttemptEntity entity = attemptRepository.findById(attempt.getAttemptId())
                .orElseThrow();
        assertThat(entity.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(entity.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(entity.getFailureReason()).isEqualTo("Timeout");
        assertThat(entity.getExternalMessageId()).isNull();
    }

    @Test
    void rejectedAttemptPersisted() {
        TelegramDeliveryAttempt attempt = buildAttempt(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED, null, "INVALID_CHAT", "Not found");

        persistence.save(attempt);

        TelegramDeliveryAttemptEntity entity = attemptRepository.findById(attempt.getAttemptId())
                .orElseThrow();
        assertThat(entity.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(entity.getFailureCode()).isEqualTo("INVALID_CHAT");
    }

    @Test
    void persistedAttemptReadableViaReadAccess() {
        TelegramDeliveryAttempt attempt = buildAttempt(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED, 77001L, null, null);

        persistence.save(attempt);

        Optional<TelegramDeliveryAttempt> found = readAccess.findLatestAttempt(
                tenant.getId(), workItem.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAttemptId()).isEqualTo(attempt.getAttemptId());
        assertThat(found.get().getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(found.get().getExternalMessageId()).isEqualTo(77001L);
    }

    @Test
    void nullAttemptRejected() {
        assertThatThrownBy(() -> persistence.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt null");
    }

    private TelegramDeliveryAttempt buildAttempt(
            TelegramDeliveryResult.DeliveryOutcome outcome,
            Long externalMessageId,
            String failureCode,
            String failureReason) {
        UUID chatBindingId = UUID.randomUUID();
        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenant.getId(), workItem.getId(),
                chatBindingId, 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());

        TelegramDeliveryResult result;
        switch (outcome) {
            case DELIVERED -> result = TelegramDeliveryResult.success(command, externalMessageId);
            case REJECTED -> result = TelegramDeliveryResult.rejected(command, failureCode, failureReason);
            case FAILED -> result = TelegramDeliveryResult.failed(command, failureCode, failureReason);
            default -> throw new IllegalArgumentException("Unexpected outcome: " + outcome);
        }

        return TelegramDeliveryAttempt.of(command, result, Instant.parse("2026-03-18T12:00:00Z"));
    }
}
