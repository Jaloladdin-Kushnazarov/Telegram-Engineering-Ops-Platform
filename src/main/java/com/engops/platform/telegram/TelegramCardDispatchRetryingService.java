package com.engops.platform.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Phase 168 — Telegram outbound dispatch'ga sinxron retry/backoff qatlami.
 *
 * <p>Bu servis {@link TelegramCardDispatchService} ni o'rab oladi va o'tib
 * tashlanadigan Telegram xatolari (RATE_LIMIT, NETWORK_ERROR) uchun
 * AFTER_COMMIT yo'lining ichida sinxron capped exponential backoff bilan
 * qayta urinadi. Har bir urinish mavjud {@link TelegramCardDispatchService#dispatchAttempt}
 * orqali alohida {@code telegram_delivery_attempt} row'ini yozadi —
 * shuning uchun observability admin endpoint'larida har bir retry vaqt
 * jadvali to'liq ko'rinadi.</p>
 *
 * <p><strong>Retry policy:</strong></p>
 * <table>
 *   <tr><th>Outcome</th><th>failure_code</th><th>Retry?</th></tr>
 *   <tr><td>DELIVERED</td><td>—</td><td>No (success)</td></tr>
 *   <tr><td>REJECTED</td><td>INVALID_REQUEST</td><td>No (permanent)</td></tr>
 *   <tr><td>FAILED</td><td>{@code RATE_LIMIT}</td><td>Yes</td></tr>
 *   <tr><td>FAILED</td><td>{@code NETWORK_ERROR}</td><td>Yes</td></tr>
 *   <tr><td>FAILED</td><td>{@code UNKNOWN_ERROR}</td><td><strong>No</strong> — stub-mode safety: stub gateway {@code UNKNOWN_ERROR} qaytaradi va retry loopiga kirib ketmasligi shart. Real production'da ham {@code UNKNOWN_ERROR} ko'pincha deterministik xato yoki muvaffaqiyatli yuborilgan xabar javobini parse qila olmaslikni ifodalaydi — duplicate xabarlar oldini olish uchun retry qilinmaydi.</td></tr>
 *   <tr><td>FAILED</td><td>boshqa</td><td>No (defensive default)</td></tr>
 * </table>
 *
 * <p><strong>Backoff:</strong> capped exponential.
 * {@code delay(i) = min(maxBackoffMs, initialBackoffMs × multiplier^i)}.
 * Oxirgi urinishdan keyin sleep yo'q.</p>
 *
 * <p><strong>Transaction'lar bilan ishlash:</strong> bu servis o'zi hech qanday
 * {@code @Transactional} olmaydi va HTTP chaqiruvi yoki backoff sleep
 * davomida DB connection ushlamaydi. Har bir urinishning persistence qadami
 * {@link JpaTelegramDeliveryAttemptPersistence#save} ichidagi
 * {@code @Transactional(REQUIRES_NEW)} orqali alohida qisqa transaction'da
 * commit qilinadi (Phase 168 da Phase 164 mini-fix REQUIRES_NEW listener
 * metodidan persistence qatlamiga ko'chirildi). Hikari connection
 * occupancy retry urinishlari va sleep oralarida nolga tushadi.</p>
 *
 * <p><strong>Interrupt handling:</strong> sleep paytida {@link InterruptedException}
 * ushlanadi va thread interrupt flag qayta o'rnatiladi
 * ({@link Thread#currentThread()}{@code .interrupt()}). Retry loop darhol
 * to'xtaydi va so'nggi {@link TelegramDeliveryAttempt} qaytariladi —
 * exception caller'ga propagatsiya qilinmaydi.</p>
 *
 * <p><strong>Out of scope (Phase 168):</strong> {@code Retry-After} HTTP
 * header'ini parse qilish (gateway-side change kerak), per-error custom
 * backoff, jitter, async/scheduler/outbox/queue, webhook/callback_query.</p>
 */
@Service
@EnableConfigurationProperties(TelegramRetryProperties.class)
public class TelegramCardDispatchRetryingService {

    /**
     * Test'larda real {@link Thread#sleep} chaqiruvini almashtirish uchun
     * minimal abstraktsiya. Production'da {@link Thread#sleep} ishlatiladi.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleepMillis(long ms) throws InterruptedException;
    }

    private final TelegramCardDispatchService cardDispatchService;
    private final TelegramRetryProperties properties;
    private final Sleeper sleeper;

    @Autowired
    public TelegramCardDispatchRetryingService(TelegramCardDispatchService cardDispatchService,
                                                TelegramRetryProperties properties) {
        this(cardDispatchService, properties, Thread::sleep);
    }

    /**
     * Test'lar uchun package-private konstruktor — sleep'ni almashtirish.
     */
    TelegramCardDispatchRetryingService(TelegramCardDispatchService cardDispatchService,
                                         TelegramRetryProperties properties,
                                         Sleeper sleeper) {
        this.cardDispatchService = cardDispatchService;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * {@link TelegramCardView}'ni dispatch qiladi va kerak bo'lsa retry oladi.
     *
     * <p>Birinchi {@code DELIVERED} / {@code REJECTED} / non-retryable
     * {@code FAILED} natijasida darhol qaytadi. Retryable failure
     * ({@code RATE_LIMIT}, {@code NETWORK_ERROR}) sodir bo'lsa,
     * {@link TelegramRetryProperties#getMaxAttempts()} chegarasigacha
     * exponential backoff bilan qayta urinadi.</p>
     *
     * @param cardView dispatch qilinadigan tayyor card view
     * @return so'nggi urinishning {@link TelegramDeliveryAttempt} record'i
     * @throws IllegalArgumentException agar {@code cardView} null bo'lsa
     */
    public TelegramDeliveryAttempt dispatchWithRetry(TelegramCardView cardView) {
        if (cardView == null) {
            throw new IllegalArgumentException("TelegramCardView null bo'lishi mumkin emas");
        }

        if (!properties.isEnabled()) {
            return cardDispatchService.dispatchAttempt(cardView);
        }

        int maxAttempts = properties.getMaxAttempts();
        TelegramDeliveryAttempt last = null;

        for (int attemptIndex = 0; attemptIndex < maxAttempts; attemptIndex++) {
            last = cardDispatchService.dispatchAttempt(cardView);

            if (!isRetryable(last)) {
                return last;
            }
            if (attemptIndex == maxAttempts - 1) {
                // Oxirgi urinish — sleep qilinmaydi.
                return last;
            }

            long delay = computeBackoffMs(attemptIndex);
            try {
                sleeper.sleepMillis(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        return last;
    }

    private boolean isRetryable(TelegramDeliveryAttempt attempt) {
        if (attempt == null) {
            // Defensive: production'da TelegramCardDispatchService null
            // qaytarmaydi — bu yo'l faqat test mock default'lariga himoya.
            return false;
        }
        if (attempt.getDeliveryOutcome() != TelegramDeliveryResult.DeliveryOutcome.FAILED) {
            return false;
        }
        String code = attempt.getFailureCode();
        return TelegramGatewayError.RATE_LIMIT.name().equals(code)
                || TelegramGatewayError.NETWORK_ERROR.name().equals(code);
    }

    long computeBackoffMs(int retryIndex) {
        long initial = properties.getInitialBackoffMs();
        long max = properties.getMaxBackoffMs();
        double multiplier = properties.getMultiplier();
        // delay(i) = min(max, initial * multiplier^i)
        double computed = (double) initial * Math.pow(multiplier, retryIndex);
        if (computed >= (double) max) {
            return max;
        }
        long rounded = (long) computed;
        return Math.max(0L, rounded);
    }
}
