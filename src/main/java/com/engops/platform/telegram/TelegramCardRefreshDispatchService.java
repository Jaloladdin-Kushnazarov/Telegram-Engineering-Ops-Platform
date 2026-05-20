package com.engops.platform.telegram;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Phase 179 — AFTER_COMMIT Telegram card dispatch'da edit-first /
 * send-as-fallback siyosatini boshqaruvchi coordinator.
 *
 * <p><strong>Algoritm:</strong></p>
 * <ol>
 *   <li>Defensiv input validatsiya — null {@code cardView} / {@code tenantId} /
 *       {@code workItemId} bo'lsa bounded log yoziladi va metod hech qanday
 *       harakatsiz qaytadi.</li>
 *   <li>Render bir marta: {@link TelegramMessageRenderer#render(TelegramCardView)}
 *       chaqirilib, {@code TelegramMessage} (text + keyboard) olinadi.
 *       Renderer kutilmagan {@link RuntimeException} tashlasa, swallow
 *       qilinadi va fallback send chaqirilmaydi — mavjud fail-soft
 *       invariantini saqlash uchun.</li>
 *   <li>Edit attempt: {@link TelegramCardRefreshService#refresh(UUID, UUID, String, java.util.List)}.
 *       Natijaga qarab qaror qabul qilinadi:
 *       <ul>
 *         <li>{@code SUCCESS} → STOP. Send chaqirilmaydi.</li>
 *         <li>{@code REJECTED} + "message is not modified" (case-insensitive)
 *             → benign no-op. Send chaqirilmaydi.</li>
 *         <li>boshqa {@code REJECTED} / har qanday {@code FAILED} / null
 *             natija / {@link RuntimeException} → bitta marta fallback send
 *             {@link TelegramCardDispatchRetryingService#dispatchWithRetry(TelegramCardView)}
 *             chaqiriladi.</li>
 *       </ul></li>
 * </ol>
 *
 * <p><strong>Duplicate-card himoyasi:</strong> har bir AFTER_COMMIT event
 * uchun coordinator faqat bitta yo'lni tanlaydi — yo edit, yo send.
 * Hech qachon ikkalasini birdaniga chaqirmaydi. "Edit muvaffaqiyatsiz →
 * fallback send" yo'lida ham operator faqat bitta yangi card ko'radi
 * (oldingi edit muvaffaqiyatsiz bo'lgani uchun chat'da hech qanday
 * vizual o'zgarish qoldirmagan).</p>
 *
 * <p><strong>Edit uchun retry yo'q.</strong> Telegram tomondagi edit
 * deadline cheklangan. Retry qiladigan bo'lsak, fallback send'ning
 * mavjud retry pipeline'i bilan birga AFTER_COMMIT thread vaqtini va
 * duplicate-card oynasini cho'zib yuborardi. Single-shot edit + send
 * retry-fallback eng oddiy va ishonchli shakl.</p>
 *
 * <p><strong>"Message is not modified" — benign no-op.</strong> Telegram
 * bu 4xx'ni yangi text mavjud text bilan bayt-baytma teng bo'lganda
 * qaytaradi. UX nuqtai nazaridan card allaqachon kerakli holatda —
 * yangi send mantiqsiz. Description Telegram versiyalari orasida
 * o'zgarishi mumkin, shuning uchun {@code contains("message is not modified")}
 * case-insensitive match ishlatiladi.</p>
 *
 * <p><strong>Transaction boundary:</strong> bu service class-level
 * {@code @Transactional} EMAS. Listener uni AFTER_COMMIT thread'da chaqiradi —
 * business transaction allaqachon commit qilingan. Coordinator hech qanday
 * DB yozish operatsiyasi qilmaydi. Send fallback'ning persistence qadami
 * mavjud {@code JpaTelegramDeliveryAttemptPersistence.save} ichidagi
 * {@code REQUIRES_NEW} qisqa transaction'da bajariladi (Phase 168 invariant).</p>
 *
 * <p><strong>Persistence:</strong> Phase 179 da {@code EDIT_MESSAGE}
 * attemptlari {@code telegram_delivery_attempt} jadvaliga yozilmaydi —
 * sabablar:</p>
 * <ul>
 *   <li>Phase 177 active-card seed query {@code SEND_NEW_MESSAGE} ga
 *       anchored — edit row'lari uni o'zgartirmaydi, shuning uchun
 *       persistence active-card invariantiga foyda bermaydi.</li>
 *   <li>Mavjud {@code findLatestAttempt} metrics endpoint'i har qanday
 *       operatsiyani qaytaradi — edit row'lari kiritilsa, mavjud
 *       dashboard semantikasi jim ravishda o'zgarib ketardi.</li>
 *   <li>Bounded log yetarli debugging signal beradi.</li>
 * </ul>
 *
 * <p><strong>Logging hygiene:</strong> har bir dispatch chaqiruvi uchun
 * bitta bounded log. Hech qachon yozilmaydi: bot token, full URL,
 * payload, rendered text, callback_data, exception message, kutilgan
 * yo'llar uchun stack trace. {@code exceptionType} faqat
 * {@code RuntimeException} swallow yo'llarida class simple name bilan
 * yoziladi.</p>
 *
 * <p><strong>Module boundary:</strong> bu service {@code ..telegram..}
 * paketida joylashgan va {@code ..workflow..}'ni import qilmaydi
 * ({@code ModuleBoundaryTest} qoidasi).</p>
 */
@Service
public class TelegramCardRefreshDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TelegramCardRefreshDispatchService.class);

    /** "Message is not modified" matni uchun case-insensitive lookup. */
    private static final String NOT_MODIFIED_MARKER = "message is not modified";

    /**
     * Coordinator natija kategoriyalari — bounded log uchun ishlatiladi.
     * Test'larda outcome bilan tasdiqlash uchun ham foydali bo'lishi mumkin.
     */
    public enum OutcomeCategory {
        /** Defensiv input fail (cardView/tenantId/workItemId null). */
        SKIPPED_BAD_INPUT,
        /** Renderer kutilmagan exception tashladi — fallback send chaqirilmaydi. */
        RENDERER_THREW_SWALLOWED,
        /** Edit muvaffaqiyatli — send fallback chaqirilmaydi. */
        EDITED,
        /** Telegram "message is not modified" — benign no-op, send chaqirilmaydi. */
        NOT_MODIFIED,
        /** Edit REJECTED (boshqa sabab) — fallback send chaqirildi. */
        EDIT_REJECTED_FALLBACK_SEND,
        /** Edit FAILED RATE_LIMIT — fallback send chaqirildi. */
        EDIT_RATE_LIMIT_FALLBACK_SEND,
        /** Edit FAILED NETWORK_ERROR — fallback send chaqirildi. */
        EDIT_NETWORK_FALLBACK_SEND,
        /** Edit FAILED UNKNOWN_ERROR yoki boshqa — fallback send chaqirildi. */
        EDIT_FAILED_FALLBACK_SEND,
        /** Edit natija null — fallback send chaqirildi. */
        EDIT_NULL_RESULT_FALLBACK_SEND,
        /** Refresh service kutilmagan exception tashladi — fallback send chaqirildi. */
        REFRESH_THREW_FALLBACK_SEND
    }

    /** Phase 189 — coordinator outcome counter nomi (low-cardinality). */
    static final String REFRESH_OUTCOMES_METER = "engops.telegram.card.refresh.outcomes";

    private final TelegramMessageRenderer renderer;
    private final TelegramCardRefreshService refreshService;
    private final TelegramCardDispatchRetryingService retryingService;
    private final MeterRegistry meterRegistry;

    public TelegramCardRefreshDispatchService(TelegramMessageRenderer renderer,
                                                TelegramCardRefreshService refreshService,
                                                TelegramCardDispatchRetryingService retryingService,
                                                MeterRegistry meterRegistry) {
        this.renderer = renderer;
        this.refreshService = refreshService;
        this.retryingService = retryingService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * AFTER_COMMIT card dispatch uchun edit-first / send-as-fallback
     * siyosatini bajaradi. Hech qachon caller'ga exception propagate
     * qilmaydi — fail-soft kontrakti listener bilan birga ushlanadi.
     *
     * @param cardView render qilinishi mumkin bo'lgan card view
     * @param tenantId tenant identifikatori
     * @param workItemId work item identifikatori
     */
    public void dispatch(TelegramCardView cardView, UUID tenantId, UUID workItemId) {
        if (cardView == null || tenantId == null || workItemId == null) {
            logOutcome(OutcomeCategory.SKIPPED_BAD_INPUT, tenantId, workItemId, null, null, null);
            return;
        }

        TelegramMessage message;
        try {
            message = renderer.render(cardView);
        } catch (RuntimeException ex) {
            // Defensiv: renderer mavjud kontraktda null/invalid input'da
            // exception tashlaydi. Fallback send ham renderer'ni qayta
            // chaqirardi va xuddi shu xatolik bilan tugardi — shuning
            // uchun fail-soft yo'l: log + STOP.
            logOutcome(OutcomeCategory.RENDERER_THREW_SWALLOWED, tenantId, workItemId, null, null,
                    ex.getClass().getSimpleName());
            return;
        }

        TelegramEditMessageTextResult result;
        try {
            result = refreshService.refresh(tenantId, workItemId,
                    message.getText(), message.getKeyboard());
        } catch (RuntimeException ex) {
            logOutcome(OutcomeCategory.REFRESH_THREW_FALLBACK_SEND, tenantId, workItemId,
                    null, null, ex.getClass().getSimpleName());
            fallbackSend(cardView);
            return;
        }

        if (result == null) {
            logOutcome(OutcomeCategory.EDIT_NULL_RESULT_FALLBACK_SEND, tenantId, workItemId,
                    null, null, null);
            fallbackSend(cardView);
            return;
        }

        switch (result.getResultType()) {
            case SUCCESS -> {
                logOutcome(OutcomeCategory.EDITED, tenantId, workItemId, result, null, null);
                // STOP — send chaqirilmaydi.
            }
            case REJECTED -> {
                if (isMessageNotModified(result.getErrorMessage())) {
                    logOutcome(OutcomeCategory.NOT_MODIFIED, tenantId, workItemId, result, null, null);
                    // STOP — benign no-op.
                } else {
                    logOutcome(OutcomeCategory.EDIT_REJECTED_FALLBACK_SEND, tenantId, workItemId,
                            result, null, null);
                    fallbackSend(cardView);
                }
            }
            case FAILED -> {
                // FAILED factory error non-null bo'lishini majbur qiladi;
                // shuning uchun bu yerda enum allaqachon mavjud.
                OutcomeCategory category = switch (result.getError()) {
                    case RATE_LIMIT -> OutcomeCategory.EDIT_RATE_LIMIT_FALLBACK_SEND;
                    case NETWORK_ERROR -> OutcomeCategory.EDIT_NETWORK_FALLBACK_SEND;
                    case INVALID_REQUEST, UNKNOWN_ERROR ->
                            OutcomeCategory.EDIT_FAILED_FALLBACK_SEND;
                };
                logOutcome(category, tenantId, workItemId, result, null, null);
                fallbackSend(cardView);
            }
        }
    }

    private void fallbackSend(TelegramCardView cardView) {
        // Mavjud retry pipeline o'zgarmaydi — RATE_LIMIT/NETWORK_ERROR
        // uchun capped exponential backoff va SEND_NEW_MESSAGE persistence
        // shu yerda amalga oshadi (Phase 158/164/168 invariantlari).
        retryingService.dispatchWithRetry(cardView);
    }

    private static boolean isMessageNotModified(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        return errorMessage.toLowerCase(Locale.ROOT).contains(NOT_MODIFIED_MARKER);
    }

    /**
     * Bounded log: faqat xavfsiz metadata. Rendered text, exception
     * message, token, full URL, callback_data — hech qachon log'ga
     * chiqarilmaydi.
     *
     * <p>Phase 189: bu yo'lda {@code engops.telegram.card.refresh.outcomes}
     * counter ham bir martagina increment qilinadi. Tag faqat
     * {@link OutcomeCategory#name() outcome} — tenantId, workItemId,
     * exceptionType counter tag'iga TUSHMAYDI (low-cardinality
     * cheklov).</p>
     */
    private void logOutcome(OutcomeCategory outcomeCategory,
                             UUID tenantId,
                             UUID workItemId,
                             TelegramEditMessageTextResult result,
                             String unusedReservedSlot,
                             String exceptionType) {
        // Note: result null bo'lishi mumkin; bo'lsa resultType/error
        // ham null sifatida loglanadi (bounded shape saqlanadi).
        log.info("Telegram card refresh dispatch outcome={} tenantId={} workItemId={} resultType={} error={} exceptionType={}",
                outcomeCategory,
                tenantId,
                workItemId,
                result == null ? null : result.getResultType(),
                result == null || result.getError() == null ? null : result.getError().name(),
                exceptionType);
        recordOutcomeCounter(outcomeCategory);
    }

    private void recordOutcomeCounter(OutcomeCategory outcomeCategory) {
        if (meterRegistry == null || outcomeCategory == null) {
            return;
        }
        Counter.builder(REFRESH_OUTCOMES_METER)
                .tag("outcome", outcomeCategory.name())
                .register(meterRegistry)
                .increment();
    }
}
