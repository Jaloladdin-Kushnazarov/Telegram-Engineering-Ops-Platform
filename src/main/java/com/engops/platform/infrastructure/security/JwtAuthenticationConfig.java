package com.engops.platform.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Phase 125 — JWT decoder konfiguratsiyasi.
 *
 * <p>{@link JwtDecoder} bean faqat {@code app.security.jwt.hmac-secret}
 * property o'rnatilganda yaratiladi (dev/test uchun HMAC HS256 yondashuvi).
 * Property yo'q bo'lsa, bean yaratilmaydi va startup'ga ta'sir qilmaydi —
 * mavjud test/prod profillari avvalgi singari yuklanadi.</p>
 *
 * <p>Production'da issuer-uri / jwk-set-uri yondashuvi keyingi phase'da
 * qo'shiladi. Hozirda HMAC secret kichik foundation sifatida xizmat qiladi.</p>
 *
 * <p>Bean Phase 125'da SecurityConfig'ga ulanmaydi —
 * {@code .oauth2ResourceServer(...)} chaqiruvi qo'shilmagan. Phase 126+ da
 * decoder + {@link JwtActorConverter} birlashtiriladi.</p>
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

    /**
     * HMAC HS256 asosidagi {@link JwtDecoder}. Faqat
     * {@code app.security.jwt.hmac-secret} property mavjud bo'lganda yaratiladi.
     *
     * @param props security properties (HMAC secret'ni o'qish uchun)
     * @return HS256 decoder
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt", name = "hmac-secret")
    public JwtDecoder jwtDecoder(SecurityProperties props) {
        String secret = props.getJwt().getHmacSecret();
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), JWT_ALG);
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
