package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * TelegramDeliveryMetricsReadAccess'ning real JPA-backed implementatsiyasi.
 *
 * Bu adapter TelegramDeliveryAttemptRepository'dan eng so'nggi attempt'ni
 * o'qiydi va TelegramDeliveryAttempt DTO'ga aylantiradi.
 *
 * Muhim:
 * - Tenant-scoped, work-item-scoped lookup
 * - "Latest" = attempted_at DESC, id DESC (deterministic tie-breaker)
 * - Entity → DTO konvertatsiya shu adapter ichida
 * - Metrics hisoblash yo'q — faqat data retrieval
 */
@Component
public class JpaTelegramDeliveryMetricsReadAccess implements TelegramDeliveryMetricsReadAccess {

    private final TelegramDeliveryAttemptRepository repository;

    public JpaTelegramDeliveryMetricsReadAccess(TelegramDeliveryAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TelegramDeliveryAttempt> findLatestAttempt(UUID tenantId, UUID workItemId) {
        return repository.findFirstByTenantIdAndWorkItemIdOrderByAttemptedAtDescIdDesc(tenantId, workItemId)
                .map(this::toAttempt);
    }

    private TelegramDeliveryAttempt toAttempt(TelegramDeliveryAttemptEntity entity) {
        return TelegramDeliveryAttempt.reconstruct(
                entity.getId(),
                entity.getAttemptedAt(),
                entity.getTenantId(),
                entity.getWorkItemId(),
                entity.getOperation(),
                entity.getTargetChatBindingId(),
                entity.getTargetTopicId(),
                entity.getDeliveryOutcome(),
                entity.getExternalMessageId(),
                entity.getFailureCode(),
                entity.getFailureReason());
    }
}
