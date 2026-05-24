package com.engops.platform.dev;

/**
 * Phase 211 — /api/dev/auth/bootstrap-admin-token va /api/dev/auth/token
 * endpoint'lari javob shakli. Token serialized JWT (Bearer header'da
 * to'g'ridan-to'g'ri ishlatish uchun).
 */
public record DevTokenResponse(String token) {
}
