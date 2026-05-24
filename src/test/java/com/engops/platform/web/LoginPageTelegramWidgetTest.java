package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 218b — /web/login sahifasida Telegram Widget conditional
 * rendering testlari.
 *
 * <p>Ikkita stsenariy ikki nested test klassda — har biri o'z
 * {@code @TestPropertySource} bilan bot-username'ning bo'sh va
 * to'ldirilgan holatini ifodalaydi.</p>
 */
class LoginPageTelegramWidgetTest {

    @Nested
    @WebMvcTest(WebController.class)
    @Import({SecurityConfig.class, SecurityWebMvcConfig.class})
    @TestPropertySource(properties = {
            "app.security.telegram.bot-username=engops_platform_bot"
    })
    class WhenBotUsernameConfigured {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private TenantConfigQueryService tenantConfigQueryService;

        @Test
        void loginPage_includesTelegramWidgetScript() throws Exception {
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "https://telegram.org/js/telegram-widget.js")))
                    .andExpect(content().string(containsString(
                            "engops_platform_bot")));
        }

        @Test
        void loginPage_usesTelegramRedirectMode() throws Exception {
            // Phase 218d — widget redirect mode'ga o'tdi: data-auth-url
            // Telegram'ni /web/login/telegram-callback'ga query params bilan
            // yo'naltiradi. data-onauth callback mode (Phase 218b/c) olib
            // tashlandi — ngrok orqali silent fail qilardi.
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "data-auth-url=\"/web/login/telegram-callback\"")))
                    .andExpect(content().string(not(containsString(
                            "data-onauth"))));
        }

        @Test
        void loginPage_includesTelegramHeader() throws Exception {
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Telegram orqali kirish")));
        }
    }

    @Nested
    @WebMvcTest(WebController.class)
    @Import({SecurityConfig.class, SecurityWebMvcConfig.class})
    @TestPropertySource(properties = {
            "app.security.telegram.bot-username="
    })
    class WhenBotUsernameEmpty {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private TenantConfigQueryService tenantConfigQueryService;

        @Test
        void loginPage_omitsTelegramWidgetScript() throws Exception {
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(
                            "telegram-widget.js"))));
        }

        @Test
        void loginPage_omitsTelegramHeader() throws Exception {
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(
                            "Telegram orqali kirish"))));
        }

        @Test
        void loginPage_devTokenButton_stillPresent() throws Exception {
            // Phase 211 fallback: bot-username bo'sh bo'lsa, dev token
            // tugmasi hali ham mavjud (auth.js JS orqali ko'rsatadi).
            mockMvc.perform(get("/web/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "id=\"dev-token-button\"")));
        }
    }
}
