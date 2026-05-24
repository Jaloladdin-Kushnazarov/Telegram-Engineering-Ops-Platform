package com.engops.platform.identity.auth;

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
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 218a — Telegram Login muvaffaqiyatli bo'lganida JWT chiqaradi.
 *
 * <p>Pattern Phase 211 {@code DevTokenIssuer}'dan ko'chirildi —
 * {@link JWSAlgorithm#HS256} bilan o'zaro mos sertifikat yo'q (bir xil
 * HMAC secret JwtAuthenticationConfig.hmacJwtDecoder bilan ulashiladi).
 * Token shape JwtActorConverter (Phase 125) talablariga to'liq mos:
 * {@code sub} = AppUser UUID, {@code telegram_user_id} claim (Long).</p>
 *
 * <p><strong>Conditional activation:</strong>
 * {@code app.security.jwt.hmac-secret} property mavjud bo'lganda bean
 * yaratiladi. Property yo'q bo'lsa, butun Telegram login chain
 * (verifier OK, lekin issuer NO BEAN) → service inject failure →
 * Spring fail-fast. Bu istalgan xulq: HMAC decoder bilan birga issuer
 * ham mavjud bo'lishi shart (round-trip integratsiya).</p>
 *
 * <p><strong>Token TTL:</strong> 24 soat — Telegram Login Widget'ning
 * standart sessiya muddati bilan mos. Foydalanuvchi 24 soat o'tgach
 * qayta login qilishi kerak.</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.jwt.hmac-secret")
public class TelegramLoginTokenIssuer {

    static final long TOKEN_TTL_SECONDS = 86_400L;

    private final byte[] hmacKey;

    public TelegramLoginTokenIssuer(
            @Value("${app.security.jwt.hmac-secret}") String hmacSecret) {
        Objects.requireNonNull(hmacSecret,
                "app.security.jwt.hmac-secret property majburiy");
        byte[] keyBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "HS256 secret kamida 32 byte bo'lishi shart, hozir: "
                            + keyBytes.length + " byte");
        }
        this.hmacKey = keyBytes;
    }

    /**
     * Berilgan AppUser uchun JWT chiqaradi. Token'da:
     * <ul>
     *   <li>{@code sub} = appUserId.toString() — JwtActorConverter buni UUID deb parse qiladi</li>
     *   <li>{@code telegram_user_id} = telegramUserId (Long) — JwtActorConverter Optional</li>
     *   <li>{@code iat} = now</li>
     *   <li>{@code exp} = now + 24 hours</li>
     * </ul>
     *
     * <p>{@code iss} ataylab o'rnatilmaydi — NimbusJwtDecoder uni URL deb
     * parse qiladi (Phase 211 dev token issuer'dan o'rganilgan).</p>
     */
    public String issueToken(UUID appUserId, long telegramUserId) {
        Objects.requireNonNull(appUserId, "appUserId null bo'lishi mumkin emas");

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(TOKEN_TTL_SECONDS);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(appUserId.toString())
                .claim("telegram_user_id", telegramUserId)
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
