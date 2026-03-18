package com.engops.platform.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Telegram delivery attempt uchun JPA entity — append-only.
 *
 * Har bir outbound dispatch attempt shu jadvalga yoziladi.
 * Yaratilgandan keyin o'zgartirilmaydi (immutable row).
 *
 * Bu entity faqat persistence qatlami uchun — domain DTO
 * TelegramDeliveryAttempt sifatida qaytariladi.
 *
 * "Latest" aniqlash: attempted_at DESC, id DESC (deterministic tie-breaker).
 *
 * target_topic_id NOT NULL — hozirgi MVP'da SEND_NEW_MESSAGE uchun
 * targetTopicId har doim majburiy (TelegramDeliveryCommand'da fail-fast).
 */
@Entity
@Table(name = "telegram_delivery_attempt")
public class TelegramDeliveryAttemptEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private TelegramDeliveryOperation operation;

    @NotNull
    @Column(name = "target_chat_binding_id", nullable = false)
    private UUID targetChatBindingId;

    @NotNull
    @Column(name = "target_topic_id", nullable = false)
    private Long targetTopicId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_outcome", nullable = false)
    private TelegramDeliveryResult.DeliveryOutcome deliveryOutcome;

    @Column(name = "external_message_id")
    private Long externalMessageId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @NotNull
    @Column(name = "attempted_at", updatable = false, nullable = false)
    private Instant attemptedAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected TelegramDeliveryAttemptEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public TelegramDeliveryAttemptEntity(UUID attemptId,
                                          UUID tenantId,
                                          UUID workItemId,
                                          TelegramDeliveryOperation operation,
                                          UUID targetChatBindingId,
                                          Long targetTopicId,
                                          TelegramDeliveryResult.DeliveryOutcome deliveryOutcome,
                                          Long externalMessageId,
                                          String failureCode,
                                          String failureReason,
                                          Instant attemptedAt) {
        this();
        this.id = attemptId;
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.operation = operation;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
        this.deliveryOutcome = deliveryOutcome;
        this.externalMessageId = externalMessageId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public TelegramDeliveryOperation getOperation() { return operation; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
    public TelegramDeliveryResult.DeliveryOutcome getDeliveryOutcome() { return deliveryOutcome; }
    public Long getExternalMessageId() { return externalMessageId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelegramDeliveryAttemptEntity that = (TelegramDeliveryAttemptEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
