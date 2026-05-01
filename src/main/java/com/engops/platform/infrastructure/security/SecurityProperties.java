package com.engops.platform.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 125 — security konfiguratsiya property'lari.
 *
 * <p>Hozirda faqat HMAC-asosida JWT decoder uchun secret saqlaydi —
 * dev/test profil'lari uchun. Production issuer-uri orqali public-key
 * yondashuvi keyingi phase'da qo'shiladi.</p>
 *
 * <p>Property prefix: {@code app.security}</p>
 */
@ConfigurationProperties("app.security")
public class SecurityProperties {

    private final Jwt jwt = new Jwt();

    public Jwt getJwt() {
        return jwt;
    }

    /**
     * JWT-related properties.
     */
    public static class Jwt {
        /**
         * HMAC secret JWT tokenlarini imzo bo'yicha tasdiqlash uchun (HS256).
         * Kamida 256-bit (32 bayt) bo'lishi kerak. Faqat dev/test uchun.
         * Production'da issuer-uri / jwk-set-uri yondashuvi tavsiya etiladi.
         * Property o'rnatilmasa, {@link JwtAuthenticationConfig#jwtDecoder} bean
         * yaratilmaydi va startup ta'sirlanmaydi.
         */
        private String hmacSecret;

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }
    }
}
