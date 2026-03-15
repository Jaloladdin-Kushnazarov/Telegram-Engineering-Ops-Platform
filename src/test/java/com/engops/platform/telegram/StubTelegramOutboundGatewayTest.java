package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StubTelegramOutboundGateway unit testi.
 *
 * Stub gateway controlled failure qaytarishini tekshiradi.
 */
class StubTelegramOutboundGatewayTest {

    private final StubTelegramOutboundGateway gateway = new StubTelegramOutboundGateway();

    @Test
    void dispatchReturnsControlledFailure() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        Long topicId = 42L;

        TelegramDeliveryCommand command = new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                chatBindingId, topicId,
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS",
                List.of());

        TelegramDeliveryResult result = gateway.dispatch(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("TELEGRAM_GATEWAY_NOT_IMPLEMENTED");
        assertThat(result.getFailureReason()).isEqualTo(
                "Telegram outbound gateway hali implement qilinmagan");
        assertThat(result.getOperation()).isEqualTo(TelegramDeliveryOperation.SEND_NEW_MESSAGE);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getWorkItemId()).isEqualTo(workItemId);
        assertThat(result.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(result.getTargetTopicId()).isEqualTo(topicId);
        assertThat(result.getExternalMessageId()).isNull();
    }
}
