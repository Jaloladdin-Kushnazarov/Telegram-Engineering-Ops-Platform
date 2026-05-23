package com.engops.platform.intake;

import com.engops.platform.telegram.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 164 + Phase 168 + Phase 179 — {@link TelegramCardDispatchEventListener}
 * unit testlari.
 *
 * <p>Listener faqat AFTER_COMMIT bosqichida ishga tushadi va event payload'ni
 * mavjud render zanjiriga + edit-first/send-as-fallback coordinator'ga
 * uzatadi.</p>
 *
 * <p><strong>Phase 179 o'zgarishi:</strong> listener endi
 * {@link TelegramCardDispatchRetryingService} o'rniga
 * {@link TelegramCardRefreshDispatchService}'ni inject qiladi va unga
 * delegate qiladi. Coordinator edit-first qarorini qabul qiladi va kerak
 * bo'lganda mavjud retry pipeline'ni o'zi chaqiradi. Listener thin bo'lib
 * qoladi va AFTER_COMMIT + fail-soft invariantlari saqlanadi.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardDispatchEventListenerTest {

    @Mock private ProjectionAssembler projectionAssembler;
    @Mock private TelegramCardViewService telegramCardViewService;
    @Mock private TelegramCardRefreshDispatchService telegramCardRefreshDispatchService;

    @InjectMocks
    private TelegramCardDispatchEventListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID workItemId = UUID.randomUUID();
    private final UUID chatBindingId = UUID.randomUUID();

    private PreparedDeliveryTarget readyTarget(String currentStatusCode) {
        return new PreparedDeliveryTarget(
                tenantId, workItemId, "BUG-1", "BUG", "Test bug",
                currentStatusCode,
                null, null,
                true,
                chatBindingId, 42L,
                null);
    }

    private PreparedDeliveryTarget notReadyTarget() {
        return new PreparedDeliveryTarget(
                tenantId, workItemId, "BUG-2", "BUG", "Test bug",
                "BUGS",
                null, null,
                false,
                null, null,
                null);
    }

    @Test
    void deliveryReadyEventForIntakeChainniIshgaTushiradi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardRefreshDispatchService).dispatch(cardView, tenantId, workItemId);
    }

    @Test
    void deliveryReadyEventForWorkflowChainniIshgaTushiradi() {
        PreparedDeliveryTarget target = readyTarget("PROCESSING");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION, "PROCESSING");

        listener.onTelegramCardDispatchRequested(event);

        verify(projectionAssembler).assemble(target);
        verify(telegramCardViewService).buildCardView(payload);
        verify(telegramCardRefreshDispatchService).dispatch(cardView, tenantId, workItemId);
    }

    /**
     * Phase 179: coordinator argumenti aniq cardView + target.tenantId +
     * target.workItemId bo'lishi shart.
     */
    @Test
    void coordinatorArgsCardViewTenantAndWorkItemIdMatchTarget() {
        PreparedDeliveryTarget target = readyTarget("PROCESSING");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_WORKFLOW_TRANSITION, "PROCESSING");

        listener.onTelegramCardDispatchRequested(event);

        ArgumentCaptor<TelegramCardView> cardViewCaptor =
                ArgumentCaptor.forClass(TelegramCardView.class);
        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> workItemCaptor = ArgumentCaptor.forClass(UUID.class);

        verify(telegramCardRefreshDispatchService).dispatch(
                cardViewCaptor.capture(), tenantCaptor.capture(), workItemCaptor.capture());

        org.assertj.core.api.Assertions.assertThat(cardViewCaptor.getValue()).isSameAs(cardView);
        org.assertj.core.api.Assertions.assertThat(tenantCaptor.getValue()).isEqualTo(tenantId);
        org.assertj.core.api.Assertions.assertThat(workItemCaptor.getValue()).isEqualTo(workItemId);
    }

    @Test
    void targetDeliveryReadyEmasBolsaChainChaqirilmaydi() {
        PreparedDeliveryTarget target = notReadyTarget();
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardRefreshDispatchService);
    }

    @Test
    void targetNullBolsaChainChaqirilmaydi() {
        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                null, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        listener.onTelegramCardDispatchRequested(event);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardRefreshDispatchService);
    }

    @Test
    void eventNullBolsaChainChaqirilmaydi() {
        listener.onTelegramCardDispatchRequested(null);

        verifyNoInteractions(projectionAssembler, telegramCardViewService, telegramCardRefreshDispatchService);
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
        verify(telegramCardRefreshDispatchService, never()).dispatch(any(), any(), any());
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
        verify(telegramCardRefreshDispatchService, never()).dispatch(any(), any(), any());
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
    void coordinatorExceptionTashlasaListenerYutadi() {
        PreparedDeliveryTarget target = readyTarget("BUGS");
        ProjectionPayload payload = mock(ProjectionPayload.class);
        TelegramCardView cardView = mock(TelegramCardView.class);
        when(projectionAssembler.assemble(target)).thenReturn(payload);
        when(telegramCardViewService.buildCardView(payload)).thenReturn(cardView);
        doThrow(new RuntimeException("simulated coordinator failure"))
                .when(telegramCardRefreshDispatchService)
                .dispatch(eq(cardView), eq(tenantId), eq(workItemId));

        TelegramCardDispatchRequested event = new TelegramCardDispatchRequested(
                target, TelegramCardDispatchRequested.SOURCE_INTAKE, null);

        // Listener exception'ni yutadi va outside'ga chiqarmaydi.
        listener.onTelegramCardDispatchRequested(event);

        verify(telegramCardRefreshDispatchService).dispatch(cardView, tenantId, workItemId);
    }
}
