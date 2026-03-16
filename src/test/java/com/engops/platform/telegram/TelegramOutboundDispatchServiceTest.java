package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TelegramOutboundDispatchService unit testlari.
 *
 * Orchestration flow tekshiruvi:
 * - command -> assembler -> gateway.execute -> result mapping
 * - success/rejected/failed gateway result'larni to'g'ri mapping
 * - null guard
 * - null gateway result fail-fast
 * - InOrder: assembler keyin gateway
 */
@ExtendWith(MockitoExtension.class)
class TelegramOutboundDispatchServiceTest {

    @Mock
    private TelegramOutboundGateway gateway;

    @Mock
    private TelegramSendMessageRequestAssembler assembler;

    @InjectMocks
    private TelegramOutboundDispatchService dispatchService;

    @Test
    void successGatewayResultMappedToDeliverySuccess() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        long telegramMessageId = 98765L;
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.success(telegramMessageId);

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExternalMessageId()).isEqualTo(telegramMessageId);
        assertThat(result.getOperation()).isEqualTo(command.getOperation());
        assertThat(result.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(result.getWorkItemId()).isEqualTo(command.getWorkItemId());

        verify(assembler).assemble(command);
        verify(gateway).execute(request);
        verifyNoMoreInteractions(assembler, gateway);
    }

    @Test
    void rejectedGatewayResultMappedToDeliveryFailure() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.rejected(
                TelegramGatewayError.INVALID_REQUEST, "Chat not found");

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(result.getFailureReason()).isEqualTo("Chat not found");
        assertThat(result.getExternalMessageId()).isNull();
    }

    @Test
    void failedGatewayResultMappedToDeliveryFailure() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.failed(
                TelegramGatewayError.NETWORK_ERROR, "Connection timeout");

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("NETWORK_ERROR");
        assertThat(result.getFailureReason()).isEqualTo("Connection timeout");
        assertThat(result.getExternalMessageId()).isNull();
    }

    @Test
    void nullCommandRejected() {
        assertThatThrownBy(() -> dispatchService.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");

        verifyNoInteractions(assembler, gateway);
    }

    @Test
    void assemblerCalledBeforeGatewayExecute() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.success(111L);

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        dispatchService.dispatch(command);

        InOrder order = inOrder(assembler, gateway);
        order.verify(assembler).assemble(command);
        order.verify(gateway).execute(request);
        order.verifyNoMoreInteractions();
    }

    @Test
    void nullGatewayResultFailsFast() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(null);

        assertThatThrownBy(() -> dispatchService.dispatch(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null qaytardi");
    }

    private TelegramDeliveryCommand buildCommand() {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }

    private TelegramSendMessageRequest buildRequest(TelegramDeliveryCommand command) {
        return new TelegramSendMessageRequest(
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                command.getText(),
                command.getKeyboard());
    }
}
