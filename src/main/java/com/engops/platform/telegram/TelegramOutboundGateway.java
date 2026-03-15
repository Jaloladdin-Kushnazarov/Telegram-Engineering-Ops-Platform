package com.engops.platform.telegram;

/**
 * Telegram outbound integration port.
 *
 * Bu interface telegram module'ning tashqi dunyo bilan aloqa chegarasi.
 * Keyingi phase'da haqiqiy Telegram Bot API client shu interface'ni
 * implement qiladi.
 *
 * Hozir faqat contract — implementation yo'q.
 *
 * Muhim:
 * - Bu port telegram module ichida qoladi
 * - Infrastructure adapter shu port'ni implement qiladi
 * - Application service (TelegramOutboundDispatchService) shu port orqali ishlaydi
 */
public interface TelegramOutboundGateway {

    /**
     * TelegramDeliveryCommand'ni tashqi tizimga yuboradi.
     *
     * @param command outbound delivery command
     * @return execution natijasi (success yoki failure)
     */
    TelegramDeliveryResult dispatch(TelegramDeliveryCommand command);
}
