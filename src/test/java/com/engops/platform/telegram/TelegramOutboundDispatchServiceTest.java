package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TelegramOutboundDispatchService unit testlari.
 *
 * Thin delegation tekshiruvi:
 * - success result to'g'ri qaytadi
 * - failure result to'g'ri qaytadi
 * - null guard ishlaydi
 * - gateway faqat bir marta chaqiriladi
 */
@ExtendWith(MockitoExtension.class)
class TelegramOutboundDispatchServiceTest {

    @Mock
    private TelegramOutboundGateway gateway;

    @InjectMocks
    private TelegramOutboundDispatchService dispatchService;

    @Test
    void successResultDelegatedAndReturned() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.success(command, 12345L);

        when(gateway.dispatch(command)).thenReturn(expectedResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result).isSameAs(expectedResult);
        assertThat(result.isSuccess()).isTrue();
        verify(gateway).dispatch(command);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void failureResultDelegatedAndReturned() {
        TelegramDeliveryCommand command = buildCommand();
        TelegramDeliveryResult expectedResult = TelegramDeliveryResult.failure(
                command, "NETWORK_ERROR", "Timeout");

        when(gateway.dispatch(command)).thenReturn(expectedResult);

        TelegramDeliveryResult result = dispatchService.dispatch(command);

        assertThat(result).isSameAs(expectedResult);
        assertThat(result.isSuccess()).isFalse();
        verify(gateway).dispatch(command);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void nullCommandRejected() {
        assertThatThrownBy(() -> dispatchService.dispatch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");

        verifyNoInteractions(gateway);
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
