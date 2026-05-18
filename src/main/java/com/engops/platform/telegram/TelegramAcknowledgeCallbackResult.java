package com.engops.platform.telegram;

/**
 * Phase 175 — Telegram {@code answerCallbackQuery} chaqiruvining
 * transport-level natijasi.
 *
 * <p>Mavjud {@link TelegramGatewayResult} shape'iga moslashtirilgan,
 * lekin {@code telegramMessageId} maydonisiz (Telegram acknowledgement
 * javobi message identifikatorini qaytarmaydi — faqat
 * {@code {"ok":true}}).</p>
 *
 * <p>Uch holat:</p>
 * <ul>
 *   <li>{@code SUCCESS} — Telegram acknowledgement qabul qildi.</li>
 *   <li>{@code REJECTED} — Telegram permanent rad etdi (masalan,
 *       callback_query id eskirgan yoki noto'g'ri). Retry mantiqsiz.</li>
 *   <li>{@code FAILED} — vaqtinchalik xato (network, rate limit, 5xx).
 *       Phase 175 acknowledgement uchun retry qilmaydi — toast deadline
 *       cheklangan.</li>
 * </ul>
 *
 * <p>Immutable — factory method'lar orqali yaratiladi.</p>
 */
public class TelegramAcknowledgeCallbackResult {

    private final ResultType resultType;
    private final TelegramGatewayError error;
    private final String errorMessage;

    public enum ResultType {
        SUCCESS,
        REJECTED,
        FAILED
    }

    private TelegramAcknowledgeCallbackResult(ResultType resultType,
                                                TelegramGatewayError error,
                                                String errorMessage) {
        this.resultType = resultType;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static TelegramAcknowledgeCallbackResult success() {
        return new TelegramAcknowledgeCallbackResult(ResultType.SUCCESS, null, null);
    }

    public static TelegramAcknowledgeCallbackResult rejected(TelegramGatewayError error,
                                                              String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramAcknowledgeCallbackResult(ResultType.REJECTED, error, errorMessage);
    }

    public static TelegramAcknowledgeCallbackResult failed(TelegramGatewayError error,
                                                             String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramAcknowledgeCallbackResult(ResultType.FAILED, error, errorMessage);
    }

    public ResultType getResultType() { return resultType; }
    public TelegramGatewayError getError() { return error; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isSuccess() { return resultType == ResultType.SUCCESS; }
}
