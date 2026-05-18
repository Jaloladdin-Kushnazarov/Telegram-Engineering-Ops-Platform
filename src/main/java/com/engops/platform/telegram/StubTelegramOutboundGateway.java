package com.engops.platform.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Stub gateway — real Telegram Bot API integration mavjud bo'lmaganda
 * (test/dev sharoit yoki production'da token konfiguratsiya qilinmagan)
 * fallback sifatida ishlaydi.
 *
 * Phase 158 dan boshlab {@code @ConditionalOnExpression} orqali
 * {@code app.telegram.bot-token} bo'sh/blank bo'lganda yagona
 * {@link TelegramOutboundGateway} bean'i sifatida yuklanadi.
 * Token non-blank bo'lganda {@link TelegramOutboundGatewayConfiguration}
 * {@link HttpTelegramOutboundGateway}'ni primary bean qilib qaytaradi
 * va stub yuklanmaydi (mutually-exclusive conditional).
 *
 * Stub xulqi:
 * - Har bir dispatch chaqiruvida controlled failure qaytaradi
 * - App startup va autowiring muammosini bartaraf etadi
 * - TelegramDeliveryResult contract'ni to'g'ri ishlatadi
 * - Exception tashlamaydi — structured failure qaytaradi
 */
@Component
@ConditionalOnExpression("'${app.telegram.bot-token:}'.trim().length() == 0")
public class StubTelegramOutboundGateway implements TelegramOutboundGateway {

    static final String FAILURE_CODE = "TELEGRAM_GATEWAY_NOT_IMPLEMENTED";
    static final String FAILURE_REASON = "Telegram outbound gateway hali implement qilinmagan";

    @Override
    public TelegramDeliveryResult dispatch(TelegramDeliveryCommand command) {
        return TelegramDeliveryResult.failed(command, FAILURE_CODE, FAILURE_REASON);
    }

    @Override
    public TelegramGatewayResult execute(TelegramSendMessageRequest request) {
        return TelegramGatewayResult.failed(
                TelegramGatewayError.UNKNOWN_ERROR, FAILURE_REASON);
    }

    @Override
    public TelegramAcknowledgeCallbackResult acknowledgeCallback(
            TelegramAcknowledgeCallbackRequest request) {
        return TelegramAcknowledgeCallbackResult.failed(
                TelegramGatewayError.UNKNOWN_ERROR, FAILURE_REASON);
    }

    @Override
    public TelegramEditMessageTextResult editMessageText(TelegramEditMessageTextRequest request) {
        return TelegramEditMessageTextResult.failed(
                TelegramGatewayError.UNKNOWN_ERROR, FAILURE_REASON);
    }
}
