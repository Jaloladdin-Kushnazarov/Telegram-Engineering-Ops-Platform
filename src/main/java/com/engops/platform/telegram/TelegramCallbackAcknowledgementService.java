package com.engops.platform.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Phase 175 — Telegram inline button acknowledgement uchun fail-soft
 * wrapper service.
 *
 * <p>Mas'uliyat: {@link TelegramOutboundGateway#acknowledgeCallback(TelegramAcknowledgeCallbackRequest)}
 * ni o'rab oladi va quyidagi kontraktni kafolatlaydi:</p>
 * <ul>
 *   <li>{@code callbackQueryId} null yoki blank bo'lsa, gateway umuman
 *       chaqirilmaydi (defensive short-circuit).</li>
 *   <li>Gateway strukturali failure qaytarsa, exception tashlamaydi —
 *       faqat bounded log yoziladi.</li>
 *   <li>Gateway kutilmagan {@link RuntimeException} tashlasa (gateway
 *       contract'ini buzgan holatda), exception ushlanadi va swallow
 *       qilinadi — caller ko'rmaydi.</li>
 *   <li>Retry yo'q (Phase 175 tashqarisida). Acknowledgement deadline
 *       Telegram tomonidan cheklangan; retry mantiqsiz.</li>
 *   <li>Persistence yo'q — acknowledgement attempt
 *       {@code telegram_delivery_attempt} jadvaliga yozilmaydi.</li>
 * </ul>
 *
 * <p><strong>Transaction boundary:</strong> bu service {@code @Transactional}
 * EMAS. Caller (orchestrator) ham non-transactional. Acknowledgement HTTP
 * chaqiruvi hech qachon ochiq DB transaction ichida sodir bo'lmaydi.</p>
 *
 * <p><strong>Logging hygiene:</strong> bounded INFO/WARN faqat.
 * Yozilmaydi: bot token, URL with token, inbound webhook secret, exception
 * message, {@code text} qiymati (qisqa va bounded bo'lsa ham), full payload,
 * stack trace.</p>
 */
@Service
public class TelegramCallbackAcknowledgementService {

    private static final Logger log =
            LoggerFactory.getLogger(TelegramCallbackAcknowledgementService.class);

    private final TelegramOutboundGateway gateway;

    public TelegramCallbackAcknowledgementService(TelegramOutboundGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Acknowledgement'ni best-effort sifatida yuboradi. Hech qanday holatda
     * caller'ga exception propagate qilmaydi.
     *
     * <p>callbackQueryId null/blank bo'lsa skip qilinadi va
     * {@link TelegramAcknowledgeCallbackResult.ResultType#REJECTED}
     * qaytariladi — caller kerak bo'lsa o'qib chiqishi mumkin, lekin
     * fail-soft kontrakti uchun majburiy emas.</p>
     *
     * <p>Request constructor textni 200 simvol cheklovi bilan validate
     * qiladi; caller bu cheklovni hurmat qilishi shart. Bounded matn
     * orchestrator tomonidan static mapping orqali ta'minlanadi.</p>
     *
     * @param callbackQueryId Telegram'dan kelgan callback_query.id
     * @param text bounded acknowledgement matni (<= 200 simvol)
     * @return acknowledgement natijasi (skip uchun REJECTED qaytariladi)
     */
    public TelegramAcknowledgeCallbackResult acknowledge(String callbackQueryId, String text) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            log.info("Telegram acknowledge skip resultType=SKIPPED reason=blank-callbackQueryId");
            return TelegramAcknowledgeCallbackResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "callbackQueryId null or blank");
        }
        if (text == null || text.isBlank()) {
            log.info("Telegram acknowledge skip resultType=SKIPPED reason=blank-text");
            return TelegramAcknowledgeCallbackResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "text null or blank");
        }

        TelegramAcknowledgeCallbackRequest request;
        try {
            request = new TelegramAcknowledgeCallbackRequest(callbackQueryId, text);
        } catch (IllegalArgumentException ex) {
            // text 200 simvoldan oshgan yoki boshqa validation buzilgan —
            // caller orchestrator tomonidan oldindan bounded matn taqdim
            // etiladi, lekin defensiv ravishda fail-soft.
            log.warn("Telegram acknowledge skip resultType=SKIPPED reason=invalid-request exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramAcknowledgeCallbackResult.rejected(
                    TelegramGatewayError.INVALID_REQUEST,
                    "Invalid acknowledge request");
        }

        TelegramAcknowledgeCallbackResult result;
        try {
            result = gateway.acknowledgeCallback(request);
        } catch (RuntimeException ex) {
            // Gateway contract'iga ko'ra exception tashlamasligi kerak —
            // bu yo'l defense-in-depth. exceptionType simple class name
            // bilan log yoziladi; exception message ataylab log'ga
            // chiqarilmaydi (token leak xavfi).
            log.warn("Telegram acknowledge failed resultType=UNEXPECTED exceptionType={}",
                    ex.getClass().getSimpleName());
            return TelegramAcknowledgeCallbackResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Acknowledge gateway threw unexpected exception");
        }

        if (result == null) {
            // Defensive — gateway hech qachon null qaytarmasligi shart.
            log.warn("Telegram acknowledge failed resultType=NULL_RESULT");
            return TelegramAcknowledgeCallbackResult.failed(
                    TelegramGatewayError.UNKNOWN_ERROR,
                    "Acknowledge gateway returned null");
        }

        if (result.isSuccess()) {
            log.info("Telegram acknowledge resultType=SUCCESS");
        } else {
            // Bounded log: faqat resultType + error klassifikatsiyasi
            // (enum simple name). errorMessage ataylab log qilinmaydi —
            // gateway tomonidan sanitize qilingan bo'lsa ham, log shape'ini
            // bounded ushlash uchun.
            log.warn("Telegram acknowledge resultType={} error={}",
                    result.getResultType(),
                    result.getError() == null ? null : result.getError().name());
        }
        return result;
    }
}
