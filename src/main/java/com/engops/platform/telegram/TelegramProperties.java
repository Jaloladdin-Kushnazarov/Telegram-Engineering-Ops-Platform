package com.engops.platform.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 158 — Telegram outbound gateway konfiguratsiya property'lari.
 *
 * <p>Property prefix: {@code app.telegram}</p>
 *
 * <p>{@link #botToken} default qiymati YO'Q — production'da
 * {@code TELEGRAM_BOT_TOKEN} env var orqali berilishi shart. Token bo'sh yoki
 * yo'q bo'lsa real {@link HttpTelegramOutboundGateway} bean yaratilmaydi va
 * {@link StubTelegramOutboundGateway} fallback ishlaydi (kontrakt
 * {@link TelegramOutboundGatewayConfiguration} ichida {@code @ConditionalOnExpression}
 * orqali ushlangan). Bu Phase 125/137 JWT decoder pattern'i bilan bir xil.</p>
 *
 * <p>Token ataylab logga yozilmaydi va exception/persistence message'lariga
 * qo'shilmaydi. {@link HttpTelegramOutboundGateway} runtime'da har qanday
 * holatda ham token sub-string'ini sanitize qiladi.</p>
 */
@ConfigurationProperties("app.telegram")
public class TelegramProperties {

    /**
     * Telegram Bot API tokeni. Default qiymati yo'q. Bo'sh/non-blank
     * bo'lganda real HTTP gateway aktivlashtiriladi. Production'da env var
     * (masalan {@code TELEGRAM_BOT_TOKEN}) orqali berilishi shart va hech
     * qachon repository'ga commit qilinmaydi.
     */
    private String botToken;

    /**
     * Telegram Bot API base URL. Production default
     * {@code https://api.telegram.org}. Test/dev sharoitda mock URL'ga
     * override qilish mumkin.
     */
    private String apiBaseUrl = "https://api.telegram.org";

    /** TCP connect timeout (ms). Default 5000. */
    private int connectTimeoutMs = 5000;

    /** Response read timeout (ms). Default 10000. */
    private int readTimeoutMs = 10000;

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
