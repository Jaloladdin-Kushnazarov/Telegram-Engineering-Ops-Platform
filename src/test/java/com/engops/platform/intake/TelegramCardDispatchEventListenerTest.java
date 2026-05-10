package com.engops.platform.intake;

import com.engops.platform.telegram.TelegramCardDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import com.engops.platform.telegram.TelegramCardViewService;
import com.engops.platform.telegram.TelegramDeliveryAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 * Phase 164 — {@link TelegramCardDispatchEventListener} unit testlari.
 *
 * <p>Listener faqat AFTER_COMMIT bosqichida ishga tushadi va event payload'ni
 * mavjud render → dispatch zanjiriga uzatadi. Bu testlar listener'ning
 * orchestration + fail-soft xulqini tekshiradi (Spring transaction
 * mexanikasi emas — bu Spring framework'ning o'zining contract'i).</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardDispatchEventListenerTest {

    @Mock private ProjectionAssembler projectionAssembler;
    @Mock private TelegramCardViewService telegramCardViewService;
    @Mock private TelegramCardDispatchService telegramCardDispatchService;

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
        when(telegramCardDispatchService.dispatchAttempt(cardView)).thenReturn(attempt);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }

    @Test
    void deliveryReadyEventForWorkflowChainniIshgaTushiradi() {
        PreparedDeliveryTarget target = readyTarget("PROCESSING");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        TelegramDeliveryAttempt attempt = mock(TelegramDeliveryAttempt.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchService.dispatchAttempt(cardView)).thenReturn(attempt);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION, "PROCESSING");

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }

    @Test
    void targetDeliveryReadyEmasBolsaChainChaqirilmaydi() {
        PreparedDeliveryTarget target = notReadyTarget();
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchService);
    }

    @Test
    void targetNullBolsaChainChaqirilmaydi() {
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                null, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchService);
    }

    @Test
    void eventNullBolsaChainChaqirilmaydi() {
        listener.onTelegramCardDispatchRequested(null);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardDispatchService);
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
        verify(telegramCardDispatchService, never()).dispatchAttempt(any());
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
        verify(telegramCardDispatchService, never()).dispatchAttempt(any());
    }

    /**
     * Phase 164 mini-fix: listener metodi
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} hamda
     * {@code @Transactional(propagation = REQUIRES_NEW)} bilan annotatsiya
     * qilingan bo'lishi shart. Bu kombinatsiya AFTER_COMMIT callback ichidagi
     * delivery_attempt insertini originating business transaction'idan
     * decouple qiladi va deterministik commit semantikasini ta'minlaydi.
     */
    @Test
    void listenerMetodiAfterCommitVaRequiresNewBilanAnnotated() throws Exception {
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

        Transactional txAnnotation = method.getAnnotation(Transactional.class);
        org.assertj.core.api.Assertions.assertThat(txAnnotation)
                .as("@Transactional mavjud bo'lishi shart (Phase 164 mini-fix)")
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(txAnnotation.propagation())
                .as("propagation = REQUIRES_NEW bo'lishi shart — delivery_attempt persistence "
                        + "originating business transaction'idan decouple bo'lishi uchun")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void dispatchServiceExceptionTashlasaListenerYutadi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        when(telegramCardDispatchService.dispatchAttempt(cardView))
                .thenThrow(new RuntimeException("simulated outbound dispatch failure"));

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verify(telegramCardDispatchService).dispatchAttempt(cardView);
    }
}
