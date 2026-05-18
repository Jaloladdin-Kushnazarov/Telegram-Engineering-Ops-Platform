package com.engops.platform.telegram;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TelegramDeliveryAttemptEntity uchun repository.
 *
 * Append-only jadval — faqat read va insert operatsiyalar.
 * "Latest" aniqlash: attempted_at DESC, keyin id DESC (tie-breaker).
 */
@Repository
public interface TelegramDeliveryAttemptRepository extends JpaRepository<TelegramDeliveryAttemptEntity, UUID> {

    /**
     * Berilgan tenant va work item uchun eng so'nggi attempt'ni topadi.
     *
     * Spring Data derived query:
     * - attempted_at DESC bo'yicha eng yangi
     * - id DESC tie-breaker (bir xil attempted_at bo'lganda deterministic)
     *
     * Tenant isolation — tenantId har doim filter sifatida ishlatiladi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @return eng so'nggi attempt entity, yoki empty
     */
    Optional<TelegramDeliveryAttemptEntity> findFirstByTenantIdAndWorkItemIdOrderByAttemptedAtDescIdDesc(
            UUID tenantId, UUID workItemId);

    /**
     * Berilgan tenant va work item uchun so'nggi attempt'larni qaytaradi.
     *
     * Tartib: attempted_at DESC, id DESC — findFirst bilan bir xil deterministic ordering.
     * Pageable orqali natija soni cheklanadi (limit).
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param pageable limit uchun PageRequest
     * @return so'nggi attempt entity'lar ro'yxati, newest-first
     */
    List<TelegramDeliveryAttemptEntity> findByTenantIdAndWorkItemIdOrderByAttemptedAtDescIdDesc(
            UUID tenantId, UUID workItemId, Pageable pageable);

    /**
     * Phase 177 — kelajakdagi in-place card refresh uchun "current active
     * Telegram card" namzodini topadi.
     *
     * <p>Filter:</p>
     * <ul>
     *   <li>{@code tenant_id} va {@code work_item_id} — har doim tenant
     *       scoped (cross-tenant leak'ni oldini olish).</li>
     *   <li>{@code operation = SEND_NEW_MESSAGE} — eski tahrir
     *       ({@code EDIT_MESSAGE}) attemptlari hisobga olinmaydi; biz
     *       Telegram'ga yuborgan yangi xabarni izlaymiz.</li>
     *   <li>{@code delivery_outcome = DELIVERED} — faqat Telegram qabul
     *       qilgan attempt'lar nomzod, chunki shu rowlarda
     *       {@code external_message_id} to'ldirilgan bo'ladi.</li>
     * </ul>
     *
     * <p>Ordering: {@code attempted_at DESC, id DESC} — mavjud "latest"
     * pattern bilan sinxron (deterministic tie-breaker).</p>
     *
     * <p><strong>Phase 177 wiring:</strong> bu query hozircha hech qanday
     * production yo'lida chaqirilmaydi. {@link TelegramCardRefreshService}
     * orqali primitiv sifatida ishlatiladi; AFTER_COMMIT dispatch
     * pipeline'i Phase 178 da o'zgaradi.</p>
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @param operation kutilayotgan operatsiya (har doim {@code SEND_NEW_MESSAGE})
     * @param deliveryOutcome kutilayotgan natija (har doim {@code DELIVERED})
     * @return eng so'nggi mos attempt, yoki empty
     */
    Optional<TelegramDeliveryAttemptEntity>
            findFirstByTenantIdAndWorkItemIdAndOperationAndDeliveryOutcomeOrderByAttemptedAtDescIdDesc(
                    UUID tenantId,
                    UUID workItemId,
                    TelegramDeliveryOperation operation,
                    TelegramDeliveryResult.DeliveryOutcome deliveryOutcome);
}
