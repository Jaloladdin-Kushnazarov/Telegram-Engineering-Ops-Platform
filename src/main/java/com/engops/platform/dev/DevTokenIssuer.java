package com.engops.platform.dev;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 211 — HS256 JWT signer (DEV PROFILE ONLY).
 *
 * <p>Faqat {@code app.security.dev-mode.enabled=true} bo'lganda bean
 * yaratiladi. Production'da property o'rnatilmagan — bean yo'q.</p>
 *
 * <p>Token shape JwtActorConverter (Phase 125) talablariga moslangan:
 * {@code sub} = AppUser UUID (mandatory), {@code iat} = now, {@code exp} =
 * now + TTL, {@code iss} = "engops-dev". Sign algorithm HS256 with shared
 * HMAC secret (application-dev.properties'da bir xil secret
 * JwtAuthenticationConfig.hmacJwtDecoder ham ishlatadi — round-trip).</p>
 *
 * <p>Nimbus signing pattern Phase 125 test code'ining
 * {@code JwtAuthenticationConfigTest#signHs256} helper'idan ko'chirildi.</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.dev-mode.enabled", havingValue = "true")
public class DevTokenIssuer {

    private final byte[] hmacKey;

    public DevTokenIssuer(@Value("${app.security.jwt.hmac-secret}") String hmacSecret) {
        Objects.requireNonNull(hmacSecret,
                "app.security.jwt.hmac-secret property dev-mode uchun majburiy");
        byte[] keyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "HS256 secret kamida 32 byte bo'lishi shart, hozir: "
                            + keyBytes.length + " byte");
        }
        this.hmacKey = keyBytes;
    }

    /**
     * Berilgan userId uchun HS256 JWT chiqaradi.
     *
     * @param userId AppUser UUID — {@code sub} claim sifatida yoziladi
     * @param ttl    token amal qilish muddati
     * @return serialized JWT (Bearer header'da to'g'ridan-to'g'ri ishlatish uchun)
     */
    public String issueToken(UUID userId, Duration ttl) {
        Objects.requireNonNull(userId, "userId null bo'lishi mumkin emas");
        Objects.requireNonNull(ttl, "ttl null bo'lishi mumkin emas");

        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        // `iss` claim ataylab o'rnatilmagan — NimbusJwtDecoder uni
        // java.net.URL deb parse qiladi va plain string yoki URN reject
        // qilinadi. JwtActorConverter (Phase 125) faqat `sub` va ixtiyoriy
        // `telegram_user_id` claim'larini talab qiladi.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .build();

        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signed.sign(new MACSigner(hmacKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException("HS256 JWT sign muvaffaqiyatsiz", ex);
        }
        return signed.serialize();
    }
}
