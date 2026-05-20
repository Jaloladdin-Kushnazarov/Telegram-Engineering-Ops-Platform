package com.engops.platform.telegram;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TelegramOutboundDispatchService unit testlari.
 *
 * Orchestration flow tekshiruvi:
 * - command -> assembler -> gateway.execute -> result mapping
 * - SUCCESS -> DELIVERED, REJECTED -> REJECTED, FAILED -> FAILED
 * - null guard
 * - null gateway result fail-fast
 * - InOrder: assembler keyin gateway
 *
 * Phase 189: SimpleMeterRegistry orqali send attempt counter
 * increment'lari tasdiqlanadi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramOutboundDispatchServiceTest {

    @Mock
    private TelegramOutboundGateway gateway;

    @Mock
    private TelegramSendMessageRequestAssembler assembler;

    private SimpleMeterRegistry meterRegistry;
    private TelegramOutboundDispatchService dispatchService;

    @BeforeEach
    void initService() {
        meterRegistry = new SimpleMeterRegistry();
        dispatchService = new TelegramOutboundDispatchService(gateway, assembler, meterRegistry);
    }

    private double sendCount(String outcome, String error) {
        return meterRegistry.find(TelegramOutboundDispatchService.SEND_ATTEMPTS_METER)
                .tag("outcome", outcome)
                .tag("error", error)
                .counters()
                .stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }

    @Test
    void successGatewayResultMappedToDelivered() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        long telegramMessageId = 98765L;
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.success(telegramMessageId);

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExternalMessageId()).isEqualTo(telegramMessageId);
        assertThat(result.getOperation()).isEqualTo(command.getOperation());
        assertThat(result.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(result.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(result.getFailureCode()).isNull();
        assertThat(result.getFailureReason()).isNull();

        verifyNoMoreInteractions(assembler, gateway);
    }

    @Test
    void rejectedGatewayResultMappedToRejected() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.rejected(
                TelegramGatewayError.INVALID_REQUEST, "Chat not found");

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("INVALID_REQUEST");
        assertThat(result.getFailureReason()).isEqualTo("Chat not found");
        assertThat(result.getExternalMessageId()).isNull();
    }

    @Test
    void failedGatewayResultMappedToFailed() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        TelegramGatewayResult gatewayResult = TelegramGatewayResult.failed(
                TelegramGatewayError.NETWORK_ERROR, "Connection timeout");

        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(gatewayResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
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

    // ===== Phase 189 — Micrometer counter assertions =====

    @Test
    void phase189DeliveredIncrementsSendAttemptsCounterWithErrorNone() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(TelegramGatewayResult.success(98765L));

        dispatchService.dispatch(command);

        assertThat(sendCount("DELIVERED", "NONE")).isEqualTo(1.0);
        assertThat(sendCount("FAILED", "NETWORK_ERROR")).isZero();
    }

    @Test
    void phase189FailedNetworkErrorIncrementsSendAttemptsCounter() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(TelegramGatewayResult.failed(
                TelegramGatewayError.NETWORK_ERROR, "Connection timeout"));

        dispatchService.dispatch(command);

        assertThat(sendCount("FAILED", "NETWORK_ERROR")).isEqualTo(1.0);
        assertThat(sendCount("DELIVERED", "NONE")).isZero();
    }

    @Test
    void phase189RejectedInvalidRequestIncrementsSendAttemptsCounter() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(TelegramGatewayResult.rejected(
                TelegramGatewayError.INVALID_REQUEST, "Chat not found"));

        dispatchService.dispatch(command);

        assertThat(sendCount("REJECTED", "INVALID_REQUEST")).isEqualTo(1.0);
    }

    @Test
    void phase189MiniFixGatewayRuntimeExceptionIncrementsExceptionUnknownErrorCounter() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenThrow(new RuntimeException("simulated gateway bug"));

        assertThatThrownBy(() -> dispatchService.dispatch(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated gateway bug");

        assertThat(sendCount("EXCEPTION", "UNKNOWN_ERROR")).isEqualTo(1.0);
        assertThat(sendCount("EXCEPTION", "NONE")).isZero();
    }

    @Test
    void phase189MiniFixNullGatewayResultIncrementsExceptionUnknownErrorCounter() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramSendMessageRequest request = buildRequest(command);
        when(assembler.assemble(command)).thenReturn(request);
        when(gateway.execute(request)).thenReturn(null);

        assertThatThrownBy(() -> dispatchService.dispatch(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null qaytardi");

        assertThat(sendCount("EXCEPTION", "UNKNOWN_ERROR")).isEqualTo(1.0);
        assertThat(sendCount("EXCEPTION", "NONE")).isZero();
    }
}
