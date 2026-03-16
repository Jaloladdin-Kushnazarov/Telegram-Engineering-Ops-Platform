package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramDeliveryResult factory method unit testlari.
 *
 * Uch factory'ni tekshiradi: success (DELIVERED), rejected (REJECTED), failed (FAILED).
 */
class TelegramDeliveryResultTest {

    @Test
    void successFactoryBuildsDeliveredResult() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 42L;
        Long externalMessageId = 99001L;

        TelegramDeliveryCommand command = buildCommand(
                tenantId, workItemId, chatBindingId, topicId);

        TelegramDeliveryResult result = TelegramDeliveryResult.success(
                command, externalMessageId);

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkItemId()).isEqualTo(workItemId);
        assertThat(result.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.getTargetTopicId()).isEqualTo(topicId);
        assertThat(result.getExternalMessageId()).isEqualTo(externalMessageId);
        assertThat(result.getFailureCode()).isNull();
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    void successWithNullExternalMessageId() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10L);

        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, null);

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExternalMessageId()).isNull();
    }

    @Test
    void rejectedFactoryBuildsRejectedResult() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 55L;

        TelegramDeliveryCommand command = buildCommand(
                tenantId, workItemId, chatBindingId, topicId);

        TelegramDeliveryResult result = TelegramDeliveryResult.rejected(
                command, "INVALID_REQUEST", "Chat not found");

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkItemId()).isEqualTo(workItemId);
        assertThat(result.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.getTargetTopicId()).isEqualTo(topicId);
        assertThat(result.getExternalMessageId()).isNull();
        assertThat(result.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(result.getFailureReason()).isEqualTo("Chat not found");
    }

    @Test
    void failedFactoryBuildsFailedResult() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 77L;

        TelegramDeliveryCommand command = buildCommand(
                tenantId, workItemId, chatBindingId, topicId);

        TelegramDeliveryResult result = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Connection timed out");

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkItemId()).isEqualTo(workItemId);
        assertThat(result.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.getTargetTopicId()).isEqualTo(topicId);
        assertThat(result.getExternalMessageId()).isNull();
        assertThat(result.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(result.getFailureReason()).isEqualTo("Connection timed out");
    }

    @Test
    void rejectedAndFailedHaveDifferentOutcomes() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L);

        TelegramDeliveryResult rejected = TelegramDeliveryResult.rejected(
                command, "INVALID_REQUEST", "Bad chat");
        TelegramDeliveryResult failed = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Timeout");

        assertThat(rejected.getDeliveryOutcome()).isNotEqualTo(failed.getDeliveryOutcome());
        assertThat(rejected.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(failed.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(rejected.isSuccess()).isFalse();
        assertThat(failed.isSuccess()).isFalse();
    }

    @Test
    void successWithNullCommandRejected() {
        assertThatThrownBy(() -> TelegramDeliveryResult.success(null, 123L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command null");
    }

    @Test
    void rejectedWithNullCommandRejected() {
        assertThatThrownBy(() -> TelegramDeliveryResult.rejected(null, "CODE", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command null");
    }

    @Test
    void failedWithNullCommandRejected() {
        assertThatThrownBy(() -> TelegramDeliveryResult.failed(null, "CODE", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command null");
    }

    @Test
    void rejectedWithBlankFailureCodeRejected() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> TelegramDeliveryResult.rejected(command, "  ", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureCode");
    }

    @Test
    void rejectedWithBlankFailureReasonRejected() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> TelegramDeliveryResult.rejected(command, "CODE", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @Test
    void failedWithBlankFailureCodeRejected() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> TelegramDeliveryResult.failed(command, null, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureCode");
    }

    @Test
    void failedWithBlankFailureReasonRejected() {
        TelegramDeliveryCommand command = buildCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L);

        assertThatThrownBy(() -> TelegramDeliveryResult.failed(command, "CODE", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    private TelegramDeliveryCommand buildCommand(UUID tenantId, UUID workItemId,
                                                   UUID chatBindingId, Long topicId) {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                chatBindingId, topicId,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }
}
