package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TelegramDeliveryResult factory method unit testlari.
 *
 * Success va failure factory'larning to'g'ri ishlashini tekshiradi.
 */
class TelegramDeliveryResultTest {

    @Test
    void successFactoryBuildsCorrectResult() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 42L;
        Long externalMessageId = 99001L;

        TelegramDeliveryCommand command = buildCommand(
                tenantId, workItemId, chatBindingId, topicId);

        TelegramDeliveryResult result = TelegramDeliveryResult.success(
                command, externalMessageId);

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

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExternalMessageId()).isNull();
        assertThat(result.getFailureCode()).isNull();
    }

    @Test
    void failureFactoryBuildsCorrectResult() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 55L;

        TelegramDeliveryCommand command = buildCommand(
                tenantId, workItemId, chatBindingId, topicId);

        TelegramDeliveryResult result = TelegramDeliveryResult.failure(
                command, "NETWORK_ERROR", "Connection timed out");

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
