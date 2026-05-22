package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramMessageRenderer unit testlari.
 *
 * Pure rendering tekshiruvi:
 * - text format to'g'ri (header + displayTitle + status)
 * - keyboard button count va mapping
 * - action'siz holat — bo'sh keyboard
 * - ko'p action'li holat — har biri alohida row
 * - null guard
 */
class TelegramMessageRendererTest {

    private final TelegramMessageRenderer renderer = new TelegramMessageRenderer();

    @Test
    void bugItemRenderedCorrectly() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();

        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                tenantId,
                workItemId, "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                "Bug | BUG-1", "Status: BUGS",
                null, null,
                true,
                chatBindingId, 42L);

        TelegramCardAction action = new TelegramCardAction(
                workItemId, "START_PROCESSING", "Start Processing",
                "PROCESSING", true, false,
                workItemId + ":START_PROCESSING");

        TelegramCardView cardView = new TelegramCardView(renderPayload, List.of(action));

        TelegramMessage message = renderer.render(cardView);

        assertThat(message.getTenantId()).isEqualTo(tenantId);
        assertThat(message.getWorkItemId()).isEqualTo(workItemId);
        assertThat(message.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(message.getTargetTopicId()).isEqualTo(42L);
        // Phase 194 — null priority/severity must preserve the pre-Phase-194
        // 3-line format byte-for-byte (Phase 179 NOT_MODIFIED safety).
        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS");
        assertThat(message.hasKeyboard()).isTrue();
        assertThat(message.getKeyboard()).hasSize(1);

        TelegramInlineKeyboardRow row = message.getKeyboard().getFirst();
        assertThat(row.getButtons()).hasSize(1);

        TelegramInlineKeyboardButton button = row.getButtons().getFirst();
        assertThat(button.getText()).isEqualTo("Start Processing");
        assertThat(button.getCallbackData()).isEqualTo(workItemId + ":START_PROCESSING");
    }

    @Test
    void noActionsProducesEmptyKeyboard() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();

        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                tenantId,
                workItemId, "INCIDENT-1", "INCIDENT", "DB down", "OPEN",
                "[INCIDENT-1] DB down", "Incident",
                "Incident | INCIDENT-1", "Status: OPEN",
                null, null,
                false,
                null, null);

        TelegramCardView cardView = new TelegramCardView(renderPayload, List.of());

        TelegramMessage message = renderer.render(cardView);

        assertThat(message.getTargetChatBindingId()).isNull();
        assertThat(message.getTargetTopicId()).isNull();
        assertThat(message.getText()).isEqualTo(
                "Incident | INCIDENT-1\n[INCIDENT-1] DB down\nStatus: OPEN");
        assertThat(message.hasKeyboard()).isFalse();
        assertThat(message.getKeyboard()).isEmpty();
    }

    @Test
    void multipleActionsEachInSeparateRow() {
        UUID workItemId = UUID.randomUUID();

        TelegramRenderPayload renderPayload = new TelegramRenderPayload(
                UUID.randomUUID(),
                workItemId, "BUG-5", "BUG", "Crash on save", "TESTING",
                "[BUG-5] Crash on save", "Bug",
                "Bug | BUG-5", "Status: TESTING",
                null, null,
                true,
                UUID.randomUUID(), 10L);

        TelegramCardAction markFixed = new TelegramCardAction(
                workItemId, "MARK_FIXED", "Mark Fixed",
                "FIXED", true, false,
                workItemId + ":MARK_FIXED");

        TelegramCardAction returnToBugs = new TelegramCardAction(
                workItemId, "RETURN_TO_BUGS", "Return to Bugs",
                "BUGS", true, true,
                workItemId + ":RETURN_TO_BUGS");

        TelegramCardView cardView = new TelegramCardView(
                renderPayload, List.of(markFixed, returnToBugs));

        TelegramMessage message = renderer.render(cardView);

        assertThat(message.hasKeyboard()).isTrue();
        assertThat(message.getKeyboard()).hasSize(2);

        TelegramInlineKeyboardRow row1 = message.getKeyboard().get(0);
        assertThat(row1.getButtons()).hasSize(1);
        assertThat(row1.getButtons().getFirst().getText()).isEqualTo("Mark Fixed");
        assertThat(row1.getButtons().getFirst().getCallbackData())
                .isEqualTo(workItemId + ":MARK_FIXED");

        TelegramInlineKeyboardRow row2 = message.getKeyboard().get(1);
        assertThat(row2.getButtons()).hasSize(1);
        assertThat(row2.getButtons().getFirst().getText()).isEqualTo("Return to Bugs");
        assertThat(row2.getButtons().getFirst().getCallbackData())
                .isEqualTo(workItemId + ":RETURN_TO_BUGS");
    }

    // ---- Phase 194 — optional priority/severity rendering ----

    /**
     * Phase 194 — priority alone appends one extra line after status; severity
     * is absent so no severity line is rendered.
     */
    @Test
    void priorityCodePresentRendersOnePriorityLine() {
        TelegramRenderPayload renderPayload = renderPayload("HIGH", null);

        TelegramMessage message = renderer.render(buildCardView(renderPayload));

        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS\nPriority: HIGH");
    }

    /**
     * Phase 194 — severity alone appends one extra line after status; priority
     * is absent so no priority line is rendered.
     */
    @Test
    void severityCodePresentRendersOneSeverityLine() {
        TelegramRenderPayload renderPayload = renderPayload(null, "CRITICAL");

        TelegramMessage message = renderer.render(buildCardView(renderPayload));

        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS\nSeverity: CRITICAL");
    }

    /**
     * Phase 194 — priority and severity both present appear as two extra
     * lines in stable order: priority first, severity second.
     */
    @Test
    void priorityAndSeverityBothPresentRenderTwoLinesInOrder() {
        TelegramRenderPayload renderPayload = renderPayload("HIGH", "CRITICAL");

        TelegramMessage message = renderer.render(buildCardView(renderPayload));

        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS\nPriority: HIGH\nSeverity: CRITICAL");
    }

    /**
     * Phase 194 — blank / whitespace-only values are treated as absent. No
     * empty {@code Priority:} or {@code Severity:} label line is rendered.
     * This preserves Phase 179 NOT_MODIFIED safety for work items that were
     * never assigned a priority/severity.
     */
    @Test
    void blankPriorityAndSeverityNotRendered() {
        TelegramRenderPayload renderPayload = renderPayload("", "   ");

        TelegramMessage message = renderer.render(buildCardView(renderPayload));

        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS");
    }

    /**
     * Phase 194 — priority blank but severity present: only severity line
     * is rendered. Defends against asymmetric blank/non-blank inputs.
     */
    @Test
    void blankPriorityWithPresentSeverityRendersOnlySeverityLine() {
        TelegramRenderPayload renderPayload = renderPayload(" ", "LOW");

        TelegramMessage message = renderer.render(buildCardView(renderPayload));

        assertThat(message.getText()).isEqualTo(
                "Bug | BUG-1\n[BUG-1] Login xato\nStatus: BUGS\nSeverity: LOW");
    }

    @Test
    void nullCardViewRadEtilishi() {
        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null bo'lishi mumkin emas");
    }

    // ---- helpers ----

    /** Phase 194 — fixed BUG/BUGS payload with parameterised priority/severity. */
    private TelegramRenderPayload renderPayload(String priorityCode, String severityCode) {
        return new TelegramRenderPayload(
                UUID.randomUUID(),
                UUID.randomUUID(), "BUG-1", "BUG", "Login xato", "BUGS",
                "[BUG-1] Login xato", "Bug",
                "Bug | BUG-1", "Status: BUGS",
                priorityCode, severityCode,
                true,
                UUID.randomUUID(), 42L);
    }

    private TelegramCardView buildCardView(TelegramRenderPayload payload) {
        return new TelegramCardView(payload, List.of());
    }
}
