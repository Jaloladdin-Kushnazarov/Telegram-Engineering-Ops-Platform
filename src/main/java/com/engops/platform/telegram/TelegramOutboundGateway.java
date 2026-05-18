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

    /**
     * Transport-level request'ni tashqi tizimga yuboradi.
     *
     * Bu method Phase 11 dan kelgan TelegramSendMessageRequest'ni
     * qabul qilib, transport-level natija qaytaradi.
     *
     * @param request transport-oriented send message request
     * @return gateway execution natijasi
     */
    TelegramGatewayResult execute(TelegramSendMessageRequest request);

    /**
     * Phase 175 — Telegram {@code answerCallbackQuery} chaqiruvi.
     *
     * <p>Inline button bosilganda operatorga vaqtinchalik toast
     * ko'rinishida feedback qaytarish uchun ishlatiladi. Bu metod
     * Telegram'dagi chat yoki xabar holatini o'zgartirmaydi —
     * faqat ephemeral acknowledgement. Phase 175 da retry yo'q.</p>
     *
     * <p><strong>Failure semantics:</strong> implementor hech qachon
     * exception tashlamaydi va har bir holat
     * {@link TelegramAcknowledgeCallbackResult} bilan qaytariladi.
     * Stub gateway fail-closed strukturali failure qaytaradi.</p>
     *
     * @param request acknowledgement request (callback_query id + bounded text)
     * @return transport-level acknowledgement result
     */
    TelegramAcknowledgeCallbackResult acknowledgeCallback(TelegramAcknowledgeCallbackRequest request);

    /**
     * Phase 177 — Telegram {@code editMessageText} chaqiruvi.
     *
     * <p>Mavjud xabar matnini (va ixtiyoriy inline keyboard'ini) joyida
     * tahrirlash uchun ishlatiladi. Hozircha bu primitiv production
     * dispatch yo'lida wired emas — kelajakdagi card-refresh pipeline
     * uchun foundation sifatida qo'shildi.</p>
     *
     * <p><strong>Failure semantics:</strong> implementor hech qachon
     * exception tashlamaydi; har bir holat
     * {@link TelegramEditMessageTextResult} bilan qaytariladi.
     * Stub gateway fail-closed strukturali failure qaytaradi.</p>
     *
     * @param request edit request (chat_id + message_id + text + ixtiyoriy keyboard)
     * @return transport-level edit result
     */
    TelegramEditMessageTextResult editMessageText(TelegramEditMessageTextRequest request);
}
