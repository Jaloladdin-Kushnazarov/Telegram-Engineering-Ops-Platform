package com.engops.platform.telegram;

/**
 * Telegram gateway xato klassifikatsiyasi.
 *
 * Gateway execution muvaffaqiyatsiz bo'lganda xato turini aniqlaydi.
 * Keyingi phase'larda retry va resilience logikasi shu klassifikatsiya
 * asosida qaror qabul qiladi.
 *
 * Masalan:
 * - RATE_LIMIT → retry with backoff
 * - NETWORK_ERROR → retry
 * - INVALID_REQUEST → retry qilmaslik
 * - UNKNOWN_ERROR → log va alert
 */
public enum TelegramGatewayError {

    NETWORK_ERROR,
    RATE_LIMIT,
    INVALID_REQUEST,
    UNKNOWN_ERROR
}
