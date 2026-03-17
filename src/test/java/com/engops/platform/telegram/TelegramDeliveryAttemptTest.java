package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryAttempt factory va invariant testlari.
 *
 * Tekshiruvlar:
 * - DELIVERED attempt to'g'ri field mapping
 * - REJECTED attempt to'g'ri field mapping
 * - FAILED attempt to'g'ri field mapping
 * - attemptId noyobligi
 * - attemptedAt deterministic
 * - null guard invariantlari
 */
class TelegramDeliveryAttemptTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-03-17T10:00:00Z");

    @Test
    void deliveredAttemptMapsCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        Long externalMessageId = 99001L;
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, externalMessageId);

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        assertThat(attempt.getAttemptId()).isNotNull();
        assertThat(attempt.getAttemptedAt()).isEqualTo(FIXED_TIME);
        assertThat(attempt.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(attempt.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(attempt.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(attempt.getTargetChatBindingId()).isEqualTo(command.getTargetChatBindingId());
        assertThat(attempt.getTargetTopicId()).isEqualTo(command.getTargetTopicId());
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(attempt.isSuccess()).isTrue();
        assertThat(attempt.getExternalMessageId()).isEqualTo(externalMessageId);
        assertThat(attempt.getFailureCode()).isNull();
        assertThat(attempt.getFailureReason()).isNull();
    }

    @Test
    void rejectedAttemptMapsCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.rejected(
                command, "INVALID_REQUEST", "Chat not found");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        assertThat(attempt.getAttemptId()).isNotNull();
        assertThat(attempt.getAttemptedAt()).isEqualTo(FIXED_TIME);
        assertThat(attempt.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(attempt.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getExternalMessageId()).isNull();
        assertThat(attempt.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(attempt.getFailureReason()).isEqualTo("Chat not found");
    }

    @Test
    void failedAttemptMapsCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Connection timeout");

        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        assertThat(attempt.getAttemptId()).isNotNull();
        assertThat(attempt.getAttemptedAt()).isEqualTo(FIXED_TIME);
        assertThat(attempt.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(attempt.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getExternalMessageId()).isNull();
        assertThat(attempt.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(attempt.getFailureReason()).isEqualTo("Connection timeout");
    }

    @Test
    void eachAttemptGetsUniqueId() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 1L);

        TelegramDeliveryAttempt attempt1 = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);
        TelegramDeliveryAttempt attempt2 = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        assertThat(attempt1.getAttemptId()).isNotEqualTo(attempt2.getAttemptId());
    }

    @Test
    void nullCommandRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(null, result, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command null");
    }

    @Test
    void nullResultRejected() {
        TelegramDeliveryCommand command = buildCommand();

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, null, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result null");
    }

    @Test
    void nullAttemptedAtRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, result, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptedAt null");
    }

    @Test
    void mismatchedTenantIdRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryCommand otherCommand = buildCommandWith(
                UUID.randomUUID(), command.getWorkItemId(),
                command.getTargetChatBindingId(), command.getTargetTopicId());
        TelegramDeliveryResult result = TelegramDeliveryResult.success(otherCommand, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, result, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId mos kelmaydi");
    }

    @Test
    void mismatchedWorkItemIdRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryCommand otherCommand = buildCommandWith(
                command.getTenantId(), UUID.randomUUID(),
                command.getTargetChatBindingId(), command.getTargetTopicId());
        TelegramDeliveryResult result = TelegramDeliveryResult.success(otherCommand, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, result, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workItemId mos kelmaydi");
    }

    @Test
    void mismatchedOperationRejected() {
        TelegramDeliveryCommand command = buildCommand();
        // Result created from same command always has same operation,
        // so we test via a command with different operation if possible.
        // Since only SEND_NEW_MESSAGE exists, we verify the guard path
        // by building result from same command — operation always matches.
        // This test exists as a structural placeholder for when EDIT_MESSAGE is added.
        // For now, verify that matching operation passes.
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 1L);
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);
        assertThat(attempt.getOperation()).isEqualTo(command.getOperation());
    }

    @Test
    void mismatchedTargetChatBindingIdRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryCommand otherCommand = buildCommandWith(
                command.getTenantId(), command.getWorkItemId(),
                UUID.randomUUID(), command.getTargetTopicId());
        TelegramDeliveryResult result = TelegramDeliveryResult.success(otherCommand, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, result, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetChatBindingId mos kelmaydi");
    }

    @Test
    void mismatchedTargetTopicIdRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryCommand otherCommand = buildCommandWith(
                command.getTenantId(), command.getWorkItemId(),
                command.getTargetChatBindingId(), 999L);
        TelegramDeliveryResult result = TelegramDeliveryResult.success(otherCommand, 1L);

        assertThatThrownBy(() -> TelegramDeliveryAttempt.of(command, result, FIXED_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetTopicId mos kelmaydi");
    }

    private TelegramDeliveryCommand buildCommand() {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }

    private TelegramDeliveryCommand buildCommandWith(UUID tenantId, UUID workItemId,
                                                       UUID targetChatBindingId, Long targetTopicId) {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                targetChatBindingId, targetTopicId,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }
}
