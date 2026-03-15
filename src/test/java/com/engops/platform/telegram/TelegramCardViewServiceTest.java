package com.engops.platform.telegram;

import com.engops.platform.intake.ProjectionPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import org.mockito.InOrder;

/**
 * TelegramCardViewService unit testlari.
 *
 * Orchestration tekshiruvi:
 * - renderAssembler va actionAssembler ketma-ket chaqiriladi
 * - final TelegramCardView to'g'ri qaytadi
 * - null guard ishlaydi
 */
@ExtendWith(MockitoExtension.class)
class TelegramCardViewServiceTest {

    @Mock private TelegramRenderAssembler renderAssembler;
    @Mock private TelegramActionAssembler actionAssembler;

    @InjectMocks
    private TelegramCardViewService cardViewService;

    @Test
    void buildCardViewOrchestrationCorrect() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();

        ProjectionPayload projectionPayload = new ProjectionPayload(
                tenantId,
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                true,
                chatBindingId, 42L);

        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                tenantId,
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                "Bug | BUG-1", "Status: BUGS",
                true,
                chatBindingId, 42L);

        TelegramCardAction action = new TelegramCardAction(
                workItemId, "START_PROCESSING", "Start Processing",
                "PROCESSING", true, false,
                workItemId + ":START_PROCESSING");

        TelegramCardView expectedView = new TelegramCardView(renderPayload, List.of(action));

        when(renderAssembler.assemble(projectionPayload)).thenReturn(renderPayload);
        when(actionAssembler.assemble(renderPayload)).thenReturn(expectedView);

        TelegramCardView result = cardViewService.buildCardView(projectionPayload);

        assertThat(result).isSameAs(expectedView);
        verify(renderAssembler).assemble(projectionPayload);
        verify(actionAssembler).assemble(renderPayload);
    }

    @Test
    void buildCardViewDelegatesInCorrectOrder() {
        ProjectionPayload projectionPayload = new ProjectionPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "INCIDENT-1", "INCIDENT", "DB down", "OPEN",
                "[INCIDENT-1] DB down", "Incident",
                false,
                null, null);

        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                projectionPayload.getTenantId(),
                projectionPayload.getWorkItemId(), "INCIDENT-1", "INCIDENT",
                "DB down", "OPEN",
                "[INCIDENT-1] DB down", "Incident",
                "Incident | INCIDENT-1", "Status: OPEN",
                false,
                null, null);

        TelegramCardView expectedView = new TelegramCardView(renderPayload, List.of());

        when(renderAssembler.assemble(projectionPayload)).thenReturn(renderPayload);
        when(actionAssembler.assemble(renderPayload)).thenReturn(expectedView);

        TelegramCardView result = cardViewService.buildCardView(projectionPayload);

        assertThat(result).isSameAs(expectedView);
        assertThat(result.hasActions()).isFalse();

        InOrder inOrder = inOrder(renderAssembler, actionAssembler);
        inOrder.verify(renderAssembler).assemble(projectionPayload);
        inOrder.verify(actionAssembler).assemble(renderPayload);
        verifyNoMoreInteractions(renderAssembler, actionAssembler);
    }

    @Test
    void nullProjectionPayloadRadEtilishi() {
        assertThatThrownBy(() -> cardViewService.buildCardView(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");

        verifyNoInteractions(renderAssembler, actionAssembler);
    }
}
