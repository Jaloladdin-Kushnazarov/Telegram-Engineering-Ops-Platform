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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 126 — oauth2ResourceServer wiring round-trip test.
 *
 * <p>Tasdiqlanadi:</p>
 * <ul>
 *   <li>HMAC secret property o'rnatilganda JwtDecoder bean mavjud va
 *       SecurityConfig {@code oauth2ResourceServer} chain'ini wire qiladi</li>
 *   <li>Bearer-siz so'rov hamon permitAll bilan o'tadi (401/403 emas)</li>
 *   <li>Yaroqli Bearer JWT decoded bo'ladi va {@link AuthenticatedActor}
 *       principal SecurityContext'da paydo bo'ladi</li>
 *   <li>Token authorities bo'sh — identity-only kontrakt</li>
 *   <li>Yaroqsiz Bearer (noto'g'ri imzo) 401 qaytaradi (resource-server
 *       reject xulqi)</li>
 * </ul>
 *
 * <p>Test debug-only controller (TestActorIntrospectionController) ishlatadi —
 * SecurityContext'dagi authentication holatini JSON sifatida qaytaradi. Bu
 * controller faqat ushbu test class'i ichida {@code @TestConfiguration}
 * orqali yuklanadi va production code'ga ta'sir qilmaydi.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "app.security.jwt.hmac-secret=test-secret-very-long-32-bytes-min!!")
class JwtResourceServerWiringTest {

    private static final String SECRET = "test-secret-very-long-32-bytes-min!!";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.engops.platform.telegram.TelegramOutboundGateway telegramOutboundGateway;

    @Test
    void unauthenticatedRequestStillPermitted() throws Exception {
        mockMvc.perform(get("/__phase126_probe__/whoami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void validBearerTokenPopulatesAuthenticatedActor() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = signHs256(SECRET,
                Map.of("sub", userId.toString(),
                        "telegram_user_id", 999L),
                300);

        mockMvc.perform(get("/__phase126_probe__/whoami")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.principalType").value("AuthenticatedActor"))
                .andExpect(jsonPath("$.appUserId").value(userId.toString()))
                .andExpect(jsonPath("$.telegramUserId").value(999))
                .andExpect(jsonPath("$.authorities").isEmpty());
    }

    @Test
    void invalidBearerTokenIsRejectedByResourceServer() throws Exception {
        // Wrong secret → signature verification fails → 401
        String tokenSignedWithWrongSecret = signHs256(
                "WRONG-SECRET-but-also-32-bytes!!!",
                Map.of("sub", UUID.randomUUID().toString()),
                300);

        mockMvc.perform(get("/__phase126_probe__/whoami")
                        .header("Authorization", "Bearer " + tokenSignedWithWrongSecret))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test-only controller — SecurityContext holatini JSON sifatida ochadi.
     * Faqat shu test class'i ichida yuklanadi, production'ga ta'sir qilmaydi.
     */
    @RestController
    static class TestActorIntrospectionController {
        @GetMapping("/__phase126_probe__/whoami")
        public Map<String, Object> whoami() {
            Map<String, Object> result = new HashMap<>();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                result.put("authenticated", false);
                return result;
            }
            result.put("authenticated", true);
            Object principal = auth.getPrincipal();
            result.put("principalType",
                    principal == null ? null : principal.getClass().getSimpleName());
            if (principal instanceof AuthenticatedActor actor) {
                result.put("appUserId", actor.appUserId().toString());
                result.put("telegramUserId", actor.telegramUserId());
            }
            result.put("authorities",
                    auth.getAuthorities().stream().map(Object::toString).toList());
            return result;
        }
    }

    @TestConfiguration
    static class ProbeControllerConfig {
        @Bean
        TestActorIntrospectionController testActorIntrospectionController() {
            return new TestActorIntrospectionController();
        }
    }

    /**
     * Test-only HS256 signing helper — production code'da JWT signing yo'q
     * (Phase 125 mini-fix bilan ko'chirilgan).
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
