package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 218c — base.html topnav'da yangi {@code user-display} span va
 * Login/Logout link mavjudligi.
 *
 * <p>Element o'zi server-rendered (mavjud), visibility {@code auth.js}
 * {@code updateAuthNav()} tomonidan client-side boshqariladi (test
 * server-rendered HTML'da {@code style="display: none;"} default'ni
 * tasdiqlaydi).</p>
 */
@WebMvcTest(WebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class LoginPageNavTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantConfigQueryService tenantConfigQueryService;

    @Test
    void loginPage_topnav_includesUserDisplaySpan() throws Exception {
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"user-display\"")))
                .andExpect(content().string(containsString(
                        "class=\"user-display\"")));
    }

    @Test
    void loginPage_topnav_includesLoginAndLogoutLinks() throws Exception {
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"login-link\"")))
                .andExpect(content().string(containsString(
                        "id=\"logout-link\"")));
    }

    @Test
    void loginPage_userDisplay_serverRenderedHidden() throws Exception {
        // Server HTML default: display: none. auth.js updateAuthNav
        // client-side ko'rsatadi (JWT mavjud bo'lsa).
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"user-display\" class=\"user-display\" style=\"display: none;\"")));
    }

    @Test
    void dashboardPage_topnav_includesUserDisplaySpan() throws Exception {
        // base.html barcha sahifalarda inheritd, /web/dashboard'da ham bor.
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"user-display\"")));
    }
}
