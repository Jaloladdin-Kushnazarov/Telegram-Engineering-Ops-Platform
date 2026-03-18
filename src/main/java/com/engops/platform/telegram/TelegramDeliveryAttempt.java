package com.engops.platform.telegram;

import java.time.Instant;
import java.util.UUID;

/**
 * Bitta outbound delivery urinishining to'liq trace record'i.
 *
 * Bu DTO production observability, audit va debugging uchun
 * minimal lekin yetarli traceability contract'ni taqdim etadi.
 *
 * Har bir dispatch attempt uchun:
 * - attemptId: noyob identifikator (korrelyatsiya uchun)
 * - attemptedAt: qachon urinildi
 * - tenant/workItem/target konteksti (command'dan)
 * - delivery outcome (result'dan)
 * - externalMessageId (faqat DELIVERED holatda)
 * - failureCode/failureReason (faqat REJECTED/FAILED holatda)
 *
 * Architectural notes:
 * - TelegramCardDispatchService tomonidan yaratiladi — facade entry point
 * - Command + Result dan flat snapshot hosil qilinadi
 * - Transport-level gateway internals expose qilinmaydi
 * - Immutable — factory method orqali yaratiladi
 * - Clock bean kerak emas — factory Instant parametr qabul qiladi,
 *   caller (service) Instant.now() beradi, testlar deterministic Instant beradi.
 *   Persistence/audit qo'shilganda Clock o'sha qatlamda inject qilinadi.
 *
 * Factory method'lar:
 * - of(command, result, attemptedAt): yangi attempt yaratish (dispatch paytida)
 * - reconstruct(...): persistence'dan o'qilgan attempt'ni qayta tiklash
 */
public class TelegramDeliveryAttempt {

    private final UUID attemptId;
    private final Instant attemptedAt;
    private final UUID tenantId;
    private final UUID workItemId;
    private final TelegramDeliveryOperation operation;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;
    private final TelegramDeliveryResult.DeliveryOutcome deliveryOutcome;
    private final Long externalMessageId;
    private final String failureCode;
    private final String failureReason;

    private TelegramDeliveryAttempt(UUID attemptId,
                                     Instant attemptedAt,
                                     UUID tenantId,
                                     UUID workItemId,
                                     TelegramDeliveryOperation operation,
                                     UUID targetChatBindingId,
                                     Long targetTopicId,
                                     TelegramDeliveryResult.DeliveryOutcome deliveryOutcome,
                                     Long externalMessageId,
                                     String failureCode,
                                     String failureReason) {
        this.attemptId = attemptId;
        this.attemptedAt = attemptedAt;
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.operation = operation;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
        this.deliveryOutcome = deliveryOutcome;
        this.externalMessageId = externalMessageId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    /**
     * Command va result'dan flat trace record hosil qiladi.
     *
     * Command va result'dagi kontekst field'lari mos kelishi tekshiriladi —
     * mismatch buzilgan pipeline'ni bildiradi va fail-fast qilinadi.
     *
     * @param command bajarilgan delivery command
     * @param result delivery natijasi
     * @param attemptedAt urinish vaqti
     * @return immutable delivery attempt trace record
     * @throws IllegalArgumentException agar command, result yoki attemptedAt null bo'lsa,
     *         yoki command/result kontekst field'lari mos kelmasa
     */
    public static TelegramDeliveryAttempt of(TelegramDeliveryCommand command,
                                              TelegramDeliveryResult result,
                                              Instant attemptedAt) {
        if (command == null) {
            throw new IllegalArgumentException("command null bo'lishi mumkin emas");
        }
        if (result == null) {
            throw new IllegalArgumentException("result null bo'lishi mumkin emas");
        }
        if (attemptedAt == null) {
            throw new IllegalArgumentException("attemptedAt null bo'lishi mumkin emas");
        }

        verifyConsistency(command, result);

        return new TelegramDeliveryAttempt(
                UUID.randomUUID(),
                attemptedAt,
                command.getTenantId(),
                command.getWorkItemId(),
                command.getOperation(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                result.getDeliveryOutcome(),
                result.getExternalMessageId(),
                result.getFailureCode(),
                result.getFailureReason());
    }

    private static void verifyConsistency(TelegramDeliveryCommand command,
                                           TelegramDeliveryResult result) {
        if (!command.getTenantId().equals(result.getTenantId())) {
            throw new IllegalArgumentException(
                    "command/result tenantId mos kelmaydi: " + command.getTenantId()
                            + " vs " + result.getTenantId());
        }
        if (!command.getWorkItemId().equals(result.getWorkItemId())) {
            throw new IllegalArgumentException(
                    "command/result workItemId mos kelmaydi: " + command.getWorkItemId()
                            + " vs " + result.getWorkItemId());
        }
        if (command.getOperation() != result.getOperation()) {
            throw new IllegalArgumentException(
                    "command/result operation mos kelmaydi: " + command.getOperation()
                            + " vs " + result.getOperation());
        }
        if (!command.getTargetChatBindingId().equals(result.getTargetChatBindingId())) {
            throw new IllegalArgumentException(
                    "command/result targetChatBindingId mos kelmaydi: "
                            + command.getTargetChatBindingId()
                            + " vs " + result.getTargetChatBindingId());
        }
        if (!command.getTargetTopicId().equals(result.getTargetTopicId())) {
            throw new IllegalArgumentException(
                    "command/result targetTopicId mos kelmaydi: " + command.getTargetTopicId()
                            + " vs " + result.getTargetTopicId());
        }
    }

    public UUID getAttemptId() { return attemptId; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public TelegramDeliveryOperation getOperation() { return operation; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public TelegramDeliveryResult.DeliveryOutcome getDeliveryOutcome() { return deliveryOutcome; }
    public Long getExternalMessageId() { return externalMessageId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }

    public boolean isSuccess() { return deliveryOutcome == TelegramDeliveryResult.DeliveryOutcome.DELIVERED; }

    /**
     * Persistence'dan o'qilgan attempt'ni qayta tiklaydi.
     *
     * Bu method faqat JPA adapter tomonidan ishlatiladi —
     * yangi attempt yaratish uchun of(command, result, attemptedAt) ishlatiladi.
     * Consistency tekshiruvi yo'q chunki ma'lumot allaqachon validatsiyadan o'tgan
     * va persistence'da saqlangan.
     *
     * @return immutable delivery attempt
     * @throws IllegalArgumentException agar majburiy field'lar null bo'lsa
     */
    public static TelegramDeliveryAttempt reconstruct(UUID attemptId,
                                                        Instant attemptedAt,
                                                        UUID tenantId,
                                                        UUID workItemId,
                                                        TelegramDeliveryOperation operation,
                                                        UUID targetChatBindingId,
                                                        Long targetTopicId,
                                                        TelegramDeliveryResult.DeliveryOutcome deliveryOutcome,
                                                        Long externalMessageId,
                                                        String failureCode,
                                                        String failureReason) {
        if (attemptId == null) {
            throw new IllegalArgumentException("attemptId null bo'lishi mumkin emas");
        }
        if (attemptedAt == null) {
            throw new IllegalArgumentException("attemptedAt null bo'lishi mumkin emas");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation null bo'lishi mumkin emas");
        }
        if (deliveryOutcome == null) {
            throw new IllegalArgumentException("deliveryOutcome null bo'lishi mumkin emas");
        }

        return new TelegramDeliveryAttempt(
                attemptId, attemptedAt, tenantId, workItemId,
                operation, targetChatBindingId, targetTopicId,
                deliveryOutcome, externalMessageId, failureCode, failureReason);
    }
}
