package com.engops.platform.intake;

import com.engops.platform.telegram.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 164 + Phase 168 — {@link TelegramCardDispatchEventListener} unit testlari.
 *
 * <p>Listener faqat AFTER_COMMIT bosqichida ishga tushadi va event payload'ni
 * mavjud render zanjiriga + retrying dispatch service'ga uzatadi.</p>
 *
 * <p><strong>Phase 168 o'zgarishi:</strong> listener {@link TelegramCardDispatchService}
 * o'rniga {@link TelegramCardDispatchRetryingService}'ni inject qiladi —
 * retry/backoff o'sha qatlamda. Listener metodi endi
 * {@code @Transactional(REQUIRES_NEW)} EMAS — boundary
 * {@code JpaTelegramDeliveryAttemptPersistence.save} ga ko'chirilgan
 * (har bir attempt persistence o'z REQUIRES_NEW transaction'ida).</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardDispatchEventListenerTest {

    @Mock private ProjectionAssembler projectionAssembler;
    @Mock private TelegramCardViewService telegramCardViewService;
    @Mock private TelegramCardDispatchRetryingService telegramCardDispatchRetryingService;

    @InjectMocks
    private TelegramCardDispatchEventListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID workItemId = UUID.randomUUID();
    private final UUID chatBindingId = UUID.randomUUID();

    private PreparedDeliveryTarget readyTarget(String currentStatusCode) {
        return new PreparedDeliveryTarget(
                tenantId, workItemId, "BUG-1", "BUG", "Test bug",
                currentStatusCode,
                true,
                chatBindingId, 42L);
    }

    private PreparedDeliveryTarget notReadyTarget() {
        return new PreparedDeliveryTarget(
                tenantId, workItemId, "BUG-2", "BUG", "Test bug",
                "BUGS",
                false,
                null, null);
    }

    @Test
    void deliveryReadyEventForIntakeChainniIshgaTushiradi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt attempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchRetryingService.dispatchWithRetry(cardView)).thenReturn(attempt);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchRetryingService).dispatchWithRetry(cardView);
    }

    @Test
    void deliveryReadyEventForWorkflowChainniIshgaTushiradi() {
        PreparedDeliveryTarget target = readyTarget("PROCESSING");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt attempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchRetryingService.dispatchWithRetry(cardView)).thenReturn(attempt);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION, "PROCESSING");

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchRetryingService).dispatchWithRetry(cardView);
    }

    @Test
    void targetDeliveryReadyEmasBolsaChainChaqirilmaydi() {
        PreparedDeliveryTarget target = notReadyTarget();
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchRetryingService);
    }

    @Test
    void targetNullBolsaChainChaqirilmaydi() {
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                null, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchRetryingService);
    }

    @Test
    void eventNullBolsaChainChaqirilmaydi() {
        listener.onTelegramCardDispatchRequested(null);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchRetryingService);
    }

    @Test
    void projectionAssemblerExceptionTashlasaListenerYutadi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        when(projectionAssembler.assemble(target))
                .thenThrow(new RuntimeException("simulated assemble failure"));

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        // Hech qanday exception listener'dan tashqariga chiqmasligi shart.
        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService, never()).buildCardView(any());
        verify(telegramCardDispatchRetryingService, never()).dispatchWithRetry(any());
    }

    @Test
    void cardViewServiceExceptionTashlasaListenerYutadi() {
        PreparedDeliveryTarget target = readyTarget("PROCESSING");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload))
                .thenThrow(new RuntimeException("simulated cardView failure"));

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION, "PROCESSING");

        listener.onTelegramCardDispatchRequested(event);

        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchRetryingService, never()).dispatchWithRetry(any());
    }

    /**
     * Phase 168: listener metodi {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
     * bilan annotatsiya qilingan bo'lishi shart. {@code @Transactional(REQUIRES_NEW)}
     * Phase 164 mini-fix da bu yerda turgan edi, lekin Phase 168 da boundary
     * {@link com.engops.platform.telegram.JpaTelegramDeliveryAttemptPersistence#save}
     * ga ko'chirildi — retry urinishlari va backoff sleep'lar DB connection
     * ushlamasligi uchun.
     */
    @Test
    void listenerMetodiAfterCommitBilanAnnotated() throws Exception {
        Method method = TelegramCardDispatchEventListener.class.getDeclaredMethod(
                "onTelegramCardDispatchRequested", TelegramCardDispatchRequested.class);

        TransactionalEventListener listenerAnnotation =
                method.getAnnotation(TransactionalEventListener.class);
        org.assertj.core.api.Assertions.assertThat(listenerAnnotation)
                .as("@TransactionalEventListener mavjud bo'lishi shart")
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(listenerAnnotation.phase())
                .as("phase = AFTER_COMMIT bo'lishi shart")
                .isEqualTo(TransactionPhase.AFTER_COMMIT);

        // Phase 168: listener'da @Transactional bo'lmasligi shart — REQUIRES_NEW
        // boundary persistence qatlamiga ko'chirilgan (Phase 164 mini-fix
        // contracti saqlanadi, lekin retry sleep'lari uchun fine-grained).
        org.springframework.transaction.annotation.Transactional txAnnotation =
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        org.assertj.core.api.Assertions.assertThat(txAnnotation)
                .as("Phase 168: @Transactional listener metodida bo'lmasligi shart — "
                        + "REQUIRES_NEW JpaTelegramDeliveryAttemptPersistence.save'ga ko'chirilgan")
                .isNull();
    }

    @Test
    void retryingServiceExceptionTashlasaListenerYutadi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchRetryingService.dispatchWithRetry(cardView))
                .thenThrow(new RuntimeException("simulated outbound dispatch failure"));

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verify(telegramCardDispatchRetryingService).dispatchWithRetry(cardView);
    }
}
