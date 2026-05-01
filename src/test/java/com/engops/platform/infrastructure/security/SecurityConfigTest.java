package com.engops.platform.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig integratsiya testlari (Phase 124 baseline + Phase 146 posture).
 *
 * <p>Bu test class'i {@code @SpringBootTest} bilan to'liq application context'ni
 * yuklaydi va <em>JwtDecoder bean YOQ</em> sharoitida ishlaydi
 * (test profilida {@code app.security.jwt.*} property'lari o'rnatilmagan).
 * Demak {@code SecurityConfig} {@code oauth2ResourceServer} chain'ini wire
 * qilmaydi va Spring Security default fallback ({@code Http403ForbiddenEntryPoint})
 * himoyalangan endpoint'lar uchun 403 qaytaradi.</p>
 *
 * <p>JwtDecoder MAVJUD bo'lgan sharoit ({@code app.security.jwt.hmac-secret}
 * @TestPropertySource bilan o'rnatilgan) {@link JwtResourceServerWiringTest}
 * tomonidan sinab ko'riladi: u missing/invalid Bearer uchun 401
 * ({@code BearerTokenAuthenticationEntryPoint}) qaytarilishini tasdiqlaydi.</p>
 *
 * <p>Tasdiqlanadi (Phase 146 posture):</p>
 * <ul>
 *   <li>Application context SecurityConfig bilan muvaffaqiyatli yuklanadi.</li>
 *   <li>{@code SecurityFilterChain} bean mavjud.</li>
 *   <li>{@code /api/**} no JWT decoder fallback'da 403 qaytaradi
 *       (filter-chain reject; intentional fail-closed posture — operatorlar
 *       JwtDecoder'ni Phase 137 mexanizmi orqali sozlashlari shart).</li>
 *   <li>{@code /actuator/health/**} no JWT — 401/403 emas (k8s liveness/readiness
 *       probelar uchun ochiq).</li>
 *   <li>{@code /actuator/metrics} no JWT — 403 (Phase 146 actuator gate).</li>
 *   <li>{@code /actuator/flyway} no JWT — 403 (Phase 146 actuator gate).</li>
 *   <li>{@code /api/**} bo'lmagan path (masalan {@code /__phase124_probe__})
 *       hamon {@code permitAll} (anyRequest) bilan ishlaydi va auth challenge
 *       qaytarmaydi.</li>
 *   <li>{@code formLogin} disabled — {@code /login} GET 302 redirect emas.</li>
 *   <li>POST CSRF disabled — non-{@code /api/**} path uchun 401/403 qaytarmaydi.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @MockBean
    private com.engops.platform.telegram.TelegramOutboundGateway telegramOutboundGateway;

    @Test
    void contextLoadsWithSecurityConfig() {
        assertThat(webApplicationContext).isNotNull();
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void noJwtDecoderFallbackForApiWithoutAuthentication_isDocumentedByTest() throws Exception {
        // Phase 146 + no JwtDecoder bean: Spring Security
        // Http403ForbiddenEntryPoint default fallback ishlaydi va himoyalangan
        // /api/** so'rovlari 403 qaytaradi (custom JSON envelope'siz).
        // Production deployment'lar JwtDecoder'ni Phase 137 issuer-uri/jwk-set-uri
        // orqali sozlashlari shart — aks holda barcha /api/** endpoint'lar
        // 403 bo'lib qoladi (Phase 144 runbook'da hujjatlangan).
        MockMvc mockMvc = mvc();

        mockMvc.perform(get("/api/__phase146_probe__"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthWithoutAuthenticationIsPublic() throws Exception {
        // Kubernetes liveness/readiness probelar uchun ochiq qoldirildi.
        // Status 401/403 bo'lmasligi shart (200 yoki health'ga qarab boshqa
        // health-state status'lar bo'lishi mumkin).
        MockMvc mockMvc = mvc();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("/actuator/health public bo'lishi shart")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void actuatorMetricsWithoutAuthenticationRequiresAuthentication() throws Exception {
        // Phase 146 actuator gate: /actuator/health'dan tashqari hammasi
        // authenticated. JwtDecoder yo'q bo'lganligi uchun 403 (filter-chain
        // default). Production deployment'da JwtDecoder sozlanganda 401 bo'ladi.
        MockMvc mockMvc = mvc();

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorFlywayWithoutAuthenticationRequiresAuthentication() throws Exception {
        // Phase 146: /actuator/flyway production'da DB schema migration history
        // leak qilmasligi uchun authenticated qilingan.
        MockMvc mockMvc = mvc();

        mockMvc.perform(get("/actuator/flyway"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyRequestPermitAllAllowsNonApiPathWithoutChallenge() throws Exception {
        // Phase 146 dan keyin ham /api/**, /actuator/** dan tashqari path'lar
        // anyRequest().permitAll() ostida qoladi. MVC dispatcher 404 qaytaradi
        // va Spring Security auth challenge yuklamaydi.
        MockMvc mockMvc = mvc();

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
        MockMvc mockMvc = mvc();

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
        MockMvc mockMvc = mvc();

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

    private MockMvc mvc() {
        Filter[] securityFilters = webApplicationContext
                .getBean("springSecurityFilterChain", Filter.class) != null
                ? new Filter[]{webApplicationContext.getBean("springSecurityFilterChain", Filter.class)}
                : new Filter[0];
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilters)
                .build();
    }
}
