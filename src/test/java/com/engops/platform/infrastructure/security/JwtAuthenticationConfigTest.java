package com.engops.platform.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtAuthenticationConfig} conditional bean tekshiruvi (Phase 125 + Phase 137).
 *
 * <p>Testlar {@link ApplicationContextRunner} orqali yoziladi — har bir test
 * faqat {@link JwtAuthenticationConfig} klassini yuklab, property'larni
 * scenariyiga qarab o'zgartiradi va {@link JwtDecoder} bean holati hamda
 * startup xatoligini tekshiradi. Bu yondashuv to'liq {@code @SpringBootTest}
 * bootstrap'ini va {@code TelegramOutboundGateway} kabi unrelated mock'larni
 * talab qilmaydi.</p>
 *
 * <p>Tekshiriladigan rejimlar:</p>
 * <ul>
 *   <li>Hech qanday property → {@link JwtDecoder} bean yo'q</li>
 *   <li>Faqat {@code hmac-secret} → HMAC HS256 decoder + round-trip decode +
 *       {@link JwtActorConverter} mapping</li>
 *   <li>Faqat {@code jwk-set-uri} → JWK URL asosidagi decoder (lazy fetch)</li>
 *   <li>Faqat {@code issuer-uri} → OIDC discovery orqali decoder
 *       (lokal embedded {@link HttpServer} mock OIDC end-point bilan)</li>
 *   <li>Bir nechta rejim bir vaqtda → fail-fast {@link IllegalStateException}</li>
 *   <li>Bo'sh property qiymatlari → decoder yaratilmaydi</li>
 * </ul>
 *
 * <p><strong>Phase 125 mini-fix:</strong> JWT imzolash helper'i production
 * code'da yo'q — bu testning private static metodi sifatida saqlanadi.
 * Production resource-server roli faqat tasdiqlash (decode), token chiqarish
 * emas.</p>
 */
class JwtAuthenticationConfigTest {

