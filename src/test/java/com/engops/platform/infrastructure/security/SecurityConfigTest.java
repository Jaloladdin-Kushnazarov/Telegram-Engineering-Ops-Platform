package com.engops.platform.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig integratsiya testlari (Phase 124 baseline + Phase 146 posture
 * + Phase 148 JSON envelope).
 *
 * <p>Bu test class'i {@code @SpringBootTest} bilan to'liq application context'ni
 * yuklaydi va <em>JwtDecoder bean YOQ</em> sharoitida ishlaydi
 * (test profilida {@code app.security.jwt.*} property'lari o'rnatilmagan).
 * Demak {@link SecurityConfig} {@code oauth2ResourceServer} chain'ini wire
 * qilmaydi, lekin Phase 148'dan keyin {@code http.exceptionHandling}
 * {@link JsonAuthenticationEntryPoint}'ni default fallback sifatida ulaydi.
 * Shu sababli himoyalangan {@code /api/**} va {@code /actuator/**} (health'dan
 * tashqari) avtorizatsiyasiz so'rovlari uchun <strong>401 + UNAUTHORIZED
 * envelope</strong> qaytariladi (avvalgi 403 + bo'sh-body fallback'i o'rnini
 * bosdi).</p>
 *
 * <p>{@code @AutoConfigureMockMvc} ishlatiladi — bu auto-configured filter
 * zanjirini (jumladan {@code CorrelationIdFilter}) MockMvc'ga ulaydi, shuning
 * uchun envelope'ning {@code correlationId} maydonini ham tasdiqlash mumkin.</p>
 *
 * <p>JwtDecoder MAVJUD bo'lgan sharoit ({@code app.security.jwt.hmac-secret}
 * @TestPropertySource bilan o'rnatilgan) {@link JwtResourceServerWiringTest}
 * va {@link JsonSecurityErrorEnvelopeTest} tomonidan sinab ko'riladi:
 * resource-server chain'i ham yangi {@link JsonAuthenticationEntryPoint} +
 * {@link JsonAccessDeniedHandler} bilan wire qilinadi.</p>
 *
 * <p>Tasdiqlanadi (Phase 148 posture, decoder ABSENT):</p>
 * <ul>
 *   <li>Application context SecurityConfig bilan muvaffaqiyatli yuklanadi.</li>
 *   <li>{@code SecurityFilterChain} bean mavjud.</li>
 *   <li>{@code /api/**} no JWT decoder fallback'da <strong>401 + UNAUTHORIZED
 *       envelope</strong> qaytaradi (anonymous principal {@code authenticated()}
 *       qoidasi orqali rad etiladi → {@code ExceptionTranslationFilter}
 *       {@link JsonAuthenticationEntryPoint}'ni chaqiradi).</li>
 *   <li>{@code /actuator/health/**} no JWT — 401/403 emas (k8s liveness/readiness
 *       probelar uchun ochiq).</li>
 *   <li>{@code /actuator/metrics} no JWT — 401 + UNAUTHORIZED envelope.</li>
 *   <li>{@code /actuator/flyway} no JWT — 401 + UNAUTHORIZED envelope.</li>
 *   <li>{@code /api/**} bo'lmagan path (masalan {@code /__phase124_probe__})
 *       hamon {@code permitAll} (anyRequest) bilan ishlaydi va auth challenge
 *       qaytarmaydi.</li>
 *   <li>{@code formLogin} disabled — {@code /login} GET 302 redirect emas.</li>
 *   <li>POST CSRF disabled — non-{@code /api/**} path uchun 401/403 qaytarmaydi.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @MockBean
    private com.engops.platform.telegram.TelegramOutboundGateway telegramOutboundGateway;

    @Test
    void contextLoadsWithSecurityConfig() {
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void noJwtDecoderFallbackForApiReturnsUnauthorizedEnvelope() throws Exception {
        // Phase 146 + Phase 148 + no JwtDecoder bean: oauth2ResourceServer
        // chain wire qilinmaydi, lekin http.exceptionHandling default
        // JsonAuthenticationEntryPoint'ni ulaydi. Anonymous principal
        // /api/** authenticated qoidasini buzadi → ExceptionTranslationFilter
        // entry point'ni chaqiradi → 401 + UNAUTHORIZED + ApiErrorResponse
        // envelope (errorCode/correlationId/path) qaytariladi.
        mockMvc.perform(get("/api/__phase148_probe__"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value("/api/__phase148_probe__"));
    }

    @Test
    void actuatorHealthWithoutAuthenticationIsPublic() throws Exception {
        // Kubernetes liveness/readiness probelar uchun ochiq qoldirildi.
        // Status 401/403 bo'lmasligi shart (200 yoki health'ga qarab boshqa
        // health-state status'lar bo'lishi mumkin).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("/actuator/health public bo'lishi shart")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void actuatorMetricsWithoutAuthenticationReturnsUnauthorizedEnvelope() throws Exception {
        // Phase 146 actuator gate + Phase 148 envelope: /actuator/health'dan
        // tashqari hammasi authenticated. JwtDecoder yo'q sharoitida
        // JsonAuthenticationEntryPoint 401 + UNAUTHORIZED envelope qaytaradi.
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.path").value("/actuator/metrics"));
    }

    @Test
    void actuatorFlywayWithoutAuthenticationReturnsUnauthorizedEnvelope() throws Exception {
        // Phase 146 + Phase 148: /actuator/flyway production'da DB schema
        // migration history leak qilmasligi uchun authenticated qilingan;
        // envelope shaklida 401 UNAUTHORIZED qaytariladi.
        mockMvc.perform(get("/actuator/flyway"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void anyRequestPermitAllAllowsNonApiPathWithoutChallenge() throws Exception {
        // Phase 146 dan keyin ham /api/**, /actuator/** dan tashqari path'lar
        // anyRequest().permitAll() ostida qoladi. MVC dispatcher 404 qaytaradi
        // va Spring Security auth challenge yuklamaydi.
        mockMvc.perform(get("/__phase124_probe__"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("Non-/api/** path: permitAll, 401/403 bo'lmasligi shart")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void csrfDisabledForNonApiPostWithoutAuthChallenge() throws Exception {
        // Non-/api/** POST — CSRF disabled bo'lgani uchun 403 qaytarilmaydi.
        // Test invariant: yo'q endpoint uchun 404/405 bo'ladi (filter chain
        // anyRequest permitAll'dan o'tkazadi va MVC handle qiladi).
        mockMvc.perform(post("/__phase124_probe__"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("CSRF disabled + non-/api/** POST: 401/403 bo'lmasligi shart")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void formLoginPathIsNotConfigured() throws Exception {
        // Spring Security default formLogin /login redirect'ni o'rnatadi.
        // SecurityConfig formLogin disable qilgani sabab — /login GET 302
        // redirect emas. Asosiy maqsad: 302 redirect'siz xulq.
        mockMvc.perform(get("/login"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("formLogin disabled: /login redirect bo'lmasligi kerak")
                            .isNotIn(302);
                });
    }
}
