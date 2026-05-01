package com.engops.platform.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 125 — JwtDecoder bean conditional behavior + signed-token round-trip test.
 *
 * <p>Property o'rnatilganda decoder yaratiladi va HMAC HS256 token'ni
 * tasdiqlay oladi. Bu testda biz token yaratish va decode qilish round-trip'ni
 * ham tekshiramiz — converter Phase 125'da SecurityConfig'ga ulanmagan
 * bo'lsa-da, decoder ishlash to'g'riligi tasdiqlanadi.</p>
 *
 * <p><strong>Phase 125 mini-fix:</strong> JWT imzolash helper'i production
 * code'da yo'q — bu testning private static metodi sifatida saqlanadi.
 * Production resource-server roli faqat tasdiqlash (decode), token chiqarish
 * emas.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "app.security.jwt.hmac-secret=test-secret-very-long-32-bytes-min!!")
class JwtAuthenticationConfigTest {

    private static final String SECRET = "test-secret-very-long-32-bytes-min!!";

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockBean
    private com.engops.platform.telegram.TelegramOutboundGateway telegramOutboundGateway;

    @Test
    void jwtDecoderBeanCreatedWhenPropertySet() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    void jwtDecoderDecodesValidHs256Token() throws JOSEException {
        UUID userId = UUID.randomUUID();
        String token = signHs256(SECRET,
                Map.of("sub", userId.toString(),
                       "telegram_user_id", 999L),
                300);

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.<Long>getClaim("telegram_user_id")).isEqualTo(999L);
    }

    @Test
    void jwtActorConverterMapsDecodedJwtToActor() throws JOSEException {
        UUID userId = UUID.randomUUID();
        String token = signHs256(SECRET,
                Map.of("sub", userId.toString(),
                       "telegram_user_id", 555L),
                300);

        Jwt jwt = jwtDecoder.decode(token);
        AuthenticatedActor actor = (AuthenticatedActor)
                new JwtActorConverter().convert(jwt).getPrincipal();

        assertThat(actor.appUserId()).isEqualTo(userId);
        assertThat(actor.telegramUserId()).isEqualTo(555L);
    }

    /**
     * Faqat test yordamchi metodi — HS256 imzolangan JWT yaratadi.
     * Production code'da bunday helper yo'q (resource-server roli decode'da
     * cheklangan). Nimbus API'si Spring Boot starter orqali test classpath'da
     * mavjud bo'lgani uchun qo'shimcha dependency talab qilmaydi.
     */
    private static String signHs256(String secret, Map<String, Object> claims, long ttlSeconds)
            throws JOSEException {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        long nowSeconds = System.currentTimeMillis() / 1000L;
        builder.issueTime(new Date(nowSeconds * 1000L));
        builder.expirationTime(new Date((nowSeconds + ttlSeconds) * 1000L));
        claims.forEach(builder::claim);
        SignedJWT signed = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                builder.build());
        signed.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return signed.serialize();
    }
}
