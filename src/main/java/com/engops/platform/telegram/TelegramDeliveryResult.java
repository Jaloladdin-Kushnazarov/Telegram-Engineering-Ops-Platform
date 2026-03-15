package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Telegram outbound dispatch natijasi.
 *
 * Bu DTO outbound gateway'dan qaytadigan execution outcome'ni ifodalaydi.
 *
 * Ikki holat:
 * - success: message muvaffaqiyatli yuborildi
 *   - externalMessageId bo'lishi mumkin (Telegram'dan qaytgan message ID)
 * - failure: yuborish muvaffaqiyatsiz bo'ldi
 *   - failureCode: xato turi (masalan "NETWORK_ERROR", "RATE_LIMITED")
 *   - failureReason: xato tavsifi
 *
 * Immutable — factory method'lar orqali yaratiladi.
 */
public class TelegramDeliveryResult {

    private final boolean success;
    private final TelegramDeliveryOperation operation;
    private final UUID tenantId;
    private final UUID workItemId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;
    private final Long externalMessageId;
    private final String failureCode;
    private final String failureReason;

    private TelegramDeliveryResult(boolean success,
                                    TelegramDeliveryOperation operation,
                                    UUID tenantId, UUID workItemId,
                                    UUID targetChatBindingId, Long targetTopicId,
                                    Long externalMessageId,
                                    String failureCode, String failureReason) {
        this.success = success;
        this.operation = operation;
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
        this.externalMessageId = externalMessageId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    /**
     * Muvaffaqiyatli natija yaratadi.
     *
     * @param command bajarilgan command
     * @param externalMessageId Telegram'dan qaytgan message ID (nullable)
     * @return success result
     */
    public static TelegramDeliveryResult success(TelegramDeliveryCommand command,
                                                   Long externalMessageId) {
        return new TelegramDeliveryResult(
                true,
                command.getOperation(),
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                externalMessageId,
                null, null);
    }

    /**
     * Muvaffaqiyatsiz natija yaratadi.
     *
     * @param command bajarilgan command
     * @param failureCode xato turi kodi
     * @param failureReason xato tavsifi
     * @return failure result
     */
    public static TelegramDeliveryResult failure(TelegramDeliveryCommand command,
                                                   String failureCode,
                                                   String failureReason) {
        return new TelegramDeliveryResult(
                false,
                command.getOperation(),
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                null,
                failureCode, failureReason);
    }

    public boolean isSuccess() { return success; }
    public TelegramDeliveryOperation getOperation() { return operation; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public Long getExternalMessageId() { return externalMessageId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
}
