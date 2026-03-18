package com.engops.platform.telegram;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * TelegramDeliveryAttemptHistoryReadAccess'ning real JPA-backed implementatsiyasi.
 *
 * Bu adapter TelegramDeliveryAttemptRepository'dan so'nggi attempt'larni
 * o'qiydi va TelegramDeliveryAttempt DTO ro'yxatiga aylantiradi.
 *
 * JpaTelegramDeliveryMetricsReadAccess bilan bir xil entity → DTO mapping
 * ishlatadi (TelegramDeliveryAttempt.reconstruct orqali).
 *
 * Muhim:
 * - Tenant-scoped, work-item-scoped lookup
 * - Ordering: attempted_at DESC, id DESC (findFirst bilan bir xil)
 * - Limit: PageRequest orqali repository'ga uzatiladi
 * - Entity → DTO konvertatsiya shu adapter ichida
 */
@Component
public class JpaTelegramDeliveryAttemptHistoryReadAccess implements TelegramDeliveryAttemptHistoryReadAccess {

    private final TelegramDeliveryAttemptRepository repository;

    public JpaTelegramDeliveryAttemptHistoryReadAccess(TelegramDeliveryAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TelegramDeliveryAttempt> findRecentAttempts(UUID tenantId, UUID workItemId, int limit) {
        return repository.findByTenantIdAndWorkItemIdOrderByAttemptedAtDescIdDesc(
                        tenantId, workItemId, PageRequest.of(0, limit))
                .stream()
                .map(this::toAttempt)
                .toList();
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
