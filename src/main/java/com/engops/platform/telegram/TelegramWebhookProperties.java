package com.engops.platform.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 171 — Telegram inbound webhook konfiguratsiya property'lari.
 *
 * <p>Property prefix: {@code app.telegram.webhook}</p>
 *
 * <p>Yagona maydon — {@link #secretToken}. Bu Telegram Bot API'ning
 * <em>Secret Token</em> mexanizmidan kelib chiqadi: operator
 * {@code setWebhook} chaqiruvida {@code secret_token} qiymatini bersa,
 * Telegram har bir webhook so'rovida shuni
 * {@code X-Telegram-Bot-Api-Secret-Token} HTTP header'i orqali qaytaradi.
 * Backend o'sha header'ni configured qiymat bilan
 * <strong>constant-time</strong> taqqoslab kelganini tasdiqlaydi.</p>
 *
 * <p><strong>Property binding:</strong>
 * {@code app.telegram.webhook.secret-token}. Tavsiya etilgan env var:
 * {@code TELEGRAM_WEBHOOK_SECRET_TOKEN}.</p>
 *
 * <p><strong>Fail-closed posture:</strong> {@link #getSecretToken()}
 * trimmed qiymat yoki bo'sh string qaytaradi (hech qachon null);
 * controller bo'sh string holatini "configured emas" deb hisoblaydi va
 * har qanday inbound so'rovga {@code 401 Unauthorized} qaytaradi.
 * Production deployment'lar shu sababdan tokenni env / secret manager
 * orqali majburiy o'rnatishi shart (Phase 158 {@code app.telegram.bot-token}
 * bilan bir xil pattern).</p>
 *
 * <p><strong>Logga yozilmaydi.</strong> Token qiymati hech qachon log'ga
 * chiqarilmaydi va exception message'larga qo'shilmaydi.</p>
 *
 * <p><strong>Bot-token activation'dan mustaqil:</strong> bu properties
 * outbound {@code TelegramOutboundGatewayConfiguration}'ning conditional
 * yuklanishiga bog'liq emas — webhook bot tokendan oldin yoki keyin
 * sozlanishi mumkin. Registratsiya {@link TelegramWebhookController}
 * ichidagi {@code @EnableConfigurationProperties} orqali always-loaded
 * (Phase 168 {@code TelegramRetryProperties} bilan bir xil pattern).</p>
 */
@ConfigurationProperties("app.telegram.webhook")
public class TelegramWebhookProperties {

    /**
     * Telegram Bot API <em>secret_token</em> qiymati. Default qiymati yo'q.
     * Bo'sh / null / blank bo'lsa, webhook har qanday so'rovni rad etadi.
     */
    private String secretToken;

    /**
     * Trimmed token qiymatini qaytaradi. Null yoki blank holatda bo'sh string
     * qaytariladi (hech qachon null) — caller "configured emas" sharoitini
     * {@code .isEmpty()} bilan tekshiradi.
     */
    public String getSecretToken() {
        if (secretToken == null) {
            return "";
        }
        return secretToken.trim();
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }
}
