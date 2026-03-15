package com.engops.platform.telegram;

import org.springframework.stereotype.Service;

/**
 * Telegram outbound dispatch uchun application service.
 *
 * Bu servis telegram module'ning outbound execution uchun public entry point'i.
 * TelegramDeliveryCommand'ni qabul qilib, TelegramOutboundGateway orqali
 * tashqi tizimga yuboradi.
 *
 * Thin delegation:
 * - Null guard
 * - Gateway'ga delegate
 * - Natijani qaytarish
 *
 * Muhim:
 * - Business rule yo'q — faqat delegation
 * - Rendering yo'q — command allaqachon tayyor
 * - HTTP yo'q — gateway abstraktsiya orqali
 * - Retry yo'q — keyingi phase
 * - Repository access yo'q
 * - Stateless — concurrent-safe
 */
@Service
public class TelegramOutboundDispatchService {

    private final TelegramOutboundGateway gateway;

    public TelegramOutboundDispatchService(TelegramOutboundGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * TelegramDeliveryCommand'ni outbound gateway orqali dispatch qiladi.
     *
     * @param command outbound delivery command
     * @return execution natijasi
     * @throws IllegalArgumentException agar command null bo'lsa
     */
    public TelegramDeliveryResult dispatch(TelegramDeliveryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TelegramDeliveryCommand null bo'lishi mumkin emas");
        }

        return gateway.dispatch(command);
    }
}
