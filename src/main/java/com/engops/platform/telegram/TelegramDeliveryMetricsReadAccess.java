package com.engops.platform.telegram;

import java.util.Optional;
import java.util.UUID;

/**
 * Delivery metrics query uchun read-only data access port'i.
 *
 * Bu interface query service'ni persistence implementatsiyasidan ajratadi.
 * Hozirgi Phase 19 scope'da faqat eng so'nggi bitta attempt qaytaradi —
 * to'liq aggregatsiya yoki tarixiy ro'yxat bu phase'da yo'q.
 *
 * Hozir stub implementatsiya ishlatiladi (StubTelegramDeliveryMetricsReadAccess).
 * Keyingi phase'da real repository adapter shu interface'ni implement qiladi
 * va stub'ni almashtiradi.
 *
 * Muhim:
 * - Faqat read operatsiyalar
 * - Tenant-scoped va work-item-scoped lookup
 * - Ichki entity list'ni tashqariga chiqarmaydi — faqat bitta attempt qaytaradi
 * - Repository/JPA/SQL detail'lari bu interface'da ko'rinmaydi
 */
public interface TelegramDeliveryMetricsReadAccess {

    /**
     * Berilgan tenant va work item uchun eng so'nggi delivery attempt'ni topadi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     * @return eng so'nggi attempt, yoki empty agar hech qanday attempt topilmasa
     */
    Optional<TelegramDeliveryAttempt> findLatestAttempt(UUID tenantId, UUID workItemId);
}
