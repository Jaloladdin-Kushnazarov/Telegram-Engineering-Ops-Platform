package com.engops.platform.telegram;

/**
 * Phase 177 — Telegram {@code editMessageText} chaqiruvining
 * transport-level natijasi.
 *
 * <p>Mavjud {@link TelegramGatewayResult} va
 * {@link TelegramAcknowledgeCallbackResult} shape'iga moslashtirilgan.
 * Telegram {@code editMessageText} muvaffaqiyatli bo'lganda
 * tahrirlangan message'ni qaytaradi — uning {@code message_id} so'rovda
 * yuborilgan id bilan bir xil bo'ladi va {@code telegramMessageId}
 * maydoniga yoziladi.</p>
 *
 * <p>Uch holat:</p>
 * <ul>
 *   <li>{@code SUCCESS} — Telegram message muvaffaqiyatli tahrirlandi.</li>
 *   <li>{@code REJECTED} — Telegram permanent rad etdi (masalan
 *       "message is not modified", message topilmadi, edit ruxsat
 *       berilmagan). Retry mantiqsiz.</li>
 *   <li>{@code FAILED} — vaqtinchalik xato (network, rate limit, 5xx)
 *       yoki kutilmagan parse/runtime xato. Phase 177 retry qilmaydi —
 *       retry siyosati Phase 178 wiring bilan birga ko'rib chiqiladi.</li>
 * </ul>
 *
 * <p>Immutable — factory method'lar orqali yaratiladi.</p>
 */
public class TelegramEditMessageTextResult {

    private final ResultType resultType;
    private final Long telegramMessageId;
    private final TelegramGatewayError error;
    private final String errorMessage;

    public enum ResultType {
        SUCCESS,
        REJECTED,
        FAILED
    }

    private TelegramEditMessageTextResult(ResultType resultType,
                                            Long telegramMessageId,
                                            TelegramGatewayError error,
                                            String errorMessage) {
        this.resultType = resultType;
        this.telegramMessageId = telegramMessageId;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static TelegramEditMessageTextResult success(Long telegramMessageId) {
        return new TelegramEditMessageTextResult(
                ResultType.SUCCESS, telegramMessageId, null, null);
    }

    public static TelegramEditMessageTextResult rejected(TelegramGatewayError error,
                                                          String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramEditMessageTextResult(
                ResultType.REJECTED, null, error, errorMessage);
    }

    public static TelegramEditMessageTextResult failed(TelegramGatewayError error,
                                                         String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramEditMessageTextResult(
                ResultType.FAILED, null, error, errorMessage);
    }

    public ResultType getResultType() { return resultType; }
    public Long getTelegramMessageId() { return telegramMessageId; }
    public TelegramGatewayError getError() { return error; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isSuccess() { return resultType == ResultType.SUCCESS; }
}
