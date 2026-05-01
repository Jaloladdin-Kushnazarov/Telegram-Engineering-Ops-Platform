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
 * Phase 124 — SecurityConfig skeleton testlari.
 *
 * Tasdiqlanadi:
 * - Application context SecurityConfig bilan muvaffaqiyatli yuklanadi
 * - SecurityFilterChain bean mavjud (default Spring Boot secure chain'i emas)
 * - Existing endpoint'larga unauthenticated so'rovlar 401/403 olmaydi —
 *   permitAll xulqi ishlamoqda
 * - Form login redirect / HTTP Basic challenge bartaraf etilgan
 *
 * Bu test {@code @SpringBootTest} ishlatadi (kichik ham bo'lsa) chunki
 * SecurityConfig integratsiyasi to'liq application context'ni talab qiladi —
 * @WebMvcTest slice'i Spring Security default'ini boshqacha yuklaydi.
 *
 * Mock'lar Spring Boot test profile'ining DataSource majburiyatlarini
 * minimallashtirish uchun joriy emas — embedded H2 + Flyway prod baseline'ni
 * to'liq ko'taradi va test holati boshqa SpringBootTest test'lari bilan bir xil.
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
    void permitAllChainAllowsUnauthenticatedGetWithoutBasicChallenge() throws Exception {
        MockMvc mockMvc = mvc();

        // Mavjud bo'lmagan endpoint — Spring Security uni 401/403 qilmasligi kerak
        // (permitAll). 404 esa MVC dispatcher'idan keladi — bu kutilgan natija va
        // hech qanday auth challenge response'da bo'lmaydi.
        mockMvc.perform(get("/api/__phase124_probe__"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("permitAll: 401/403 bo'lmasligi kerak")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void permitAllChainAllowsUnauthenticatedPostWithoutCsrfBlock() throws Exception {
        MockMvc mockMvc = mvc();

        // Mavjud bo'lmagan POST endpoint — CSRF disabled, shuning uchun 403
        // (CSRF reject) qaytarilmasligi kerak.
        mockMvc.perform(post("/api/__phase124_probe__"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    assertThat(statusCode)
                            .as("CSRF disabled + permitAll: 401/403 bo'lmasligi kerak")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void formLoginPathIsNotConfigured() throws Exception {
        MockMvc mockMvc = mvc();

        // Spring Security default formLogin /login redirect'ni o'rnatadi.
        // SecurityConfig formLogin disable qilgani sabab — /login GET 404 yoki
        // hech bo'lmaganda redirect emas. Asosiy maqsad: 302 redirect'siz xulq.
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
