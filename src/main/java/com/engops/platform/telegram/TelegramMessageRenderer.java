package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TelegramCardView'dan transport-level TelegramMessage hosil qiluvchi renderer.
 *
 * Bu component pure rendering qiladi:
 * 1. cardView.renderPayload dan message text yig'adi
 * 2. cardView.actions dan inline keyboard hosil qiladi
 *
 * Muhim:
 * - Repository access yo'q — pure rendering
 * - Side effect yo'q
 * - Business rule yo'q
 * - Authorization yo'q — faqat view render
 * - Stateless — concurrent-safe
 * - Telegram Bot API ishlatilmaydi — faqat internal model
 */
@Component
public class TelegramMessageRenderer {

    /**
     * TelegramCardView'dan tayyor TelegramMessage hosil qiladi.
     *
     * @param cardView render payload + action'lar
     * @return transport-level message model
     * @throws IllegalArgumentException agar cardView null bo'lsa
     */
    public TelegramMessage render(TelegramCardView cardView) {
        if (cardView == null) {
            throw new IllegalArgumentException("TelegramCardView null bo'lishi mumkin emas");
        }

        TelegramRenderPayload renderPayload = cardView.getRenderPayload();

        String text = buildMessageText(renderPayload);
        List<TelegramInlineKeyboardRow> keyboard = buildKeyboard(cardView.getActions());

        return new TelegramMessage(
                renderPayload.getTenantId(),
                renderPayload.getWorkItemId(),
                text,
                keyboard,
                renderPayload.getTargetChatBindingId(),
                renderPayload.getTargetTopicId());
    }

    private String buildMessageText(TelegramRenderPayload renderPayload) {
        return renderPayload.getHeaderLine() + "\n"
                + renderPayload.getDisplayTitle() + "\n"
                + renderPayload.getStatusLine();
    }

    private List<TelegramInlineKeyboardRow> buildKeyboard(List<TelegramCardAction> actions) {
        return actions.stream()
                .map(this::toKeyboardRow)
                .toList();
    }

    private TelegramInlineKeyboardRow toKeyboardRow(TelegramCardAction action) {
        TelegramInlineKeyboardButton button = new TelegramInlineKeyboardButton(
                action.getLabel(),
                action.getCallbackData());
        return new TelegramInlineKeyboardRow(List.of(button));
    }
}
