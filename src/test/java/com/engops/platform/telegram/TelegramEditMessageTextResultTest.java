package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 177 — {@link TelegramEditMessageTextResult} factory validatsiya
 * testlari.
 */
class TelegramEditMessageTextResultTest {

    @Test
    void successCarriesMessageId() {
        TelegramEditMessageTextResult result = TelegramEditMessageTextResult.success(12345L);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.SUCCESS);
        assertThat(result.getTelegramMessageId()).isEqualTo(12345L);
        assertThat(result.getError()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void successAcceptsNullMessageId() {
        TelegramEditMessageTextResult result = TelegramEditMessageTextResult.success(null);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTelegramMessageId()).isNull();
    }

    @Test
    void rejectedRequiresNonNullError() {
        assertThatThrownBy(() -> TelegramEditMessageTextResult.rejected(null, "msg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedRequiresNonBlankMessage() {
        assertThatThrownBy(() ->
                TelegramEditMessageTextResult.rejected(TelegramGatewayError.INVALID_REQUEST, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                TelegramEditMessageTextResult.rejected(TelegramGatewayError.INVALID_REQUEST, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                TelegramEditMessageTextResult.rejected(TelegramGatewayError.INVALID_REQUEST, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedRequiresNonNullError() {
        assertThatThrownBy(() -> TelegramEditMessageTextResult.failed(null, "msg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedRequiresNonBlankMessage() {
        assertThatThrownBy(() ->
                TelegramEditMessageTextResult.failed(TelegramGatewayError.UNKNOWN_ERROR, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedExposesErrorAndMessage() {
        TelegramEditMessageTextResult result = TelegramEditMessageTextResult.rejected(
                TelegramGatewayError.INVALID_REQUEST, "stale");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.REJECTED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).isEqualTo("stale");
        assertThat(result.getTelegramMessageId()).isNull();
    }

    @Test
    void failedExposesErrorAndMessage() {
        TelegramEditMessageTextResult result = TelegramEditMessageTextResult.failed(
                TelegramGatewayError.NETWORK_ERROR, "timeout");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType())
                .isEqualTo(TelegramEditMessageTextResult.ResultType.FAILED);
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("timeout");
    }
}
