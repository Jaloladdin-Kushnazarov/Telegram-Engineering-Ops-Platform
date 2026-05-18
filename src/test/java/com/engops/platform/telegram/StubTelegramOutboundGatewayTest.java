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
        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
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

    @Test
    void executeReturnsControlledFailure() {
        TelegramSendMessageRequest request = new TelegramSendMessageRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS",
                List.of());

        TelegramGatewayResult result = gateway.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        assertThat(result.getErrorMessage()).isEqualTo(
                "Telegram outbound gateway hali implement qilinmagan");
        assertThat(result.getTelegramMessageId()).isNull();
    }

    @Test
    void editMessageTextReturnsControlledFailure() {
        TelegramEditMessageTextRequest request = new TelegramEditMessageTextRequest(
                -1001234567890L, 555L, "Bug | BUG-1\nStatus: FIXED", List.of());

        TelegramEditMessageTextResult result = gateway.editMessageText(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        assertThat(result.getErrorMessage()).isEqualTo(
                "Telegram outbound gateway hali implement qilinmagan");
        assertThat(result.getTelegramMessageId()).isNull();
    }

    @Test
    void acknowledgeCallbackReturnsControlledFailure() {
        TelegramAcknowledgeCallbackRequest request =
                new TelegramAcknowledgeCallbackRequest("cb-id-1", "Action applied.");

        TelegramAcknowledgeCallbackResult result = gateway.acknowledgeCallback(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        assertThat(result.getErrorMessage()).isEqualTo(
                "Telegram outbound gateway hali implement qilinmagan");
    }
}
