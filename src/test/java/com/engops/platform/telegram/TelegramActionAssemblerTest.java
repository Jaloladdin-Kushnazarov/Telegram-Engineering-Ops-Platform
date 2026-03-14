package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramActionAssembler unit testlari.
 *
 * MVP bug flow action visibility policy'ni tekshiradi:
 * - har bir status uchun to'g'ri action'lar
 * - confirmationRequired semantikasi
 * - callbackData formati
 * - non-BUG type uchun bo'sh action list
 * - noma'lum status uchun bo'sh action list
 * - null guard
 */
class TelegramActionAssemblerTest {

    private final TelegramActionAssembler assembler = new TelegramActionAssembler();

    // --- BUG MVP flow testlari ---

    @Test
    void bugAtBugsStatusShowsStartProcessing() {
        UUID workItemId = UUID.randomUUID();
        TelegramRenderPayload payload = buildPayload(workItemId, "BUG", "BUGS");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.hasActions()).isTrue();
        assertThat(view.getActions()).hasSize(1);

        TelegramCardAction action = view.getActions().getFirst();
        // Structured callback foundation
        assertThat(action.getWorkItemId()).isEqualTo(workItemId);
        assertThat(action.getActionCode()).isEqualTo("START_PROCESSING");
        // Display
        assertThat(action.getLabel()).isEqualTo("Start Processing");
        assertThat(action.getTargetStatusCode()).isEqualTo("PROCESSING");
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.isConfirmationRequired()).isFalse();
        // Transport (derived)
        assertThat(action.getCallbackData()).isEqualTo(workItemId + ":START_PROCESSING");
    }

    @Test
    void bugAtProcessingShowsSendToTesting() {
        UUID workItemId = UUID.randomUUID();
        TelegramRenderPayload payload = buildPayload(workItemId, "BUG", "PROCESSING");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.getActions()).hasSize(1);

        TelegramCardAction action = view.getActions().getFirst();
        assertThat(action.getActionCode()).isEqualTo("SEND_TO_TESTING");
        assertThat(action.getLabel()).isEqualTo("Send to Testing");
        assertThat(action.getTargetStatusCode()).isEqualTo("TESTING");
        assertThat(action.isConfirmationRequired()).isFalse();
    }

    @Test
    void bugAtTestingShowsMarkFixedAndReturnToBugs() {
        UUID workItemId = UUID.randomUUID();
        TelegramRenderPayload payload = buildPayload(workItemId, "BUG", "TESTING");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.getActions()).hasSize(2);

        TelegramCardAction markFixed = view.getActions().get(0);
        assertThat(markFixed.getActionCode()).isEqualTo("MARK_FIXED");
        assertThat(markFixed.getLabel()).isEqualTo("Mark Fixed");
        assertThat(markFixed.getTargetStatusCode()).isEqualTo("FIXED");
        assertThat(markFixed.isConfirmationRequired()).isFalse();

        TelegramCardAction returnToBugs = view.getActions().get(1);
        assertThat(returnToBugs.getActionCode()).isEqualTo("RETURN_TO_BUGS");
        assertThat(returnToBugs.getLabel()).isEqualTo("Return to Bugs");
        assertThat(returnToBugs.getTargetStatusCode()).isEqualTo("BUGS");
        assertThat(returnToBugs.isConfirmationRequired()).isTrue();
    }

    @Test
    void bugAtFixedShowsReopen() {
        UUID workItemId = UUID.randomUUID();
        TelegramRenderPayload payload = buildPayload(workItemId, "BUG", "FIXED");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.getActions()).hasSize(1);

        TelegramCardAction reopen = view.getActions().getFirst();
        assertThat(reopen.getWorkItemId()).isEqualTo(workItemId);
        assertThat(reopen.getActionCode()).isEqualTo("REOPEN");
        assertThat(reopen.getLabel()).isEqualTo("Reopen");
        assertThat(reopen.getTargetStatusCode()).isEqualTo("BUGS");
        assertThat(reopen.isConfirmationRequired()).isTrue();
        assertThat(reopen.getCallbackData()).isEqualTo(workItemId + ":REOPEN");
    }

    // --- Non-BUG va edge case testlari ---

    @Test
    void incidentTypeReturnsNoActions() {
        TelegramRenderPayload payload = buildPayload(UUID.randomUUID(), "INCIDENT", "OPEN");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.hasActions()).isFalse();
        assertThat(view.getActions()).isEmpty();
    }

    @Test
    void taskTypeReturnsNoActions() {
        TelegramRenderPayload payload = buildPayload(UUID.randomUUID(), "TASK", "TODO");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.hasActions()).isFalse();
        assertThat(view.getActions()).isEmpty();
    }

    @Test
    void bugAtUnknownStatusReturnsNoActions() {
        TelegramRenderPayload payload = buildPayload(UUID.randomUUID(), "BUG", "UNKNOWN_STATUS");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.hasActions()).isFalse();
        assertThat(view.getActions()).isEmpty();
    }

    // --- Structural testlari ---

    @Test
    void cardViewContainsRenderPayload() {
        TelegramRenderPayload payload = buildPayload(UUID.randomUUID(), "BUG", "BUGS");

        TelegramCardView view = assembler.assemble(payload);

        assertThat(view.getRenderPayload()).isSameAs(payload);
    }

    @Test
    void actionsListIsImmutable() {
        TelegramRenderPayload payload = buildPayload(UUID.randomUUID(), "BUG", "BUGS");

        TelegramCardView view = assembler.assemble(payload);

        assertThatThrownBy(() -> view.getActions().add(
                new TelegramCardAction(UUID.randomUUID(), "FAKE", "Fake", "X", true, false, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Null guard ---

    @Test
    void nullPayloadRadEtilishi() {
        assertThatThrownBy(() -> assembler.assemble(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }

    @Test
    void cardViewNullRenderPayloadRadEtilishi() {
        assertThatThrownBy(() -> new TelegramCardView(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }

    // --- Helper ---

    private TelegramRenderPayload buildPayload(UUID workItemId, String workItemType,
                                                String currentStatusCode) {
        return new TelegramRenderPayload(
                UUID.randomUUID(),
                workItemId, workItemType + "-1", workItemType,
                "Test title", currentStatusCode,
                "[" + workItemType + "-1] Test title",
                workItemType.substring(0, 1) + workItemType.substring(1).toLowerCase(),
                workItemType.substring(0, 1) + workItemType.substring(1).toLowerCase()
                        + " | " + workItemType + "-1",
                "Status: " + currentStatusCode,
                true,
                UUID.randomUUID(), 42L);
    }
}
