package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

/**
 * TelegramDeliveryCommand'dan TelegramSendMessageRequest hosil qiluvchi assembler.
 *
 * Application-level command'ni transport-level request model'ga
 * aylantirib beradi. Keyingi phase'dagi real gateway shu request
 * model'ni Telegram Bot API sendMessage call'iga tarjima qiladi.
 *
 * Faqat SEND_NEW_MESSAGE operatsiyasini qo'llab-quvvatlaydi.
 * Boshqa operatsiya uchun fail-fast.
 *
 * Muhim:
 * - Pure mapping — repository/HTTP/business rule yo'q
 * - Stateless — concurrent-safe
 */
@Component
public class TelegramSendMessageRequestAssembler {

    /**
     * TelegramDeliveryCommand'dan TelegramSendMessageRequest hosil qiladi.
     *
     * @param command outbound delivery command (faqat SEND_NEW_MESSAGE)
     * @return transport-oriented sendMessage request
     * @throws IllegalArgumentException agar command null yoki operatsiya SEND_NEW_MESSAGE emas
     */
    public TelegramSendMessageRequest assemble(TelegramDeliveryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("TelegramDeliveryCommand null bo'lishi mumkin emas");
        }
        if (command.getOperation() != TelegramDeliveryOperation.SEND_NEW_MESSAGE) {
            throw new IllegalArgumentException(
                    "Faqat SEND_NEW_MESSAGE operatsiyasi qo'llab-quvvatlanadi, berilgan: "
                            + command.getOperation());
        }

        return new TelegramSendMessageRequest(
                command.getTenantId(),
                command.getWorkItemId(),
                command.getTargetChatBindingId(),
                command.getTargetTopicId(),
                command.getText(),
                command.getKeyboard());
    }
}
