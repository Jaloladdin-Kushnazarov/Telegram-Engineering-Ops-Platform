package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

/**
 * TelegramDeliveryAttemptPersistence'ning JPA-backed implementatsiyasi.
 *
 * TelegramDeliveryAttempt DTO'ni TelegramDeliveryAttemptEntity'ga
 * explicit field mapping bilan aylantiradi va bazaga saqlaydi.
 *
 * Muhim:
 * - Append-only — faqat insert, update yo'q
 * - DTO → Entity mapping shu adapter ichida
 * - Repository faqat save() uchun ishlatiladi
 */
@Component
public class JpaTelegramDeliveryAttemptPersistence implements TelegramDeliveryAttemptPersistence {

    private final TelegramDeliveryAttemptRepository repository;

    public JpaTelegramDeliveryAttemptPersistence(TelegramDeliveryAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(TelegramDeliveryAttempt attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt null bo'lishi mumkin emas");
        }

        TelegramDeliveryAttemptEntity entity = new TelegramDeliveryAttemptEntity(
                attempt.getAttemptId(),
                attempt.getTenantId(),
                attempt.getWorkItemId(),
                attempt.getOperation(),
                attempt.getTargetChatBindingId(),
                attempt.getTargetTopicId(),
                attempt.getDeliveryOutcome(),
                attempt.getExternalMessageId(),
                attempt.getFailureCode(),
                attempt.getFailureReason(),
                attempt.getAttemptedAt());

        repository.save(entity);
    }
}
