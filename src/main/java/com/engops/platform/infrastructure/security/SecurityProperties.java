package com.engops.platform.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security konfiguratsiya property'lari (Phase 125 + Phase 137).
 *
 * <p>JWT uchun uchta o'zaro mustasno (mutually exclusive) decoder rejimi
 * qo'llab-quvvatlanadi:</p>
 * <ul>
 *   <li>{@code app.security.jwt.hmac-secret} — HMAC HS256, asosan dev/test
 *       profil'lari uchun</li>
 *   <li>{@code app.security.jwt.issuer-uri} — production-friendly OIDC
 *       discovery orqali public-key decoder</li>
 *   <li>{@code app.security.jwt.jwk-set-uri} — production-friendly
 *       to'g'ridan-to'g'ri JWK Set URL orqali public-key decoder</li>
 * </ul>
 *
 * <p>Bir vaqtda faqat bittasi konfiguratsiya qilinishi mumkin —
 * {@link JwtAuthenticationConfig} startup'da fail-fast tekshiruv qiladi.</p>
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
     *
     * <p>Uchta o'zaro mustasno (mutually exclusive) decoder rejimini
     * qo'llab-quvvatlaydi:</p>
     * <ul>
     *   <li>{@link #hmacSecret} — dev/test uchun HMAC HS256</li>
     *   <li>{@link #issuerUri} — production OIDC discovery
     *       ({@code /.well-known/openid-configuration})</li>
     *   <li>{@link #jwkSetUri} — to'g'ridan-to'g'ri JWK Set URL</li>
     * </ul>
     *
     * <p>Bir vaqtda faqat bittasi konfiguratsiya qilinishi mumkin —
     * {@link JwtAuthenticationConfig} startup'da fail-fast tekshiruv qiladi.</p>
     */
    public static class Jwt {
        /**
         * HMAC secret JWT tokenlarini imzo bo'yicha tasdiqlash uchun (HS256).
         * Kamida 256-bit (32 bayt) bo'lishi kerak. Faqat dev/test uchun.
         * Production'da {@link #issuerUri} yoki {@link #jwkSetUri} yondashuvi
         * tavsiya etiladi. Property o'rnatilmasa, HMAC decoder bean yaratilmaydi
         * va startup ta'sirlanmaydi.
         */
        private String hmacSecret;

        /**
         * OIDC issuer URL — production deployment uchun. O'rnatilganda
         * {@link org.springframework.security.oauth2.jwt.JwtDecoders#fromIssuerLocation(String)}
         * orqali JwtDecoder yaratiladi. Decoder yaratilish vaqtida
         * {@code <issuer>/.well-known/openid-configuration} discovery so'rovi
         * bajariladi (eager). Property bo'sh yoki o'rnatilmagan bo'lsa decoder
         * yaratilmaydi.
         */
        private String issuerUri;

        /**
         * JWK Set URL — agar OIDC discovery'siz to'g'ridan-to'g'ri JWK
         * end-point ishlatilsa. O'rnatilganda
         * {@link org.springframework.security.oauth2.jwt.NimbusJwtDecoder#withJwkSetUri(String)}
         * orqali JwtDecoder yaratiladi. JWK kalitlari birinchi {@code decode()}
         * chaqiruvida lazy-fetch qilinadi. Property bo'sh yoki o'rnatilmagan
         * bo'lsa decoder yaratilmaydi.
         */
        private String jwkSetUri;

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }
}
