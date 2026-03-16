package com.engops.platform.telegram;

/**
 * Telegram outbound delivery operatsiya turi.
 *
 * Adapter shu operatsiya asosida qanday Telegram API call
 * bajarilishini aniqlaydi.
 *
 * Hozirgi MVP'da faqat bitta operatsiya:
 * - SEND_NEW_MESSAGE: yangi message yuborish
 *
 * Keyingi phase'larda qo'shilishi mumkin:
 * - EDIT_MESSAGE: mavjud message'ni yangilash
 * - DELETE_MESSAGE: message'ni o'chirish
 *
 * Lekin hozir faqat MVP scope.
 */
public enum TelegramDeliveryOperation {

    SEND_NEW_MESSAGE,

    /**
     * Mavjud message'ni yangilash — hali implement qilinmagan.
     * Keyingi phase'larda qo'llab-quvvatlanadi.
     */
    EDIT_MESSAGE
}
