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
 * Orchestration flow tekshiruvi:
 * cardView -> renderer -> commandAssembler -> outboundDispatchService
 * DELIVERED / REJECTED / FAILED natijalar to'g'ri qaytishi
 * null guard va InOrder tekshiruvi
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

    @Test
    void deliveredResultReturnedCorrectly() {
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
    void rejectedResultReturnedCorrectly() {
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
    void failedResultReturnedCorrectly() {
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
    void nullCardViewRejected() {
        assertThatThrownBy(() -> cardDispatchService.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TelegramCardView null");

        verifyNoInteractions(renderer, commandAssembler, outboundDispatchService);
    }

    @Test
    void orchestrationOrderIsCorrect() {
        TelegramCardView cardView = buildCardView();
        TelegramMessage message = buildMessage();
        TelegramDeliveryCommand command = buildCommand(message);
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.success(command, 111L);

        when(renderer.render(cardView)).thenReturn(message);
        when(commandAssembler.assembleSend(message)).thenReturn(command);
        when(outboundDispatchService.dispatch(command)).thenReturn(expectedResult);

        cardDispatchService.dispatch(cardView);

        InOrder order = inOrder(renderer, commandAssembler, outboundDispatchService);
        order.verify(renderer).render(cardView);
        order.verify(commandAssembler).assembleSend(message);
        order.verify(outboundDispatchService).dispatch(command);
        order.verifyNoMoreInteractions();
    }

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
