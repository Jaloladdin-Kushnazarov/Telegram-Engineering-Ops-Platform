package com.engops.platform.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Phase 171 — Telegram {@code callback_query} payload'ini parse va validate
 * qiladigan service.
 *
 * <p><strong>Phase 171 doirasi:</strong> faqat parse + validate + bounded
 * log + outcome qaytarish. Workflow transition <strong>BAJARILMAYDI</strong> —
 * {@link com.engops.platform.workflow.WorkflowTransitionService} bu service
 * tomonidan chaqirilmaydi va shu phase'da Telegram→app user identity
 * mapping'i mavjud emas. Action execution alohida keyingi phase'da hal
 * qilinadi.</p>
 *
 * <p><strong>Callback data shape:</strong> outbound tomondan
 * {@link TelegramActionAssembler} quyidagi formatda yaratadi:</p>
 * <pre>{@literal
 *   <UUID workItemId>:<ACTION_CODE>
 * }</pre>
 *
 * <p><strong>Known action codes</strong> (TelegramActionAssembler bilan
 * sinxronlashtirilgan; agar TelegramActionAssembler kelajakda yangi action
 * code qo'shsa, bu ro'yxatga ham qo'shilishi shart — aks holda yangi tugma
 * bosilganda {@link CallbackOutcome#IGNORED_UNKNOWN_ACTION} qaytadi):</p>
 * <ul>
 *   <li>{@code START_PROCESSING}</li>
 *   <li>{@code SEND_TO_TESTING}</li>
 *   <li>{@code MARK_FIXED}</li>
 *   <li>{@code RETURN_TO_BUGS}</li>
 *   <li>{@code REOPEN}</li>
 * </ul>
 *
 * <p><strong>Telegram callback_data limit:</strong> 64 bayt. Bu service
 * inbound data uzunligini tekshiradi va {@code length > 64} bo'lsa
 * {@link CallbackOutcome#IGNORED_TOO_LONG} qaytaradi (defensive — Telegram
 * o'zi 64 bayt cheklovini majbur qiladi, lekin malicious yoki bug'li
 * callback'larga qarshi himoya).</p>
 *
 * <p><strong>Blank data semantics:</strong> {@code data} null yoki bo'sh
 * bo'lsa (trim qilingandan keyin), {@link CallbackOutcome#IGNORED_NULL_DATA}
 * qaytariladi. Telegram inline button bosilishi har doim non-blank
 * {@code data} keltiradi (outbound bizda har doim {@code "<uuid>:<action>"}
 * yuboradi); bu yo'l tashqaridan kelgan malformed payload'larni cheradi.</p>
 *
 * <p><strong>Bounded log fields (har bir process chaqiruvida bitta INFO
 * log):</strong> {@code outcome}, {@code callbackQueryId},
 * {@code telegramUserId}, {@code chatId}, {@code messageId},
 * {@code dataLength}, va outcome'ga qarab {@code workItemId} +
 * {@code actionCode}. Hech qachon yozilmaydi: data sub-string'i, secret
 * token, exception message, full payload.</p>
 */
@Service
public class TelegramCallbackQueryService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCallbackQueryService.class);

    /** Telegram callback_data 64 bayt cheklovi. */
    static final int MAX_CALLBACK_DATA_BYTES = 64;

    /**
     * MVP Bug Flow'ga mos action codelar. {@link TelegramActionAssembler}
     * tomonidan yaratilgan har bir action code shu yerda ham bo'lishi shart.
     */
    static final Set<String> KNOWN_ACTION_CODES = Set.of(
            "START_PROCESSING",
            "SEND_TO_TESTING",
            "MARK_FIXED",
            "RETURN_TO_BUGS",
            "REOPEN");

    /**
     * Phase 171 callback parsing outcomes. Controller har holat uchun
     * 200 OK qaytaradi (Telegram retry'ni avoid qilish uchun) — outcome
     * faqat bounded log uchun ishlatiladi.
     */
    public enum CallbackOutcome {
        /** {@code callbackQuery} obyektining o'zi null edi. */
        IGNORED_NULL_CALLBACK,
        /** {@code data} null yoki blank edi (trim qilingandan keyin bo'sh). */
        IGNORED_NULL_DATA,
        /** {@code data} 64 baytdan oshib ketdi. */
        IGNORED_TOO_LONG,
        /** {@code data} formati noto'g'ri (no colon, bad UUID). */
        IGNORED_MALFORMED,
        /** Format to'g'ri lekin action code katalogda yo'q. */
        IGNORED_UNKNOWN_ACTION,
        /** Format to'g'ri va action code known — Phase 171 da faqat log + return. */
        ACCEPTED
    }

    /**
     * Callback_query payload'ini parse va validate qiladi.
     *
     * <p>Hech qanday holatda exception tashlamaydi — null callback ham
     * {@link CallbackOutcome#IGNORED_NULL_CALLBACK} sifatida qaytariladi.
     * Bu controller'ga "har holatda 200 OK qaytar" kontraktni ta'minlaydi.</p>
     *
     * @param callbackQuery Telegram'dan kelgan callback_query (null bo'lishi
     *                      mumkin — {@link CallbackOutcome#IGNORED_NULL_CALLBACK}
     *                      qaytariladi)
     * @return outcome enum
     */
    public CallbackOutcome process(TelegramCallbackQueryRequest callbackQuery) {
        if (callbackQuery == null) {
            log.info("Telegram callback ignored outcome={}", CallbackOutcome.IGNORED_NULL_CALLBACK);
            return CallbackOutcome.IGNORED_NULL_CALLBACK;
        }

        String data = callbackQuery.data();
        if (data == null || data.isBlank()) {
            logIgnored(CallbackOutcome.IGNORED_NULL_DATA, callbackQuery, 0, null, null);
            return CallbackOutcome.IGNORED_NULL_DATA;
        }

        int dataLength = data.length();
        if (dataLength > MAX_CALLBACK_DATA_BYTES) {
            logIgnored(CallbackOutcome.IGNORED_TOO_LONG, callbackQuery, dataLength, null, null);
            return CallbackOutcome.IGNORED_TOO_LONG;
        }

        int colon = data.indexOf(':');
        if (colon <= 0 || colon == data.length() - 1) {
            logIgnored(CallbackOutcome.IGNORED_MALFORMED, callbackQuery, dataLength, null, null);
            return CallbackOutcome.IGNORED_MALFORMED;
        }

        String workItemIdRaw = data.substring(0, colon);
        String actionCode = data.substring(colon + 1);

        UUID workItemId;
        try {
            workItemId = UUID.fromString(workItemIdRaw);
        } catch (IllegalArgumentException ex) {
            logIgnored(CallbackOutcome.IGNORED_MALFORMED, callbackQuery, dataLength, null, null);
            return CallbackOutcome.IGNORED_MALFORMED;
        }

        if (!KNOWN_ACTION_CODES.contains(actionCode)) {
            logIgnored(CallbackOutcome.IGNORED_UNKNOWN_ACTION, callbackQuery, dataLength,
                    workItemId, actionCode);
            return CallbackOutcome.IGNORED_UNKNOWN_ACTION;
        }

        // Phase 171: ACCEPTED — workflow transition BAJARILMAYDI.
        // Telegram→app user identity mapping va action execution keyingi
        // phase'da hal qilinadi.
        log.info("Telegram callback accepted outcome={} callbackQueryId={} telegramUserId={} chatId={} messageId={} dataLength={} workItemId={} actionCode={}",
                CallbackOutcome.ACCEPTED,
                callbackQuery.id(),
                telegramUserId(callbackQuery),
                chatId(callbackQuery),
                messageId(callbackQuery),
                dataLength,
                workItemId,
                actionCode);
        return CallbackOutcome.ACCEPTED;
    }

    private void logIgnored(CallbackOutcome outcome,
                             TelegramCallbackQueryRequest cb,
                             int dataLength,
                             UUID workItemId,
                             String actionCode) {
        log.info("Telegram callback ignored outcome={} callbackQueryId={} telegramUserId={} chatId={} messageId={} dataLength={} workItemId={} actionCode={}",
                outcome,
                cb.id(),
                telegramUserId(cb),
                chatId(cb),
                messageId(cb),
                dataLength,
                workItemId,
                actionCode);
    }

    private static Long telegramUserId(TelegramCallbackQueryRequest cb) {
        return cb.from() == null ? null : cb.from().id();
    }

    private static Long chatId(TelegramCallbackQueryRequest cb) {
        TelegramCallbackMessageRequest m = cb.message();
        if (m == null || m.chat() == null) {
            return null;
        }
        return m.chat().id();
    }

    private static Long messageId(TelegramCallbackQueryRequest cb) {
        return cb.message() == null ? null : cb.message().messageId();
    }
}
