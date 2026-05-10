package com.engops.platform.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 168 — Telegram outbound retry/backoff konfiguratsiya property'lari.
 *
 * <p>Property prefix: {@code app.telegram.retry}</p>
 *
 * <p>Standart qiymatlar production-uchun ehtiyotkor:</p>
 * <ul>
 *   <li>{@link #enabled} — default {@code true}; disable qilish uchun env'da
 *       {@code app.telegram.retry.enabled=false}</li>
 *   <li>{@link #maxAttempts} — default {@code 3} (1 birinchi urinish + 2 retry).
 *       Real {@link #getMaxAttempts()} har doim {@code >= 1}.</li>
 *   <li>{@link #initialBackoffMs} — default {@code 500} ms (birinchi backoff).
 *       Real {@link #getInitialBackoffMs()} har doim {@code >= 0}.</li>
 *   <li>{@link #maxBackoffMs} — default {@code 5000} ms (capped backoff).
 *       Real {@link #getMaxBackoffMs()} har doim {@code >= getInitialBackoffMs()}.</li>
 *   <li>{@link #multiplier} — default {@code 2.0} (exponential).
 *       Real {@link #getMultiplier()} har doim {@code >= 1.0} — qisqarayotgan
 *       backoff'ga ruxsat berilmaydi.</li>
 * </ul>
 *
 * <p><strong>Sanitization stratifeya:</strong> getter'lar minimal-safe qiymatlarni
 * qaytaradi. Setter'lar raw qiymatni o'zgartirmaydi (Spring Boot binding
 * shifosi shaffof bo'ladi). Consumer ({@code TelegramCardDispatchRetryingService})
 * faqat getter'lardan foydalanadi va ularning return qiymatlariga ishonadi.</p>
 *
 * <p><strong>Stub-mode xavfsizligi:</strong> {@code TELEGRAM_BOT_TOKEN} bo'sh
 * bo'lganda gateway {@code UNKNOWN_ERROR} qaytaradi va retrying service'ning
 * retryable matrix'ida {@code UNKNOWN_ERROR} ataylab non-retryable —
 * shu sababli stub mode hech qachon retry loopiga kirmaydi (har dispatch
 * tugashi uchun bitta urinish).</p>
 */
@ConfigurationProperties("app.telegram.retry")
public class TelegramRetryProperties {

    /** Retry'ni umumiy darajada yoqish/o'chirish. Default {@code true}. */
    private boolean enabled = true;

    /** Maksimal urinishlar soni (1 birinchi + retry'lar). Default {@code 3}. */
    private int maxAttempts = 3;

    /** Birinchi backoff (ms). Default {@code 500}. */
    private long initialBackoffMs = 500L;

    /** Backoff cap (ms). Default {@code 5000}. */
    private long maxBackoffMs = 5000L;

    /** Backoff multiplier. Default {@code 2.0}. */
    private double multiplier = 2.0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Maksimal urinishlar soni — har doim {@code >= 1}. Konfigurda
     * {@code 0} yoki manfiy qiymat berilsa, {@code 1} qaytariladi
     * (no-retry semantikasi).
     */
    public int getMaxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Birinchi backoff (ms) — har doim {@code >= 0}. Konfigurda manfiy
     * qiymat berilsa, {@code 0} qaytariladi.
     */
    public long getInitialBackoffMs() {
        return Math.max(0L, initialBackoffMs);
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Backoff cap (ms) — har doim {@code >= getInitialBackoffMs()}. Konfigurda
     * cap initial'dan kichik bo'lsa, initial qaytariladi (cap effectively
     * disabled qilinadi va backoff initial'da qoladi).
     */
    public long getMaxBackoffMs() {
        long initial = getInitialBackoffMs();
        return Math.max(initial, maxBackoffMs);
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * Backoff multiplier — har doim {@code >= 1.0}. Konfigurda {@code < 1.0}
     * berilsa, {@code 1.0} qaytariladi (qisqarayotgan backoff yo'q —
     * har retry oldingisidan kichik bo'lmaydi).
     */
    public double getMultiplier() {
        return Math.max(1.0, multiplier);
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
}
