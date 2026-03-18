package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Bitta delivery attempt'ning metrics-friendly flattened snapshot'i.
 *
 * Bu value object aggregatsiya va dashboard'lar uchun tayyor bo'lgan
 * dimension/flag ma'lumotlarni saqlaydi.
 *
 * Muhim farqlar:
 * - TelegramDeliveryAttempt — to'liq trace/audit record (attemptId, attemptedAt bilan)
 * - TelegramDeliveryMetricsSnapshot — faqat metrics dimension'lar va flag'lar
 *
 * Invariantlar:
 * - success == (deliveryOutcome == DELIVERED)
 * - rejected == (deliveryOutcome == REJECTED)
 * - failed == (deliveryOutcome == FAILED)
 * - DELIVERED uchun failureCode null bo'lishi mumkin
 * - REJECTED va FAILED uchun failureCode majburiy (non-null, non-blank)
 * - empty snapshot — hech qanday attempt topilmaganda qaytariladi
 *
 * Immutable, factory method orqali yaratiladi.
 */
public class TelegramDeliveryMetricsSnapshot {

    private final UUID tenantId;
    private final UUID workItemId;
    private final TelegramDeliveryOperation operation;
    private final TelegramDeliveryResult.DeliveryOutcome deliveryOutcome;
    private final boolean success;
    private final boolean rejected;
    private final boolean failed;
    private final String failureCode;
    private final boolean hasExternalMessageId;
    private final boolean empty;

    private TelegramDeliveryMetricsSnapshot(UUID tenantId,
                                             UUID workItemId,
                                             TelegramDeliveryOperation operation,
                                             TelegramDeliveryResult.DeliveryOutcome deliveryOutcome,
                                             boolean success,
                                             boolean rejected,
                                             boolean failed,
                                             String failureCode,
                                             boolean hasExternalMessageId,
                                             boolean empty) {
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.operation = operation;
        this.deliveryOutcome = deliveryOutcome;
        this.success = success;
        this.rejected = rejected;
        this.failed = failed;
        this.failureCode = failureCode;
        this.hasExternalMessageId = hasExternalMessageId;
        this.empty = empty;
    }

    /**
     * Flattened metrics field'lardan immutable snapshot yaratadi.
     *
     * Bu factory method tayyor dimension/flag qiymatlarni qabul qiladi.
     * TelegramDeliveryAttempt'dan mapping TelegramDeliveryMetricsAssembler
     * tomonidan amalga oshiriladi — bu method attempt'ni bevosita qabul qilmaydi.
     *
     * Barcha invariantlarni fail-fast tekshiradi:
     * - tenantId, workItemId, operation, deliveryOutcome null bo'lmasligi kerak
     * - DELIVERED uchun failureCode null bo'lishi kerak
     * - REJECTED/FAILED uchun failureCode non-null va non-blank bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param operation delivery operatsiya turi
     * @param deliveryOutcome delivery natijasi
     * @param failureCode xato klassifikatsiyasi (DELIVERED uchun null bo'lishi kerak)
     * @param hasExternalMessageId Telegram external message ID mavjudligi
     * @return immutable metrics snapshot
     * @throws IllegalArgumentException agar invariantlar buzilsa
     */
    public static TelegramDeliveryMetricsSnapshot of(UUID tenantId,
                                                       UUID workItemId,
                                                       TelegramDeliveryOperation operation,
                                                       TelegramDeliveryResult.DeliveryOutcome deliveryOutcome,
                                                       String failureCode,
                                                       boolean hasExternalMessageId) {
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

        boolean isSuccess = deliveryOutcome == TelegramDeliveryResult.DeliveryOutcome.DELIVERED;
        boolean isRejected = deliveryOutcome == TelegramDeliveryResult.DeliveryOutcome.REJECTED;
        boolean isFailed = deliveryOutcome == TelegramDeliveryResult.DeliveryOutcome.FAILED;

        if (isSuccess && failureCode != null) {
            throw new IllegalArgumentException(
                    "DELIVERED holatda failureCode null bo'lishi kerak, lekin: " + failureCode);
        }
        if (isRejected && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException(
                    "REJECTED holatda failureCode majburiy (non-null, non-blank)");
        }
        if (isFailed && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException(
                    "FAILED holatda failureCode majburiy (non-null, non-blank)");
        }

        return new TelegramDeliveryMetricsSnapshot(
                tenantId, workItemId, operation, deliveryOutcome,
                isSuccess, isRejected, isFailed,
                failureCode, hasExternalMessageId, false);
    }

    /**
     * Hech qanday delivery attempt topilmaganda qaytariladigan bo'sh snapshot.
     *
     * Empty snapshot faqat tenant va work item kontekstini saqlaydi.
     * Operation, outcome va boshqa dimension'lar null/false bo'ladi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @return bo'sh metrics snapshot
     * @throws IllegalArgumentException agar tenantId yoki workItemId null bo'lsa
     */
    public static TelegramDeliveryMetricsSnapshot empty(UUID tenantId, UUID workItemId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (workItemId == null) {
            throw new IllegalArgumentException("workItemId null bo'lishi mumkin emas");
        }
        return new TelegramDeliveryMetricsSnapshot(
                tenantId, workItemId, null, null,
                false, false, false,
                null, false, true);
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public TelegramDeliveryOperation getOperation() { return operation; }
    public TelegramDeliveryResult.DeliveryOutcome getDeliveryOutcome() { return deliveryOutcome; }
    public boolean isSuccess() { return success; }
    public boolean isRejected() { return rejected; }
    public boolean isFailed() { return failed; }
    public String getFailureCode() { return failureCode; }
    public boolean hasExternalMessageId() { return hasExternalMessageId; }
    public boolean isEmpty() { return empty; }
}
