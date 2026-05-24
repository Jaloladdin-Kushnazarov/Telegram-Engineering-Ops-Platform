package com.engops.platform.identity.auth;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 218a — TelegramLoginController @WebMvcTest.
 */
@WebMvcTest(TelegramLoginController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class TelegramLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TelegramLoginService loginService;

    private String validJsonPayload() throws Exception {
        TelegramLoginPayload payload = new TelegramLoginPayload(
                100_000_001L, "Davron", null, null, null,
                Instant.now().getEpochSecond(), "abcdef");
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    void post_validPayload_returns200WithToken() throws Exception {
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("eyJhbGciOiJIUzI1NiJ9.fake.jwt");

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("eyJhbGciOiJIUzI1NiJ9.fake.jwt"));
    }

    @Test
    void post_invalidHash_returns401() throws Exception {
        doThrow(new TelegramLoginException("Imzo noto'g'ri yoki muddati o'tgan"))
                .when(loginService).authenticate(any(TelegramLoginPayload.class));

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void post_disabledServer_returns401() throws Exception {
        doThrow(new TelegramLoginException("Telegram login bu serverda sozlanmagan"))
                .when(loginService).authenticate(any(TelegramLoginPayload.class));

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("sozlanmagan")));
    }

    @Test
    void post_emptyBody_returnsErrorStatus() throws Exception {
        // Bo'sh body @RequestBody parse fail qiladi. Platform error
        // handler (Phase 148) buni 500 yoki 400 sifatida qaytarishi
        // mumkin — har holatda 200 OK qaytmasligini tasdiqlaymiz.
        int status = mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(status).isGreaterThanOrEqualTo(400);
    }

    @Test
    void post_securityConfig_permitsAnonymous() throws Exception {
        // No JWT — SecurityConfig matcher /api/auth/telegram-login permitAll.
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("token");

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isOk());
    }

    @Test
    void post_returnsJsonContentType() throws Exception {
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("token");

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void post_errorResponse_doesNotLeakStackTrace() throws Exception {
        doThrow(new TelegramLoginException("Hash verification fail"))
                .when(loginService).authenticate(any(TelegramLoginPayload.class));

        mockMvc.perform(post("/api/auth/telegram-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJsonPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com.engops"))));
    }
}
