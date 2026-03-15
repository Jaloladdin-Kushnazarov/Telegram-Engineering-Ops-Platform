package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

/**
 * Vaqtinchalik stub gateway — real Telegram Bot API integration yo'q.
 *
 * Bu bean TelegramOutboundGateway port'ining placeholder implementation'i.
 * Keyingi phase'da real Telegram Bot API client shu bean'ni almashtiradi.
 *
 * Hozirgi holat:
 * - Har bir dispatch chaqiruvida controlled failure qaytaradi
 * - App startup va autowiring muammosini bartaraf etadi
 * - TelegramDeliveryResult contract'ni to'g'ri ishlatadi
 * - Exception tashlamaydi — structured failure qaytaradi
 */
@Component
public class StubTelegramOutboundGateway implements TelegramOutboundGateway {

    static final String FAILURE_CODE = "TELEGRAM_GATEWAY_NOT_IMPLEMENTED";
    static final String FAILURE_REASON = "Telegram outbound gateway hali implement qilinmagan";

    @Override
    public TelegramDeliveryResult dispatch(TelegramDeliveryCommand command) {
        return TelegramDeliveryResult.failure(command, FAILURE_CODE, FAILURE_REASON);
    }
}
