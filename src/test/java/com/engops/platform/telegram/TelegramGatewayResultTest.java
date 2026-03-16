package com.engops.platform.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramGatewayResult factory method unit testlari.
 *
 * SUCCESS, REJECTED, FAILED factory'larning to'g'ri ishlashini tekshiradi.
 */
class TelegramGatewayResultTest {

    @Test
    void successResultWithMessageId() {
        TelegramGatewayResult result = TelegramGatewayResult.success(12345L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.SUCCESS);
        assertThat(result.getTelegramMessageId()).isEqualTo(12345L);
        assertThat(result.getError()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void successResultWithNullMessageId() {
        TelegramGatewayResult result = TelegramGatewayResult.success(null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTelegramMessageId()).isNull();
    }

    @Test
    void rejectedResult() {
        TelegramGatewayResult result = TelegramGatewayResult.rejected(
                TelegramGatewayError.INVALID_REQUEST,
                "Bad Request: chat not found");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
        assertThat(result.getTelegramMessageId()).isNull();
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.INVALID_REQUEST);
        assertThat(result.getErrorMessage()).isEqualTo("Bad Request: chat not found");
    }

    @Test
    void failedResult() {
        TelegramGatewayResult result = TelegramGatewayResult.failed(
                TelegramGatewayError.NETWORK_ERROR,
                "Connection timed out");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
        assertThat(result.getTelegramMessageId()).isNull();
        assertThat(result.getError()).isEqualTo(TelegramGatewayError.NETWORK_ERROR);
        assertThat(result.getErrorMessage()).isEqualTo("Connection timed out");
    }

    @Test
    void rateLimitRejection() {
        TelegramGatewayResult result = TelegramGatewayResult.rejected(
                TelegramGatewayError.RATE_LIMIT,
                "Too Many Requests: retry after 30");

        assertThat(result.getError()).isEqualTo(TelegramGatewayError.RATE_LIMIT);
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.REJECTED);
    }

    @Test
    void unknownErrorFailure() {
        TelegramGatewayResult result = TelegramGatewayResult.failed(
                TelegramGatewayError.UNKNOWN_ERROR,
                "Unexpected response");

        assertThat(result.getError()).isEqualTo(TelegramGatewayError.UNKNOWN_ERROR);
        assertThat(result.getResultType()).isEqualTo(TelegramGatewayResult.ResultType.FAILED);
    }

    // --- Factory invariant testlari ---

    @Test
    void rejectedWithNullErrorRejected() {
        assertThatThrownBy(() -> TelegramGatewayResult.rejected(null, "some message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error null");
    }

    @Test
    void rejectedWithBlankErrorMessageRejected() {
        assertThatThrownBy(() -> TelegramGatewayResult.rejected(
                TelegramGatewayError.INVALID_REQUEST, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }

    @Test
    void failedWithNullErrorRejected() {
        assertThatThrownBy(() -> TelegramGatewayResult.failed(null, "some message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error null");
    }

    @Test
    void failedWithBlankErrorMessageRejected() {
        assertThatThrownBy(() -> TelegramGatewayResult.failed(
                TelegramGatewayError.NETWORK_ERROR, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }
}
