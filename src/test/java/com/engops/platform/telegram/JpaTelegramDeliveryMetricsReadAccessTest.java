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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JpaTelegramDeliveryMetricsReadAccess integration testlari.
 *
 * Tekshiruvlar:
 * - eng so'nggi attempt qaytariladi (attempted_at DESC)
 * - bir xil attempted_at bo'lganda id DESC tie-breaker ishlaydi
 * - attempt topilmasa empty qaytaradi
 * - tenant isolation — boshqa tenant'ning attempt'i ko'rinmaydi
 * - boshqa workItem'ning attempt'i ko'rinmaydi
 * - entity → DTO mapping to'g'ri ishlaydi
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class JpaTelegramDeliveryMetricsReadAccessTest {

    @Autowired private TelegramDeliveryAttemptRepository attemptRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired private WorkItemRepository workItemRepository;

    private JpaTelegramDeliveryMetricsReadAccess readAccess;

    private Tenant tenant;
    private WorkItem workItem;

    @BeforeEach
    void setUp() {
        readAccess = new JpaTelegramDeliveryMetricsReadAccess(attemptRepository);

        tenant = tenantRepository.save(new Tenant("Metrics Test Co", "metrics-test-co"));
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG"));
        workItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "BUG-1", WorkItemType.BUG,
                workflowDef.getId(), "Test bug", "BUGS", null));
    }

    @Test
    void latestAttemptReturned() {
        Instant olderTime = Instant.parse("2026-03-18T08:00:00Z");
        Instant newerTime = Instant.parse("2026-03-18T09:00:00Z");
        UUID chatBindingId = UUID.randomUUID();

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                null, "NETWORK_ERROR", "Connection timeout",
                olderTime));

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                99001L, null, null,
                newerTime));

        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        TelegramDeliveryAttempt attempt = result.get();
        assertThat(attempt.getAttemptedAt()).isEqualTo(newerTime);
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(attempt.getExternalMessageId()).isEqualTo(99001L);
        assertThat(attempt.getFailureCode()).isNull();
    }

    @Test
    void tieBreakByIdDescWhenSameAttemptedAt() {
        Instant sameTime = Instant.parse("2026-03-18T10:00:00Z");
        UUID chatBindingId = UUID.randomUUID();

        // UUID'larni aniq belgilab, qaysi biri "kattaroq" ekanini nazorat qilamiz
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                smallerId, tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.FAILED,
                null, "FIRST_ATTEMPT", "First",
                sameTime));

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                largerId, tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                88001L, null, null,
                sameTime));

        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        TelegramDeliveryAttempt attempt = result.get();
        // id DESC — kattaroq UUID birinchi qaytadi
        assertThat(attempt.getAttemptId()).isEqualTo(largerId);
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
    }

    @Test
    void emptyWhenNoAttemptExists() {
        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                tenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void tenantIsolation() {
        Tenant otherTenant = tenantRepository.save(new Tenant("Other Co", "other-co"));

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                1L, null, null,
                Instant.parse("2026-03-18T10:00:00Z")));

        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                otherTenant.getId(), workItem.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void workItemIsolation() {
        WorkflowDefinition workflowDef = workflowDefinitionRepository.save(
                new WorkflowDefinition(tenant.getId(), "Task Workflow", "TASK"));
        WorkItem otherItem = workItemRepository.save(new WorkItem(
                tenant.getId(), "TASK-1", WorkItemType.TASK,
                workflowDef.getId(), "Other task", "OPEN", null));

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                UUID.randomUUID(), tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), 42L,
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED,
                1L, null, null,
                Instant.parse("2026-03-18T10:00:00Z")));

        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                tenant.getId(), otherItem.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void entityToDtoMappingCorrect() {
        UUID attemptId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Instant attemptedAt = Instant.parse("2026-03-18T10:00:00Z");

        attemptRepository.save(new TelegramDeliveryAttemptEntity(
                attemptId, tenant.getId(), workItem.getId(),
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                chatBindingId, 55L,
                TelegramDeliveryResult.DeliveryOutcome.REJECTED,
                null, "INVALID_CHAT", "Chat not found",
                attemptedAt));

        Optional<TelegramDeliveryAttempt> result = readAccess.findLatestAttempt(
                tenant.getId(), workItem.getId());

        assertThat(result).isPresent();
        TelegramDeliveryAttempt attempt = result.get();
        assertThat(attempt.getAttemptId()).isEqualTo(attemptId);
        assertThat(attempt.getAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(attempt.getTenantId()).isEqualTo(tenant.getId());
        assertThat(attempt.getWorkItemId()).isEqualTo(workItem.getId());
        assertThat(attempt.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(attempt.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(attempt.getTargetTopicId()).isEqualTo(55L);
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(attempt.getExternalMessageId()).isNull();
        assertThat(attempt.getFailureCode()).isEqualTo("INVALID_CHAT");
        assertThat(attempt.getFailureReason()).isEqualTo("Chat not found");
    }
}
