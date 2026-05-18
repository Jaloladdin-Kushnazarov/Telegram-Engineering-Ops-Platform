package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 175 — {@link TelegramAcknowledgeCallbackRequest} canonical
 * constructor validatsiya testlari.
 */
class TelegramAcknowledgeCallbackRequestTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankCallbackQueryIdRejected(String callbackQueryId) {
        assertThatThrownBy(() ->
                new TelegramAcknowledgeCallbackRequest(callbackQueryId, "Action applied."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankTextRejected(String text) {
        assertThatThrownBy(() -> new TelegramAcknowledgeCallbackRequest("cb-id", text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textAtExactly200CharsAccepted() {
        String text = "a".repeat(200);
        TelegramAcknowledgeCallbackRequest req =
                new TelegramAcknowledgeCallbackRequest("cb-id", text);
        assertThat(req.text()).hasSize(200);
        assertThat(req.callbackQueryId()).isEqualTo("cb-id");
    }

    @Test
    void textAt201CharsRejected() {
        String text = "a".repeat(201);
        assertThatThrownBy(() -> new TelegramAcknowledgeCallbackRequest("cb-id", text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxTextLengthBoundaryLockedAt200() {
        assertThat(TelegramAcknowledgeCallbackRequest.MAX_TEXT_LENGTH).isEqualTo(200);
    }
}
