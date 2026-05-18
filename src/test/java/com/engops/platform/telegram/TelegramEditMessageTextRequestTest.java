package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 177 — {@link TelegramEditMessageTextRequest} canonical
 * constructor validatsiya testlari.
 */
class TelegramEditMessageTextRequestTest {

    @Test
    void nullChatIdRejected() {
        assertThatThrownBy(() ->
                new TelegramEditMessageTextRequest(null, 555L, "new text", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullMessageIdRejected() {
        assertThatThrownBy(() ->
                new TelegramEditMessageTextRequest(-1001234567890L, null, "new text", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankTextRejected(String text) {
        assertThatThrownBy(() ->
                new TelegramEditMessageTextRequest(-1001234567890L, 555L, text, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validRequestAccepted() {
        TelegramEditMessageTextRequest req = new TelegramEditMessageTextRequest(
                -1001234567890L, 555L, "Bug | BUG-1\nStatus: FIXED", null);

        assertThat(req.chatId()).isEqualTo(-1001234567890L);
        assertThat(req.messageId()).isEqualTo(555L);
        assertThat(req.text()).isEqualTo("Bug | BUG-1\nStatus: FIXED");
        assertThat(req.keyboard()).isEmpty();
        assertThat(req.hasKeyboard()).isFalse();
    }

    @Test
    void nullKeyboardNormalizedToEmptyList() {
        TelegramEditMessageTextRequest req = new TelegramEditMessageTextRequest(
                1L, 2L, "x", null);
        assertThat(req.keyboard()).isEmpty();
        assertThat(req.hasKeyboard()).isFalse();
    }

    @Test
    void keyboardCopiedDefensively() {
        var row = new TelegramInlineKeyboardRow(List.of(
                new TelegramInlineKeyboardButton("Mark Fixed", "uuid:MARK_FIXED")));
        TelegramEditMessageTextRequest req = new TelegramEditMessageTextRequest(
                1L, 2L, "x", List.of(row));

        assertThat(req.keyboard()).hasSize(1);
        assertThat(req.hasKeyboard()).isTrue();
        assertThat(req.keyboard().get(0).getButtons().get(0).getText()).isEqualTo("Mark Fixed");
    }

    @Test
    void textAtExactly4096CharsAccepted() {
        String text = "a".repeat(4096);
        TelegramEditMessageTextRequest req = new TelegramEditMessageTextRequest(1L, 2L, text, null);
        assertThat(req.text()).hasSize(4096);
    }

    @Test
    void textAt4097CharsRejected() {
        String text = "a".repeat(4097);
        assertThatThrownBy(() -> new TelegramEditMessageTextRequest(1L, 2L, text, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxTextLengthBoundaryLockedAt4096() {
        assertThat(TelegramEditMessageTextRequest.MAX_TEXT_LENGTH).isEqualTo(4096);
    }
}
