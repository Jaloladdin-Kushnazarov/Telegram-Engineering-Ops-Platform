package com.engops.platform.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 168 — {@link TelegramCardDispatchRetryingService} unit testlari.
 *
 * <p>Maqsad: retry policy invariantlarini lock qilish:</p>
 * <ul>
 *   <li>retryable — RATE_LIMIT, NETWORK_ERROR</li>
 *   <li>non-retryable — DELIVERED, REJECTED, UNKNOWN_ERROR (stub-mode safety)</li>
 *   <li>capped exponential backoff schedule</li>
 *   <li>oxirgi urinishdan keyin sleep qilinmaydi</li>
 *   <li>InterruptedException → interrupt flag tiklanadi va loop to'xtaydi</li>
 *   <li>{@code enabled=false} → retry yo'q</li>
 *   <li>{@code maxAttempts=1} → retry yo'q</li>
 * </ul>
 *
 * <p>Real {@link Thread#sleep} chaqirilmaydi —
 * {@link TelegramCardDispatchRetryingService.Sleeper} test double sleep
 * davomiyliklarini yig'adi va isbotlash uchun ishlatiladi.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardDispatchRetryingServiceTest {

    @Mock private TelegramCardDispatchService cardDispatchService;
    private TelegramRetryProperties properties;
    private RecordingSleeper sleeper;
    private TelegramCardDispatchRetryingService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID workItemId = UUID.randomUUID();
    private final UUID chatBindingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new TelegramRetryProperties();
        // Defaults: enabled=true, maxAttempts=3, initialBackoffMs=500,
        // maxBackoffMs=5000, multiplier=2.0
        sleeper = new RecordingSleeper();
        service = new TelegramCardDispatchRetryingService(cardDispatchService, properties, sleeper);
    }

    // --- Happy path ---

    @Test
    void birinchiUrinishdaMuvaffaqiyatYagonaChaqiruv() {
        TelegramDeliveryAttempt delivered = deliveredAttempt();
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(delivered);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(delivered);
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).isEmpty();
    }

    // --- Retryable then success ---

    @Test
    void rateLimitKeyinMuvaffaqiyat() {
        TelegramDeliveryAttempt rateLimited = failedAttempt(TelegramGatewayError.RATE_LIMIT.name());
        TelegramDeliveryAttempt delivered = deliveredAttempt();
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rateLimited)
                .thenReturn(delivered);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(delivered);
        verify(cardDispatchService, times(2)).dispatchAttempt(view);
        // Birinchi failure'dan keyin bitta sleep — initial * multiplier^0 = 500 ms.
        assertThat(sleeper.sleeps).containsExactly(500L);
    }

    @Test
    void networkErrorKeyinMuvaffaqiyat() {
        TelegramDeliveryAttempt networkError = failedAttempt(TelegramGatewayError.NETWORK_ERROR.name());
        TelegramDeliveryAttempt delivered = deliveredAttempt();
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(networkError)
                .thenReturn(delivered);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(delivered);
        verify(cardDispatchService, times(2)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).containsExactly(500L);
    }

    // --- Retry exhausted ---

    @Test
    void rateLimitMaxAttemptsTugaganidaTugaydi() {
        TelegramDeliveryAttempt rateLimited = failedAttempt(TelegramGatewayError.RATE_LIMIT.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rateLimited);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        // maxAttempts default = 3 → 3 ta urinish
        assertThat(result).isSameAs(rateLimited);
        verify(cardDispatchService, times(3)).dispatchAttempt(view);
        // Ikki sleep (oxirgi urinishdan keyin sleep yo'q):
        // retryIndex=0 → 500 * 2^0 = 500 ms (cap 5000 ostida)
        // retryIndex=1 → 500 * 2^1 = 1000 ms (cap 5000 ostida)
        assertThat(sleeper.sleeps).containsExactly(500L, 1000L);
    }

    // --- Non-retryable outcomes ---

    @Test
    void invalidRequestRejectedRetryQilinmaydi() {
        TelegramDeliveryAttempt rejected = rejectedAttempt(TelegramGatewayError.INVALID_REQUEST.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rejected);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(rejected);
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).isEmpty();
    }

    @Test
    void unknownErrorRetryQilinmaydiStubModeSafety() {
        // Stub gateway UNKNOWN_ERROR + "Telegram outbound gateway hali implement
        // qilinmagan" qaytaradi (Phase 166 mini-fix). Bu yo'l retry loopiga
        // kirib ketmasligi shart — aks holda local dev maxAttempts marta sleep
        // qiladi va exponential backoff foydasiz uzaytiradi.
        TelegramDeliveryAttempt unknownError = failedAttempt(TelegramGatewayError.UNKNOWN_ERROR.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(unknownError);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(unknownError);
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).isEmpty();
    }

    // --- Configuration toggles ---

    @Test
    void retryDisabledHolatdaRateLimitHamRetryQilinmaydi() {
        properties.setEnabled(false);
        TelegramDeliveryAttempt rateLimited = failedAttempt(TelegramGatewayError.RATE_LIMIT.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rateLimited);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(rateLimited);
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).isEmpty();
    }

    @Test
    void maxAttemptsBittaHolatdaRetryQilinmaydi() {
        properties.setMaxAttempts(1);
        TelegramDeliveryAttempt rateLimited = failedAttempt(TelegramGatewayError.RATE_LIMIT.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rateLimited);

        TelegramCardView view = mockView();
        TelegramDeliveryAttempt result = service.dispatchWithRetry(view);

        assertThat(result).isSameAs(rateLimited);
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        assertThat(sleeper.sleeps).isEmpty();
    }

    // --- Interrupt handling ---

    @Test
    void uxlashPaytidaInterruptedExceptionInterruptFlagniTiklaydi() {
        TelegramDeliveryAttempt rateLimited = failedAttempt(TelegramGatewayError.RATE_LIMIT.name());
        when(cardDispatchService.dispatchAttempt(any(TelegramCardView.class)))
                .thenReturn(rateLimited);
        sleeper.throwInterruptedAfter = 1;

        TelegramCardView view = mockView();
        // Sleep'da interrupt bo'lsa, retry to'xtaydi va so'nggi attempt qaytadi.
        // Test execution paytida thread interrupt flag'ini tozalab qo'yamiz —
        // boshqa testlarga ta'sir qilmasligi uchun.
        boolean interrupted;
        try {
            TelegramDeliveryAttempt result = service.dispatchWithRetry(view);
            assertThat(result).isSameAs(rateLimited);
        } finally {
            interrupted = Thread.interrupted();
        }
        assertThat(interrupted).as("Interrupt flag tiklangan bo'lishi shart").isTrue();
        verify(cardDispatchService, times(1)).dispatchAttempt(view);
        // Bitta sleep urinishi bo'lgan (va u InterruptedException tashlagan).
        assertThat(sleeper.sleeps).containsExactly(500L);
    }

    // --- Defensive ---

    @Test
    void nullCardViewIllegalArgumentException() {
        assertThatThrownBy(() -> service.dispatchWithRetry(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TelegramCardView null");
        verify(cardDispatchService, never()).dispatchAttempt(any());
    }

    // --- Backoff math ---

    @Test
    void backoffSchedulingExponentialVaCappingTo5000() {
        properties.setMaxAttempts(6);
        properties.setInitialBackoffMs(500);
        properties.setMaxBackoffMs(5000);
        properties.setMultiplier(2.0);

        // retryIndex 0 → 500
        // retryIndex 1 → 1000
        // retryIndex 2 → 2000
        // retryIndex 3 → 4000
        // retryIndex 4 → cap 5000 (8000 cap'dan oshib ketadi)
        assertThat(service.computeBackoffMs(0)).isEqualTo(500L);
        assertThat(service.computeBackoffMs(1)).isEqualTo(1000L);
        assertThat(service.computeBackoffMs(2)).isEqualTo(2000L);
        assertThat(service.computeBackoffMs(3)).isEqualTo(4000L);
        assertThat(service.computeBackoffMs(4)).isEqualTo(5000L);
        assertThat(service.computeBackoffMs(5)).isEqualTo(5000L);
    }

    // --- Helpers ---

    private TelegramDeliveryCommand sampleCommand() {
        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                tenantId, workItemId,
                chatBindingId, 42L,
                "Bug | BUG-1\n[BUG-1] Test\nStatus: BUGS",
                List.of());
    }

    private TelegramDeliveryAttempt deliveredAttempt() {
        TelegramDeliveryCommand command = sampleCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.success(command, 99001L);
        return TelegramDeliveryAttempt.of(command, result, java.time.Instant.parse("2026-05-10T10:00:00Z"));
    }

    private TelegramDeliveryAttempt failedAttempt(String failureCode) {
        TelegramDeliveryCommand command = sampleCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.failed(
                command, failureCode, "simulated " + failureCode);
        return TelegramDeliveryAttempt.of(command, result, java.time.Instant.parse("2026-05-10T10:00:00Z"));
    }

    private TelegramDeliveryAttempt rejectedAttempt(String failureCode) {
        TelegramDeliveryCommand command = sampleCommand();
        TelegramDeliveryResult result = TelegramDeliveryResult.rejected(
                command, failureCode, "simulated " + failureCode);
        return TelegramDeliveryAttempt.of(command, result, java.time.Instant.parse("2026-05-10T10:00:00Z"));
    }

    private TelegramCardView mockView() {
        return org.mockito.Mockito.mock(TelegramCardView.class);
    }

    /**
     * Test sleeper — real {@link Thread#sleep} o'rniga sleep davomiyligini
     * {@link #sleeps} list'iga yozadi. Optional ravishda {@code throwInterruptedAfter}
     * inchi sleep urinishida {@link InterruptedException} tashlaydi.
     */
    private static class RecordingSleeper implements TelegramCardDispatchRetryingService.Sleeper {
        final List<Long> sleeps = new ArrayList<>();
        int throwInterruptedAfter = -1; // -1 = hech qachon

        @Override
        public void sleepMillis(long ms) throws InterruptedException {
            sleeps.add(ms);
            if (throwInterruptedAfter >= 0 && sleeps.size() == throwInterruptedAfter) {
                throw new InterruptedException("simulated interrupt");
            }
        }
    }
}
