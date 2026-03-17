package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryMetricsAssembler mapping testlari.
 *
 * Tekshiruvlar:
 * - DELIVERED attempt to'g'ri map qilinadi
 * - REJECTED attempt to'g'ri map qilinadi
 * - FAILED attempt to'g'ri map qilinadi
 * - null attempt rad etiladi
 */
class TelegramDeliveryMetricsAssemblerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-03-18T10:00:00Z");

    private final TelegramDeliveryMetricsAssembler assembler = new TelegramDeliveryMetricsAssembler();

    @Test
    void deliveredAttemptMappedCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        Long externalMessageId = 88001L;
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, externalMessageId);
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        TelegramDeliveryMetricsSnapshot snapshot = assembler.assemble(attempt);

        assertThat(snapshot.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(snapshot.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(snapshot.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(snapshot.isSuccess()).isTrue();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.isFailed()).isFalse();
        assertThat(snapshot.getFailureCode()).isNull();
        assertThat(snapshot.hasExternalMessageId()).isTrue();
    }

    @Test
    void rejectedAttemptMappedCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.rejected(
                command, "INVALID_REQUEST", "Chat not found");
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        TelegramDeliveryMetricsSnapshot snapshot = assembler.assemble(attempt);

        assertThat(snapshot.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(snapshot.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(snapshot.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isTrue();
        assertThat(snapshot.isFailed()).isFalse();
        assertThat(snapshot.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(snapshot.hasExternalMessageId()).isFalse();
    }

    @Test
    void failedAttemptMappedCorrectly() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Connection timeout");
        TelegramDeliveryAttempt attempt = TelegramDeliveryAttempt.of(command, result, FIXED_TIME);

        TelegramDeliveryMetricsSnapshot snapshot = assembler.assemble(attempt);

        assertThat(snapshot.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(snapshot.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(snapshot.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(snapshot.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(snapshot.isSuccess()).isFalse();
        assertThat(snapshot.isRejected()).isFalse();
        assertThat(snapshot.isFailed()).isTrue();
        assertThat(snapshot.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(snapshot.hasExternalMessageId()).isFalse();
    }

    @Test
    void nullAttemptRejected() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt null");
    }

    private TelegramDeliveryCommand buildCommand() {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }
}
