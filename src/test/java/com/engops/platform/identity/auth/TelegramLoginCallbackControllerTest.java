package com.engops.platform.identity.auth;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 218d — TelegramLoginCallbackController @WebMvcTest (redirect mode).
 */
@WebMvcTest(TelegramLoginCallbackController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class TelegramLoginCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelegramLoginService loginService;

    @Test
    void validCallback_returnsSuccessViewWithJwt() throws Exception {
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("eyJhbGciOiJIUzI1NiJ9.fake.jwt");

        mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("auth_date", "1716600000")
                        .param("hash", "abcdef"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/login-callback-success"))
                .andExpect(model().attribute("jwt", "eyJhbGciOiJIUzI1NiJ9.fake.jwt"))
                .andExpect(model().attribute("displayName", "Jaloladdin"));
    }

    @Test
    void validCallback_withLastName_buildsFullDisplayName() throws Exception {
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("token");

        mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("last_name", "Kushnazarov")
                        .param("auth_date", "1716600000")
                        .param("hash", "abcdef"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("displayName", "Jaloladdin Kushnazarov"));
    }

    @Test
    void successView_savesJwtToLocalStorageAndRedirects() throws Exception {
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("eyJhbGciOiJIUzI1NiJ9.fake.jwt");

        mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("auth_date", "1716600000")
                        .param("hash", "abcdef"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("platform.jwt")))
                .andExpect(content().string(containsString("/web/dashboard")));
    }

    @Test
    void invalidLogin_returnsErrorViewWithMessage() throws Exception {
        doThrow(new TelegramLoginException("Imzo noto'g'ri yoki muddati o'tgan"))
                .when(loginService).authenticate(any(TelegramLoginPayload.class));

        mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("auth_date", "1716600000")
                        .param("hash", "badhash"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/login-callback-error"))
                .andExpect(model().attribute("error", "Imzo noto'g'ri yoki muddati o'tgan"));
    }

    @Test
    void callback_missingRequiredParam_returnsErrorStatus() throws Exception {
        // hash majburiy — yo'q bo'lsa Spring MVC 400 qaytaradi.
        int statusCode = mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("auth_date", "1716600000"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(statusCode).isGreaterThanOrEqualTo(400);
    }

    @Test
    void callback_permitsAnonymous() throws Exception {
        // JWT yo'q — /web/** SecurityConfig'da permitAll.
        when(loginService.authenticate(any(TelegramLoginPayload.class)))
                .thenReturn("token");

        mockMvc.perform(get("/web/login/telegram-callback")
                        .param("id", "100000001")
                        .param("first_name", "Jaloladdin")
                        .param("auth_date", "1716600000")
                        .param("hash", "abcdef"))
                .andExpect(status().isOk());
    }
}
