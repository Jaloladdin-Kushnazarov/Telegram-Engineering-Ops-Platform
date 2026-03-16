package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Telegram outbound delivery natijasi.
 *
 * Application-level delivery lifecycle outcome'ni ifodalaydi.
 *
 * Uch holat (DeliveryOutcome):
 * - DELIVERED: message muvaffaqiyatli yuborildi
 *   - externalMessageId bo'lishi mumkin (Telegram'dan qaytgan message ID)
 * - REJECTED: gateway/transport tomondan rad etildi (masalan invalid chat ID)
 *   - failureCode: xato klassifikatsiyasi
 *   - failureReason: xato tavsifi
 * - FAILED: texnik/tizim xatosi (masalan network timeout)
 *   - failureCode: xato klassifikatsiyasi
 *   - failureReason: xato tavsifi
 *
 * Muhim:
 * - REJECTED va FAILED farqlanadi — ikkalasi ham muvaffaqiyatsiz, lekin sababi turlicha
 * - Transport-level TelegramGatewayResult bu yerda expose qilinmaydi
 * - Immutable — factory method'lar orqali yaratiladi
 */
public class TelegramDeliveryResult {

    /**
     * Application-level delivery outcome klassifikatsiyasi.
     *
     * - DELIVERED: muvaffaqiyatli yuborildi
     * - REJECTED: gateway/transport tomondan rad etildi
     * - FAILED: texnik/tizim xatosi
     */
    public enum DeliveryOutcome {
        DELIVERED,
        REJECTED,
        FAILED
    }

    private final DeliveryOutcome deliveryOutcome;
    private final TelegramDeliveryOperation operation;
    private final UUID tenantId;
    private final UUID workItemId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;
    private final Long externalMessageId;
    private final String failureCode;
    private final String failureReason;

    private TelegramDeliveryResult(DeliveryOutcome deliveryOutcome,
                                    TelegramDeliveryOperation operation,
                                    UUID tenantId, UUID workItemId,
                                    UUID targetChatBindingId, Long targetTopicId,
                                    Long externalMessageId,
                                    String failureCode, String failureReason) {
        this.deliveryOutcome = deliveryOutcome;
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
     * Muvaffaqiyatli delivery natija yaratadi (DELIVERED).
     *
     * @param command bajarilgan command
     * @param externalMessageId Telegram'dan qaytgan message ID (nullable)
     * @return delivered result
     */
    public static TelegramDeliveryResult success(TelegramDeliveryCommand command,
                                                   Long externalMessageId) {
        if (command == null) {
            throw new IllegalArgumentException("command null bo'lishi mumkin emas");
        }
        return new TelegramDeliveryResult(
                DeliveryOutcome.DELIVERED,
                command.getOperation(),
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                externalMessageId,
                null, null);
    }

    /**
     * Rad etilgan delivery natija yaratadi (REJECTED).
     *
     * Gateway/transport tomondan rad etildi — masalan invalid chat ID,
     * permission denied, yoki boshqa business-level rejection.
     *
     * @param command bajarilgan command
     * @param failureCode xato klassifikatsiyasi
     * @param failureReason xato tavsifi
     * @return rejected result
     */
    public static TelegramDeliveryResult rejected(TelegramDeliveryCommand command,
                                                    String failureCode,
                                                    String failureReason) {
        if (command == null) {
            throw new IllegalArgumentException("command null bo'lishi mumkin emas");
        }
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode null yoki bo'sh bo'lishi mumkin emas");
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramDeliveryResult(
                DeliveryOutcome.REJECTED,
                command.getOperation(),
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                null,
                failureCode, failureReason);
    }

    /**
     * Texnik xato delivery natija yaratadi (FAILED).
     *
     * Tizim/network xatosi — masalan connection timeout,
     * gateway unavailable, yoki boshqa texnik muammo.
     *
     * @param command bajarilgan command
     * @param failureCode xato klassifikatsiyasi
     * @param failureReason xato tavsifi
     * @return failed result
     */
    public static TelegramDeliveryResult failed(TelegramDeliveryCommand command,
                                                  String failureCode,
                                                  String failureReason) {
        if (command == null) {
            throw new IllegalArgumentException("command null bo'lishi mumkin emas");
        }
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode null yoki bo'sh bo'lishi mumkin emas");
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason null yoki bo'sh bo'lishi mumkin emas");
        }
        return new TelegramDeliveryResult(
                DeliveryOutcome.FAILED,
                command.getOperation(),
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                null,
                failureCode, failureReason);
    }

    public DeliveryOutcome getDeliveryOutcome() { return deliveryOutcome; }
    public boolean isSuccess() { return deliveryOutcome == DeliveryOutcome.DELIVERED; }
    public TelegramDeliveryOperation getOperation() { return operation; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public Long getExternalMessageId() { return externalMessageId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
}
