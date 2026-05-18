package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 175 — {@link TelegramCallbackAcknowledgementService} unit testlari.
 *
 * <p>Fail-soft kontrakt va short-circuit qoidalarini isbotlaydi.</p>
 */
class TelegramCallbackAcknowledgementServiceTest {

    private final TelegramOutboundGateway gateway = mock(TelegramOutboundGateway.class);
    private final TelegramCallbackAcknowledgementService service =
            new TelegramCallbackAcknowledgementService(gateway);

    // ---- short-circuit on null/blank inputs ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankCallbackQueryIdSkipsGateway(String callbackQueryId) {
        TelegramAcknowledgeCallbackResult result =
                service.acknowledge(callbackQueryId, "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankTextSkipsGateway(String text) {
        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", text);

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        verifyNoInteractions(gateway);
    }

    @Test
    void textOver200CharsSkipsGatewayGracefully() {
        String oversized = "a".repeat(201);

        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", oversized);

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
        verifyNoInteractions(gateway);
    }

    // ---- forwarding to gateway ----

    @Test
    void validInputsForwardToGateway() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(TelegramAcknowledgeCallbackResult.success());

        TelegramAcknowledgeCallbackResult result =
                service.acknowledge("cb-id", "Action applied.");

        assertThat(result.isSuccess()).isTrue();
        verify(gateway, times(1)).acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class));
    }

    @Test
    void gatewaySuccessReturnsNormally() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(TelegramAcknowledgeCallbackResult.success());

        TelegramAcknowledgeCallbackResult result =
                service.acknowledge("cb-id", "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.SUCCESS);
    }

    @Test
    void gatewayStructuredFailureReturnsNormally() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(TelegramAcknowledgeCallbackResult.failed(
                        TelegramGatewayError.NETWORK_ERROR, "simulated"));

        // Service exception tashlamasligi shart.
        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
    }

    @Test
    void gatewayStructuredRejectedReturnsNormally() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(TelegramAcknowledgeCallbackResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST, "stale callback"));

        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.REJECTED);
    }

    // ---- exception swallowing ----

    @Test
    void gatewayRuntimeExceptionIsSwallowed() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenThrow(new RuntimeException("simulated gateway crash"));

        assertThatCode(() -> service.acknowledge("cb-id", "Action applied."))
                .doesNotThrowAnyException();
    }

    @Test
    void gatewayRuntimeExceptionReturnsUnknownFailureResult() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenThrow(new RuntimeException("simulated gateway crash"));

        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
    }

    @Test
    void gatewayReturningNullIsDefensivelyHandled() {
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(null);

        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", "Action applied.");

        assertThat(result.getResultType())
                .isEqualTo(TelegramAcknowledgeCallbackResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
    }

    // ---- boundary lock: text at 200 chars accepted ----

    @Test
    void textAt200CharsIsForwardedToGateway() {
        String maxText = "a".repeat(200);
        when(gateway.acknowledgeCallback(any(TelegramAcknowledgeCallbackRequest.class)))
                .thenReturn(TelegramAcknowledgeCallbackResult.success());

        TelegramAcknowledgeCallbackResult result = service.acknowledge("cb-id", maxText);

        assertThat(result.isSuccess()).isTrue();
        verify(gateway, atLeastOnce()).acknowledgeCallback(any());
    }

    @Test
    void neverInvokesGatewayWhenSkipping() {
        // Combined defensive check.
        service.acknowledge(null, "any");
        service.acknowledge("", "any");
        service.acknowledge("cb-id", null);
        service.acknowledge("cb-id", "");
        service.acknowledge("cb-id", "a".repeat(201));

        verify(gateway, never()).acknowledgeCallback(any());
    }
}
