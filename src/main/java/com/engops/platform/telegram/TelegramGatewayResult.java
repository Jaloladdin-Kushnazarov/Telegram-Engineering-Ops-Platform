package com.engops.platform.telegram;

/**
 * Telegram gateway execution natijasi.
 *
 * Transport-level execution outcome'ni ifodalaydi.
 *
 * Uch holat:
 * - SUCCESS: message muvaffaqiyatli yuborildi
 *   - telegramMessageId bo'lishi mumkin (Telegram'dan qaytgan message ID)
 * - REJECTED: request server tomonidan rad etildi (masalan invalid chat ID)
 *   - error va errorMessage to'ldiriladi
 * - FAILED: texnik xato (masalan network timeout)
 *   - error va errorMessage to'ldiriladi
 *
 * Immutable — factory method'lar orqali yaratiladi.
 */
public class TelegramGatewayResult {

    private final ResultType resultType;
    private final Long telegramMessageId;
    private final TelegramGatewayError error;
    private final String errorMessage;

    /**
     * Gateway execution natija turlari.
     */
    public enum ResultType {
        SUCCESS,
        REJECTED,
        FAILED
    }

    private TelegramGatewayResult(ResultType resultType,
                                    Long telegramMessageId,
                                    TelegramGatewayError error,
                                    String errorMessage) {
        this.resultType = resultType;
        this.telegramMessageId = telegramMessageId;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    /**
     * Muvaffaqiyatli natija yaratadi.
     *
     * @param telegramMessageId Telegram'dan qaytgan message ID (nullable)
     * @return success result
     */
    public static TelegramGatewayResult success(Long telegramMessageId) {
        return new TelegramGatewayResult(
                ResultType.SUCCESS, telegramMessageId, null, null);
    }

    /**
     * Rad etilgan natija yaratadi.
     *
     * @param error xato klassifikatsiyasi
     * @param errorMessage xato tavsifi
     * @return rejected result
     */
    public static TelegramGatewayResult rejected(TelegramGatewayError error,
                                                   String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramGatewayResult(
                ResultType.REJECTED, null, error, errorMessage);
    }

    /**
     * Muvaffaqiyatsiz natija yaratadi.
     *
     * @param error xato klassifikatsiyasi
     * @param errorMessage xato tavsifi
     * @return failed result
     */
    public static TelegramGatewayResult failed(TelegramGatewayError error,
                                                 String errorMessage) {
        if (error == null) {
            throw new IllegalArgumentException("error null bo'lishi mumkin emas");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramGatewayResult(
                ResultType.FAILED, null, error, errorMessage);
    }

    public ResultType getResultType() { return resultType; }
    public Long getTelegramMessageId() { return telegramMessageId; }
    public TelegramGatewayError getError() { return error; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isSuccess() { return resultType == ResultType.SUCCESS; }
}
