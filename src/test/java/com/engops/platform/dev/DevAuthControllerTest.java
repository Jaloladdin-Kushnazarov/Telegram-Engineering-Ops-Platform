package com.engops.platform.dev;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 211 — DevAuthController @WebMvcTest. Dev-mode property test'da
 * o'rnatilgan, shu sababli controller bean yaratiladi va endpoint'lar
 * permitAll matcher orqali authentication'siz access mumkin.
 */
@WebMvcTest(DevAuthController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
@TestPropertySource(properties = {
        "app.security.dev-mode.enabled=true",
        "app.security.jwt.hmac-secret=test-only-secret-padded-to-be-32-bytes-long-enough"
})
class DevAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DevTokenIssuer devTokenIssuer;

    @Test
    void info_returnsDevModeTrueWithFixedUuids() throws Exception {
        mockMvc.perform(get("/api/dev/auth/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devMode").value(true))
                .andExpect(jsonPath("$.bootstrapAdminUserId")
                        .value(DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID.toString()))
                .andExpect(jsonPath("$.firstTenantId")
                        .value(DevBootstrapInitializer.BOOTSTRAP_TENANT_ID.toString()));
    }

    @Test
    void bootstrapAdminToken_returnsTokenFromIssuer() throws Exception {
        when(devTokenIssuer.issueToken(
                eq(DevBootstrapInitializer.BOOTSTRAP_ADMIN_USER_ID), any()))
                .thenReturn("fake.jwt.token");

        String body = mockMvc.perform(get("/api/dev/auth/bootstrap-admin-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("token").asText()).isEqualTo("fake.jwt.token");
    }

    @Test
    void tokenForUser_acceptsArbitraryUserId() throws Exception {
        UUID arbitrary = UUID.randomUUID();
        when(devTokenIssuer.issueToken(eq(arbitrary), any()))
                .thenReturn("arbitrary.jwt.token");

        mockMvc.perform(get("/api/dev/auth/token")
                        .queryParam("userId", arbitrary.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("arbitrary.jwt.token"));
    }

    @Test
    void endpointsAccessible_withoutAuth() throws Exception {
        // /api/dev/** permitAll bo'lgani sababli — JWT kerak emas
        mockMvc.perform(get("/api/dev/auth/info"))
                .andExpect(status().isOk());
    }
}
