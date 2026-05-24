package com.engops.platform.dev;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 211 — DevTokenIssuer token sign + decoder round-trip.
 *
 * <p>{@code app.security.dev-mode.enabled=true} va {@code app.security.jwt.hmac-secret}
 * test property orqali o'rnatilgan — dev-mode bean'lari context'ga kiritiladi.
 * H2 in-memory baza (test profile) — bootstrap tests bu klassdan alohida.</p>
 */
@SpringBootTest(classes = com.engops.platform.EngOpsPlatformApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.dev-mode.enabled=true",
        "app.security.jwt.hmac-secret=test-only-secret-padded-to-be-32-bytes-long-enough"
})
class DevTokenIssuerTest {

    @Autowired
    private DevTokenIssuer devTokenIssuer;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issueToken_producesValidJwt_decodableByJwtDecoder() {
        UUID userId = UUID.randomUUID();
        String token = devTokenIssuer.issueToken(userId, Duration.ofMinutes(10));

        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void issueToken_includesExpectedClaims() throws ParseException {
        UUID userId = UUID.randomUUID();
        String token = devTokenIssuer.issueToken(userId, Duration.ofMinutes(5));

        SignedJWT parsed = SignedJWT.parse(token);
        JWTClaimsSet claims = parsed.getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getExpirationTime().after(claims.getIssueTime())).isTrue();
    }

    @Test
    void issueToken_rejectsShortSecret_atConstruction() {
        assertThatThrownBy(() -> new DevTokenIssuer("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
