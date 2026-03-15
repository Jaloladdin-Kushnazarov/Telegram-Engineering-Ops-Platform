package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

/**
 * TelegramMessage'dan TelegramDeliveryCommand hosil qiluvchi assembler.
 *
 * Bu assembler rendered content model'ni outbound delivery command'ga
 * aylantirib beradi. Keyingi phase'dagi adapter shu command'ni
 * qabul qilib, Telegram Bot API call bajaradi.
 *
 * Hozirgi MVP'da faqat SEND_NEW_MESSAGE operatsiyasi qo'llab-quvvatlanadi.
 *
 * Fail-fast policy:
 * - SEND_NEW_MESSAGE uchun targetChatBindingId va targetTopicId majburiy
 * - Agar null bo'lsa, IllegalArgumentException — delivery-ready emas bo'lgan
 *   message'ni adapter'ga uzatmaslik kerak
 *
 * Muhim:
 * - Repository access yo'q — pure mapping
 * - Side effect yo'q
 * - Business rule yo'q
 * - Stateless — concurrent-safe
 */
@Component
public class TelegramDeliveryCommandAssembler {

    /**
     * TelegramMessage'dan SEND_NEW_MESSAGE delivery command hosil qiladi.
     *
     * @param message rendered telegram message
     * @return outbound delivery command
     * @throws IllegalArgumentException agar message null bo'lsa yoki
     *         delivery target ma'lumotlari to'liq bo'lmasa
     */
    public TelegramDeliveryCommand assembleSend(TelegramMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("TelegramMessage null bo'lishi mumkin emas");
        }
        if (message.getTargetChatBindingId() == null) {
            throw new IllegalArgumentException(
                    "SEND_NEW_MESSAGE uchun targetChatBindingId majburiy");
        }
        if (message.getTargetTopicId() == null) {
            throw new IllegalArgumentException(
                    "SEND_NEW_MESSAGE uchun targetTopicId majburiy");
        }

        return new TelegramDeliveryCommand(
                TelegramDeliveryOperation.SEND_NEW_MESSAGE,
                message.getTenantId(),
                message.getWorkItemId(),
                message.getTargetChatBindingId(),
                message.getTargetTopicId(),
                message.getText(),
                message.getKeyboard());
    }
}
