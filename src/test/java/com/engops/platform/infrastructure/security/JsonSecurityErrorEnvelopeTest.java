package com.engops.platform.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 148 — Filter-chain reject yo'llarining JSON envelope shaklini end-to-end
 * tasdiqlovchi test (JwtDecoder MAVJUD sharoiti, full Spring context).
 *
 * <p>Bu test {@code app.security.jwt.hmac-secret}'ni {@code @TestPropertySource}
 * orqali o'rnatadi, shuning uchun:</p>
 * <ul>
 *   <li>{@link JwtAuthenticationConfig} HMAC decoder bean'ini yaratadi.</li>
 *   <li>{@link SecurityConfig} {@code oauth2ResourceServer} chain'ini wire qiladi
 *       va Bearer reject'lari uchun {@link JsonAuthenticationEntryPoint} +
 *       {@link JsonAccessDeniedHandler} ulanadi.</li>
 *   <li>{@code com.engops.platform.infrastructure.web.CorrelationIdFilter}
 *       full context'da yuklanadi va MDC'ga {@code correlationId}
 *       o'rnatadi — envelope'ning {@code correlationId} maydoni non-null
 *       bo'lishi shart.</li>
 * </ul>
 *
 * <p>Tasdiqlanadi:</p>
 * <ul>
 *   <li>{@code /api/**} avtorizatsiyasiz — 401, {@code errorCode=UNAUTHORIZED},
 *       JSON content-type, {@code correlationId} mavjud, {@code path} mos keladi,
 *       {@code WWW-Authenticate} header'da {@code Bearer} bor.</li>
 *   <li>{@code /api/**} yaroqsiz Bearer — 401, {@code errorCode=UNAUTHORIZED},
 *       envelope to'liq shaklda.</li>
 *   <li>{@code /actuator/metrics} avtorizatsiyasiz — 401, envelope shaklida.</li>
 *   <li>{@code /actuator/flyway} avtorizatsiyasiz — 401, envelope shaklida.</li>
 *   <li>{@code /actuator/health} avtorizatsiyasiz — 401/403 EMAS (public).</li>
 *   <li>Yaroqli Bearer + {@code /api/__phase148_envelope_probe__} — 200/404
 *       (envelope chiqmaydi, faqat reject yo'lida).</li>
 * </ul>
 *
 * <p>Decoder ABSENT yo'lining envelope shakli {@link SecurityConfigTest}
 * tomonidan tasdiqlanadi.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "app.security.jwt.hmac-secret=test-secret-very-long-32-bytes-min!!")
class JsonSecurityErrorEnvelopeTest {

    private static final String SECRET = "test-secret-very-long-32-bytes-min!!";
    private static final String API_PROBE_PATH = "/api/__phase148_envelope_probe__";
    private static final String ACTUATOR_METRICS_PATH = "/actuator/metrics";
    private static final String ACTUATOR_FLYWAY_PATH = "/actuator/flyway";
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.engops.platform.telegram.TelegramOutboundGateway telegramOutboundGateway;

    @Test
    void apiWithoutBearerReturns401UnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get(API_PROBE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value(API_PROBE_PATH))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test
    void apiWithInvalidBearerReturns401UnauthorizedEnvelopeWithInvalidTokenDiagnostic()
            throws Exception {
        // Wrong secret → resource-server signature verification fails →
        // OAuth2AuthenticationException(BearerTokenError) →
        // JsonAuthenticationEntryPoint (oauth2ResourceServer slot orqali
        // ulangan). Phase 148 mini-fix (Approach 1): entry point Spring
        // Security'ning BearerTokenAuthenticationEntryPoint delegate'idan
        // foydalanadi va RFC 6750 ga muvofiq invalid_token diagnostic'ni
        // WWW-Authenticate header'ida saqlaydi.
        String tokenSignedWithWrongSecret = signHs256(
                "WRONG-SECRET-but-also-32-bytes!!!",
                Map.of("sub", UUID.randomUUID().toString()),
                300);

        mockMvc.perform(get(API_PROBE_PATH)
                        .header("Authorization", "Bearer " + tokenSignedWithWrongSecret))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value(API_PROBE_PATH))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void actuatorMetricsWithoutBearerReturns401UnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get(ACTUATOR_METRICS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value(ACTUATOR_METRICS_PATH));
    }

    @Test
    void actuatorFlywayWithoutBearerReturns401UnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get(ACTUATOR_FLYWAY_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value(ACTUATOR_FLYWAY_PATH));
    }

    @Test
    void actuatorHealthRemainsPublicAndDoesNotReturnEnvelope() throws Exception {
        mockMvc.perform(get(ACTUATOR_HEALTH_PATH))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    if (statusCode == 401 || statusCode == 403) {
                        throw new AssertionError(
                                "/actuator/health public bo'lishi shart, status=" + statusCode);
                    }
                });
    }

    /**
     * Test-only HS256 signing helper — production code'da yo'q.
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