    private static final String SECRET = "test-secret-very-long-32-bytes-min!!";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtAuthenticationConfig.class);

    // ========== No-property baseline ==========

    @Test
    void noJwtPropertyConfigured_noJwtDecoderBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
        });
    }

    // ========== HMAC mode ==========

    @Test
    void hmacSecretConfigured_jwtDecoderBeanCreated() {
        runner.withPropertyValues("app.security.jwt.hmac-secret=" + SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtDecoder.class))
                            .isInstanceOf(NimbusJwtDecoder.class);
                });
    }

    @Test
    void hmacDecoder_decodesValidHs256Token() {
        runner.withPropertyValues("app.security.jwt.hmac-secret=" + SECRET)
                .run(context -> {
                    JwtDecoder decoder = context.getBean(JwtDecoder.class);
                    UUID userId = UUID.randomUUID();
                    String token = signHs256(SECRET,
                            Map.of("sub", userId.toString(),
                                    "telegram_user_id", 999L),
                            300);

                    Jwt jwt = decoder.decode(token);

                    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
                    assertThat(jwt.<Long>getClaim("telegram_user_id")).isEqualTo(999L);
                });
    }

    @Test
    void hmacDecoderOutput_mappedByJwtActorConverter() {
        runner.withPropertyValues("app.security.jwt.hmac-secret=" + SECRET)
                .run(context -> {
                    JwtDecoder decoder = context.getBean(JwtDecoder.class);
                    UUID userId = UUID.randomUUID();
                    String token = signHs256(SECRET,
                            Map.of("sub", userId.toString(),
                                    "telegram_user_id", 555L),
                            300);

                    Jwt jwt = decoder.decode(token);
                    AuthenticatedActor actor = (AuthenticatedActor)
                            new JwtActorConverter().convert(jwt).getPrincipal();

                    assertThat(actor.appUserId()).isEqualTo(userId);
                    assertThat(actor.telegramUserId()).isEqualTo(555L);
                });
    }

    // ========== JWK Set URI mode ==========

    @Test
    void jwkSetUriConfigured_jwtDecoderBeanCreatedLazily() {
        // NimbusJwtDecoder.withJwkSetUri(...) lazy-fetch — bean creation paytida
        // tarmoqqa murojaat qilmaydi. URL ataylab unreachable: faqat bean
        // yaratilganligini tekshiramiz, decode() chaqirmaymiz.
        runner.withPropertyValues(
                        "app.security.jwt.jwk-set-uri=https://jwks.example.test/keys.json")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtDecoder.class))
                            .isInstanceOf(NimbusJwtDecoder.class);
                });
    }

    // ========== Issuer URI mode (OIDC discovery via local mock server) ==========

    @Test
    void issuerUriConfigured_jwtDecoderBeanCreatedViaOidcDiscovery() throws Exception {
        // JwtDecoders.fromIssuerLocation(...) eager ravishda
        // /.well-known/openid-configuration so'rovini yuboradi, jwks_uri'ni
        // javob ichidan oladi va JWK Set'ni yuklab algoritm aniqlaydi
        // (kamida bitta RSA/EC public key kerak). Lokal embedded HttpServer
        // ikkala endpoint'ni mock qiladi va RSA public key bilan minimal JWK
        // Set qaytaradi — hech qanday tashqi tarmoq yoki real OIDC IdP talab
        // qilinmaydi. Public key faqat JWK Set algoritm tekshiruvini
        // qoniqtirish uchun — hech qanday token bilan signature tekshirilmaydi.
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID("test-key")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        String jwksJson = "{\"keys\":[" + jwk.toJSONString() + "]}";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String issuerUrl = "http://127.0.0.1:" + port;

        server.createContext("/.well-known/openid-configuration", exchange -> {
            String body = String.format(
                    "{\"issuer\":\"%s\",\"jwks_uri\":\"%s/jwks\"}",
                    issuerUrl, issuerUrl);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/jwks", exchange -> {
            byte[] bytes = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            runner.withPropertyValues("app.security.jwt.issuer-uri=" + issuerUrl)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(JwtDecoder.class);
                        assertThat(context.getBean(JwtDecoder.class))
                                .isInstanceOf(NimbusJwtDecoder.class);
                    });
        } finally {
            server.stop(0);
        }
    }

    // ========== Mutually exclusive validation (fail-fast) ==========

    @Test
    void hmacSecretAndIssuerUri_contextFailsWithMutuallyExclusiveError() {
        runner.withPropertyValues(
                        "app.security.jwt.hmac-secret=" + SECRET,
                        "app.security.jwt.issuer-uri=https://issuer.example.test")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("faqat bittasi");
                });
    }

    @Test
    void hmacSecretAndJwkSetUri_contextFailsWithMutuallyExclusiveError() {
        runner.withPropertyValues(
                        "app.security.jwt.hmac-secret=" + SECRET,
                        "app.security.jwt.jwk-set-uri=https://jwks.example.test/keys.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("faqat bittasi");
                });
    }

    @Test
    void issuerUriAndJwkSetUri_contextFailsWithMutuallyExclusiveError() {
        runner.withPropertyValues(
                        "app.security.jwt.issuer-uri=https://issuer.example.test",
                        "app.security.jwt.jwk-set-uri=https://jwks.example.test/keys.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("faqat bittasi");
                });
    }

    // ========== Blank value handling ==========

    @Test
    void blankIssuerUri_doesNotCreateDecoderBean() {
        runner.withPropertyValues("app.security.jwt.issuer-uri=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
    }

    @Test
    void blankJwkSetUri_doesNotCreateDecoderBean() {
        runner.withPropertyValues("app.security.jwt.jwk-set-uri=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
    }

    @Test
    void blankHmacSecret_doesNotCreateDecoderBean() {
        runner.withPropertyValues("app.security.jwt.hmac-secret=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
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
