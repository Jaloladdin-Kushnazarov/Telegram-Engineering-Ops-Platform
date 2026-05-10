package com.engops.platform.telegram;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * <p><strong>Phase 168 — REQUIRES_NEW transaction boundary:</strong>
 * {@link #save} metodi {@code @Transactional(propagation = REQUIRES_NEW)}
 * bilan annotated. Phase 164 mini-fix da bu annotatsiya
 * {@code TelegramCardDispatchEventListener} metodida turgan edi va listener
 * butun render → HTTP → persistence zanjirini bitta transaction'da o'rab
 * olar edi — retry kiritilgach bu Hikari connection occupancy uchun mos
 * emas (HTTP latency × maxAttempts + backoff sleeps DB connection ushlab
 * turardi). Phase 168 da boundary persistence qatlamiga tushirildi: HTTP
 * chaqiruvi va backoff sleep'lar transaction'siz bajariladi, faqat
 * {@code telegram_delivery_attempt} insert'i alohida qisqa transaction'da
 * commit qilinadi. Phase 164 mini-fix invariantı saqlanadi —
 * delivery_attempt persistence originating business transaction'idan
 * decouple bo'ladi.</p>
 */
@Component
public class JpaTelegramDeliveryAttemptPersistence implements TelegramDeliveryAttemptPersistence {

    private final TelegramDeliveryAttemptRepository repository;

    public JpaTelegramDeliveryAttemptPersistence(TelegramDeliveryAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
