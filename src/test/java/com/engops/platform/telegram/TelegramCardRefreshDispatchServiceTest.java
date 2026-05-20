package com.engops.platform.telegram;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 179 — {@link TelegramCardRefreshDispatchService} unit testlari.
 *
 * <p>Edit-first / send-as-fallback siyosatining barcha tarmoqlarini
 * isbotlaydi: edit SUCCESS suppresses send, "message is not modified"
 * benign no-op, boshqa REJECTED/FAILED/null/exception holatlari
 * mavjud retry pipeline'iga fallback.</p>
 */
class TelegramCardRefreshDispatchServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TelegramMessageRenderer renderer = mock(TelegramMessageRenderer.class);
    private final TelegramCardRefreshService refreshService = mock(TelegramCardRefreshService.class);
    private final TelegramCardDispatchRetryingService retryingService =
            mock(TelegramCardDispatchRetryingService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final TelegramCardRefreshDispatchService coordinator =
            new TelegramCardRefreshDispatchService(renderer, refreshService, retryingService, meterRegistry);

    private double refreshCount(String outcome) {
        return meterRegistry.find(TelegramCardRefreshDispatchService.REFRESH_OUTCOMES_METER)
                .tag("outcome", outcome)
                .counters()
                .stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }

    private TelegramCardView cardView() {
        return mock(TelegramCardView.class);
    }

    private TelegramMessage renderedMessage() {
        return new TelegramMessage(
                TENANT_ID, WORK_ITEM_ID,
                "Bug | BUG-1\nLogin xato\nStatus: FIXED",
                List.of(),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                42L);
    }

    private void stubRender(TelegramCardView cv) {
        when(renderer.render(cv)).thenReturn(renderedMessage());
    }

    // ===== edit success path =====

    @Test
    void editSuccessSuppressesSendFallback() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.success(555L));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(refreshService).refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any());
        verifyNoInteractions(retryingService);
    }

    // ===== "message is not modified" benign no-op =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Bad Request: message is not modified",
            "MESSAGE IS NOT MODIFIED",
            "Message Is Not Modified",
            "error_code=400 description=Bad Request: message is not modified: specified new..."
    })
    void messageNotModifiedSuppressesSendFallback(String errorMessage) {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST, errorMessage));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verifyNoInteractions(retryingService);
    }

    // ===== fallback send branches =====

    @Test
    void editRejectedGenericFallsBackToSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST, "Bad Request: message to edit not found"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void editFailedRateLimitFallsBackToSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.RATE_LIMIT, "HTTP 429"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void editFailedNetworkFallsBackToSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.NETWORK_ERROR, "timeout"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void editFailedUnknownFallsBackToSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.UNKNOWN_ERROR, "parse fail"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void editFailedInvalidRequestFallsBackToSend() {
        // FAILED + INVALID_REQUEST — kutilmagan kombinatsiya, lekin
        // coordinator har holatda fallback send chaqirishi shart.
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.INVALID_REQUEST, "weird combo"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void editNullResultFallsBackToSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(null);

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void refreshServiceRuntimeExceptionSwallowedAndFallbackSend() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    @Test
    void noActiveCardRejectedFallsBackToSend() {
        // TelegramCardRefreshService o'zining "no active card" qisqa yo'li.
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST, "no active card found"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(retryingService, times(1)).dispatchWithRetry(cv);
    }

    // ===== render guarantees =====

    @Test
    void renderCalledExactlyOncePerEvent() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.success(555L));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(renderer, times(1)).render(cv);
    }

    @Test
    void rendererRuntimeExceptionSwallowedAndNoFallbackSend() {
        // Fallback send ham rendering qiladi (existing pipeline ichida);
        // shu sababli renderer xatosida coordinator fallback'ni umuman
        // chaqirmaydi va fail-soft pattern saqlanadi.
        TelegramCardView cv = cardView();
        when(renderer.render(cv))
                .thenThrow(new RuntimeException("simulated render failure"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verifyNoInteractions(refreshService);
        verifyNoInteractions(retryingService);
    }

    // ===== defensive input handling =====

    @Test
    void nullCardViewHandledWithoutThrow() {
        coordinator.dispatch(null, TENANT_ID, WORK_ITEM_ID);

        verifyNoInteractions(renderer, refreshService, retryingService);
    }

    @Test
    void nullTenantIdHandledWithoutThrow() {
        TelegramCardView cv = cardView();
        coordinator.dispatch(cv, null, WORK_ITEM_ID);

        verifyNoInteractions(renderer, refreshService, retryingService);
    }

    @Test
    void nullWorkItemIdHandledWithoutThrow() {
        TelegramCardView cv = cardView();
        coordinator.dispatch(cv, TENANT_ID, null);

        verifyNoInteractions(renderer, refreshService, retryingService);
    }

    // ===== argument forwarding =====

    @Test
    void tenantAndWorkItemIdForwardedToRefresh() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.success(555L));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(refreshService).refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any());
    }

    @Test
    void renderedTextAndKeyboardForwardedToRefresh() {
        TelegramCardView cv = cardView();
        TelegramMessage msg = renderedMessage();
        when(renderer.render(cv)).thenReturn(msg);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID),
                eq(msg.getText()), eq(msg.getKeyboard())))
                .thenReturn(TelegramEditMessageTextResult.success(555L));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        verify(refreshService).refresh(eq(TENANT_ID), eq(WORK_ITEM_ID),
                eq(msg.getText()), eq(msg.getKeyboard()));
    }

    @Test
    void fallbackUsesOriginalCardView() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.failed(
                        TelegramGatewayError.NETWORK_ERROR, "timeout"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        // Fallback retry service mavjud sendMessage pipeline'i bilan
        // o'zgartirilmagan, ayni o'sha card view referansini qabul qiladi.
        ArgumentCaptor<TelegramCardView> cardViewCaptor =
                ArgumentCaptor.forClass(TelegramCardView.class);
        verify(retryingService, times(1)).dispatchWithRetry(cardViewCaptor.capture());
        assertThat(cardViewCaptor.getValue()).isSameAs(cv);
    }

    // ===== Phase 189 — Micrometer counter assertions =====

    @Test
    void phase189EditSuccessIncrementsEditedCounter() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.success(555L));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        assertThat(refreshCount("EDITED")).isEqualTo(1.0);
        assertThat(refreshCount("EDIT_REJECTED_FALLBACK_SEND")).isZero();
    }

    @Test
    void phase189NotModifiedIncrementsNotModifiedCounter() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST,
                        "Bad Request: message is not modified"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        assertThat(refreshCount("NOT_MODIFIED")).isEqualTo(1.0);
        assertThat(refreshCount("EDIT_REJECTED_FALLBACK_SEND")).isZero();
    }

    @Test
    void phase189EditRejectedFallbackIncrementsRejectedFallbackCounter() {
        TelegramCardView cv = cardView();
        stubRender(cv);
        when(refreshService.refresh(eq(TENANT_ID), eq(WORK_ITEM_ID), any(), any()))
                .thenReturn(TelegramEditMessageTextResult.rejected(
                        TelegramGatewayError.INVALID_REQUEST,
                        "chat not found"));

        coordinator.dispatch(cv, TENANT_ID, WORK_ITEM_ID);

        assertThat(refreshCount("EDIT_REJECTED_FALLBACK_SEND")).isEqualTo(1.0);
        assertThat(refreshCount("EDITED")).isZero();
    }
}
