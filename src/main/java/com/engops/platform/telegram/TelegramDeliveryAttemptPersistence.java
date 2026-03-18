package com.engops.platform.telegram;

/**
 * Delivery attempt yozish uchun write-side port.
 *
 * Bu interface dispatch pipeline'ni persistence implementatsiyasidan ajratadi.
 * Append-only semantika — faqat yangi attempt qo'shiladi, mavjudlar o'zgartirilmaydi.
 *
 * Read-side (TelegramDeliveryMetricsReadAccess) bilan aralashtirilmasligi kerak —
 * write va read portlari alohida.
 */
public interface TelegramDeliveryAttemptPersistence {

    /**
     * Delivery attempt'ni bazaga saqlaydi.
     *
     * @param attempt saqlanadigan attempt
     * @throws IllegalArgumentException agar attempt null bo'lsa
     */
    void save(TelegramDeliveryAttempt attempt);
}
