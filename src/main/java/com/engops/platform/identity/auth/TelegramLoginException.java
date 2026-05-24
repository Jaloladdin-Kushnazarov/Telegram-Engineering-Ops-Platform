package com.engops.platform.identity.auth;

/**
 * Phase 218a — Telegram login muvaffaqiyatsiz bo'lganda tashlanadigan
 * exception. Controller'da 401 Unauthorized'ga aylantiriladi.
 *
 * <p>Sabablar: bot token sozlanmagan, hash tekshiruvi muvaffaqiyatsiz,
 * auth_date 24 soatdan eski, majburiy maydon yo'q.</p>
 */
public class TelegramLoginException extends RuntimeException {

    public TelegramLoginException(String message) {
        super(message);
    }
}
