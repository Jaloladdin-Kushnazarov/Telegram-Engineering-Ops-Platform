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
 * TelegramCardDispatchService unit testlari.
 *
 * Ikki public API tekshiruvi:
 * - dispatch(cardView) -> TelegramDeliveryResult (business facade)
 * - dispatchAttempt(cardView) -> TelegramDeliveryAttempt (observability facade)
 *
 * Orchestration flow, null guard, InOrder, va fail-fast tekshiruvi.
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardDispatchServiceTest {

    @Mock
    private TelegramMessageRenderer renderer;

    @Mock
    private TelegramDeliveryCommandAssembler commandAssembler;

    @Mock
    private TelegramOutboundDispatchService outboundDispatchService;

    @InjectMocks
    private TelegramCardDispatchService cardDispatchService;

    // ========== dispatch() -> TelegramDeliveryResult ==========

    @Test
    void dispatchReturnsDeliveryResult() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.success(command, 55555L);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(expectedResult);

        TelegramDeliveryResult result = cardDispatchService.dispatch(cardView);

        assertThat(result).isSameAs(expectedResult);
        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExternalMessageId()).isEqualTo(55555L);

        verify(renderer).render(cardView);
        verify(commandAssembler).assembleSend(message);
        verify(outboundDispatchService).dispatch(command);
        verifyNoMoreInteractions(renderer, commandAssembler, outboundDispatchService);
    }

    @Test
    void dispatchRejectedResult() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.rejected(
                command, "INVALID_REQUEST", "Chat not found");

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(expectedResult);

        TelegramDeliveryResult result = cardDispatchService.dispatch(cardView);

        assertThat(result).isSameAs(expectedResult);
        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void dispatchFailedResult() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.failed(
                command, "NETWORK_ERROR", "Connection timeout");

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(expectedResult);

        TelegramDeliveryResult result = cardDispatchService.dispatch(cardView);

        assertThat(result).isSameAs(expectedResult);
        assertThat(result.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("NETWORK_ERROR");
    }

    @Test
    void dispatchNullCardViewRejected() {
        assertThatThrownBy(() -> cardDispatchService.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TelegramCardView null");

        verifyNoInteractions(renderer, commandAssembler, outboundDispatchService);
    }

    @Test
    void dispatchOrchestrationOrder() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult deliveryResult = TelegramDeliveryResult.success(command, 111L);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(deliveryResult);

        cardDispatchService.dispatch(cardView);

        InOrder order = inOrder(renderer, commandAssembler, outboundDispatchService);
        order.verify(renderer).render(cardView);
        order.verify(commandAssembler).assembleSend(message);
        order.verify(outboundDispatchService).dispatch(command);
        order.verifyNoMoreInteractions();
    }

    @Test
    void dispatchNullRendererResultFailsFast() {
        TelegramCardView cardView = buildCardView();

        when(renderer.render(cardView)).thenReturn(null);

        assertThatThrownBy(() -> cardDispatchService.dispatch(cardView))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TelegramMessageRenderer null");

        verifyNoInteractions(commandAssembler, outboundDispatchService);
    }

    @Test
    void dispatchNullAssemblerResultFailsFast() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(null);

        assertThatThrownBy(() -> cardDispatchService.dispatch(cardView))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TelegramDeliveryCommandAssembler null");

        verifyNoInteractions(outboundDispatchService);
    }

    @Test
    void dispatchNullDispatchResultFailsFast() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(null);

        assertThatThrownBy(() -> cardDispatchService.dispatch(cardView))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TelegramOutboundDispatchService null");
    }

    // ========== dispatchAttempt() -> TelegramDeliveryAttempt ==========

    @Test
    void dispatchAttemptReturnsDeliveredAttempt() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult deliveryResult = TelegramDeliveryResult.success(command, 77777L);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(deliveryResult);

        TelegramDeliveryAttempt attempt = cardDispatchService.dispatchAttempt(cardView);

        assertThat(attempt.getAttemptId()).isNotNull();
        assertThat(attempt.getAttemptedAt()).isNotNull();
        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.DELIVERED);
        assertThat(attempt.isSuccess()).isTrue();
        assertThat(attempt.getExternalMessageId()).isEqualTo(77777L);
        assertThat(attempt.getTenantId()).isEqualTo(command.getTenantId());
        assertThat(attempt.getWorkItemId()).isEqualTo(command.getWorkItemId());
        assertThat(attempt.getOperation()).isEqualTo(command.getOperation());
        assertThat(attempt.getTargetChatBindingId()).isEqualTo(command.getTargetChatBindingId());
        assertThat(attempt.getTargetTopicId()).isEqualTo(command.getTargetTopicId());
        assertThat(attempt.getFailureCode()).isNull();
        assertThat(attempt.getFailureReason()).isNull();

        verify(renderer).render(cardView);
        verify(commandAssembler).assembleSend(message);
        verify(outboundDispatchService).dispatch(command);
        verifyNoMoreInteractions(renderer, commandAssembler, outboundDispatchService);
    }

    @Test
    void dispatchAttemptReturnsRejectedAttempt() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult deliveryResult = TelegramDeliveryResult.rejected(
                command, "RATE_LIMIT", "Too many requests");

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(deliveryResult);

        TelegramDeliveryAttempt attempt = cardDispatchService.dispatchAttempt(cardView);

        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.REJECTED);
        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getFailureCode()).isEqualTo("RATE_LIMIT");
        assertThat(attempt.getFailureReason()).isEqualTo("Too many requests");
        assertThat(attempt.getExternalMessageId()).isNull();
        assertThat(attempt.getAttemptId()).isNotNull();
    }

    @Test
    void dispatchAttemptReturnsFailedAttempt() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult deliveryResult = TelegramDeliveryResult.failed(
                command, "UNKNOWN_ERROR", "Gateway unavailable");

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(deliveryResult);

        TelegramDeliveryAttempt attempt = cardDispatchService.dispatchAttempt(cardView);

        assertThat(attempt.getDeliveryOutcome()).isEqualTo(
                TelegramDeliveryResult.DeliveryOutcome.FAILED);
        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getFailureCode()).isEqualTo("UNKNOWN_ERROR");
        assertThat(attempt.getFailureReason()).isEqualTo("Gateway unavailable");
        assertThat(attempt.getExternalMessageId()).isNull();
        assertThat(attempt.getAttemptId()).isNotNull();
    }

    @Test
    void dispatchAttemptOrchestrationOrder() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult deliveryResult = TelegramDeliveryResult.success(command, 222L);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(deliveryResult);

        cardDispatchService.dispatchAttempt(cardView);

        InOrder order = inOrder(renderer, commandAssembler, outboundDispatchService);
        order.verify(renderer).render(cardView);
        order.verify(commandAssembler).assembleSend(message);
        order.verify(outboundDispatchService).dispatch(command);
        order.verifyNoMoreInteractions();
    }

    // ========== Helpers ==========

    private TelegramCardView buildCardView() {
        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                "Bug | BUG-1", "Status: BUGS",
                true,
                UUID.randomUUID(),
                42L);
        return new TelegramCardView(renderPayload, List.of());
    }

    private TelegramMessage buildMessage() {
        return new TelegramMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS",
                List.of(),
                UUID.randomUUID(),
                42L);
    }

    private TelegramDeliveryCommand buildCommand(TelegramMessage message) {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                message.getTenantId(),
                message.getWorkItemId(),
                message.getTargetChatBindingId(),
                message.getTargetTopicId(),
                message.getText(),
                message.getKeyboard());
    }
}
