package com.engops.platform.infrastructure.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT decoder konfiguratsiyasi — Phase 125 (HMAC) + Phase 137 (issuer-uri / jwk-set-uri).
 *
 * <p>Uchta o'zaro mustasno (mutually exclusive) decoder rejimi qo'llab-quvvatlanadi.
 * Har biri o'zining property'si bilan conditional ravishda yoqiladi:</p>
 * <ul>
 *   <li>{@code app.security.jwt.hmac-secret} — HMAC HS256 (dev/test)</li>
 *   <li>{@code app.security.jwt.issuer-uri} — OIDC discovery (production)</li>
 *   <li>{@code app.security.jwt.jwk-set-uri} — to'g'ridan-to'g'ri JWK Set URL (production)</li>
 * </ul>
 *
 * <p>Bir vaqtda faqat bittasi konfiguratsiya qilinishi mumkin —
 * {@link #validateExclusiveDecoderMode()} startup'da {@link IllegalStateException}
 * tashlash orqali fail-fast amalga oshiradi. Hech qaysisi o'rnatilmasa, hech
 * qanday {@link JwtDecoder} bean yaratilmaydi va startup'ga ta'sir qilmaydi.</p>
 *
 * <p>Phase 137 SecurityConfig'ga tegmaydi — u allaqachon mavjud
 * {@link JwtDecoder} bean'ni {@code oauth2ResourceServer} chain'iga
 * conditional ravishda wire qiladi. Shu sababli yangi decoder rejimlari
 * avtomatik ravishda qo'llab-quvvatlanadi.</p>
 *
 * <p><strong>Diqqat — Phase 125 mini-fix:</strong> Bu klass faqat JWT'ni
 * tasdiqlash (decode) bilan shug'ullanadi. JWT imzolash (signing) helper'i
 * production code'da yo'q — u faqat test fayli ichida private static
 * sifatida saqlanadi (resource-server roli token chiqarish emas, balki
 * tasdiqlash).</p>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtAuthenticationConfig {

    private static final String JWT_ALG = "HmacSHA256";

    private final SecurityProperties properties;

    public JwtAuthenticationConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Startup-time fail-fast tekshiruv: HMAC, issuer-uri va jwk-set-uri
     * o'zaro mustasno deployment rejimlari, bir vaqtda bir nechtasi
     * o'rnatilsa konfiguratsiya xatoligi.
     *
     * @throws IllegalStateException agar bir nechta JWT decoder rejim
     *                               konfiguratsiya qilingan bo'lsa
     */
    @PostConstruct
    void validateExclusiveDecoderMode() {
        SecurityProperties.Jwt jwt = properties.getJwt();
        int configuredModes = 0;
        if (StringUtils.hasText(jwt.getHmacSecret())) {
            configuredModes++;
        }
        if (StringUtils.hasText(jwt.getIssuerUri())) {
            configuredModes++;
        }
        if (StringUtils.hasText(jwt.getJwkSetUri())) {
            configuredModes++;
        }
        if (configuredModes > 1) {
            throw new IllegalStateException(
                    "JWT decoder konfiguratsiyasi noto'g'ri: "
                            + "app.security.jwt.{hmac-secret, issuer-uri, jwk-set-uri} "
                            + "dan faqat bittasi o'rnatilishi mumkin");
        }
    }

    /**
     * HMAC HS256 asosidagi {@link JwtDecoder}. Faqat
     * {@code app.security.jwt.hmac-secret} property mavjud va non-blank
     * bo'lganda yaratiladi.
     *
     * @param props security properties (HMAC secret'ni o'qish uchun)
     * @return HS256 decoder
     */
    @Bean
    @ConditionalOnExpression("'${app.security.jwt.hmac-secret:}'.trim().length() > 0")
    public JwtDecoder hmacJwtDecoder(SecurityProperties props) {
        String secret = props.getJwt().getHmacSecret();
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), JWT_ALG);
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    /**
     * OIDC issuer-uri asosidagi {@link JwtDecoder}. Faqat
     * {@code app.security.jwt.issuer-uri} property mavjud va non-blank
     * bo'lganda yaratiladi. {@link JwtDecoders#fromIssuerLocation(String)}
     * eager ravishda {@code /.well-known/openid-configuration} discovery
     * so'rovini amalga oshiradi va {@code jwks_uri} dan JWK URL ni oladi.
     *
     * @param props security properties (issuer URI ni o'qish uchun)
     * @return OIDC discovery orqali yaratilgan decoder
     */
    @Bean
    @ConditionalOnExpression("'${app.security.jwt.issuer-uri:}'.trim().length() > 0")
    public JwtDecoder issuerUriJwtDecoder(SecurityProperties props) {
        return JwtDecoders.fromIssuerLocation(props.getJwt().getIssuerUri());
    }

    /**
     * To'g'ridan-to'g'ri JWK Set URL asosidagi {@link JwtDecoder}. Faqat
     * {@code app.security.jwt.jwk-set-uri} property mavjud va non-blank
     * bo'lganda yaratiladi. JWK kalitlari birinchi {@code decode()}
     * chaqiruvida lazy-fetch qilinadi (eager network call yo'q).
     *
     * @param props security properties (JWK Set URI ni o'qish uchun)
     * @return JWK Set URL orqali yaratilgan decoder
     */
    @Bean
    @ConditionalOnExpression("'${app.security.jwt.jwk-set-uri:}'.trim().length() > 0")
    public JwtDecoder jwkSetUriJwtDecoder(SecurityProperties props) {
        return NimbusJwtDecoder.withJwkSetUri(props.getJwt().getJwkSetUri()).build();
    }
}
