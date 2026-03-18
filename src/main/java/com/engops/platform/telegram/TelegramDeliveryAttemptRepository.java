package com.engops.platform.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
