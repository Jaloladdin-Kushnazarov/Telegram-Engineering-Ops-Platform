package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 207 + 208 — {@link WebController} @WebMvcTest.
 *
 * Phase 208 da har 3 endpoint base layout'ni render qiladi (HTMX script,
 * nav, footer). Phase 207'dagi 5 ta health test'i base layout shape
 * o'zgarishi tufayli kichik adjust qilindi (Phase 208 marker), va 8 ta
 * yangi test qo'shildi (login, dashboard, base layout invariants).
 */
@WebMvcTest(WebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== /web/health (P207 surface, now layout-aware) ==========

    @Test
    void health_returnsOk_andRendersHealthTemplate() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void health_renderedHtml_containsStatusOK() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")));
    }

    @Test
    void health_renderedHtml_containsPhase208Marker() throws Exception {
        // Phase 208 updated PHASE constant to "208"; layout wraps
        // "Platform Web — Phase <span>208</span>".
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Platform Web")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">208<")));
    }

    @Test
    void health_endpointReachable_withoutJwt_dueToPermitAll() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_renderedHtml_containsUzbekConfirmationSentence() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Web rendering stack ishlamoqda")));
    }

    // ========== /web/login (P208 NEW) ==========

    @Test
    void login_returnsOk_andContainsJwtPasteForm() throws Exception {
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<textarea")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"jwt-input\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">Login</button>")));
    }

    @Test
    void login_endpointReachable_withoutJwt() throws Exception {
        // Login page must be reachable anonymously — that's the whole point.
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk());
    }

    // ========== /web/dashboard (P208 NEW) ==========

    @Test
    void dashboard_returnsOk_andContainsPlaceholderCards() throws Exception {
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Work items by status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Work items by type")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Work items by severity")));
    }

    @Test
    void dashboard_endpointReachable_withoutJwt() throws Exception {
        // Phase 208 D1 — anonymous at the shell level. Data loading is
        // browser-side JS + JWT; server-side render returns the shell.
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    void dashboard_containsPhase209ChartPlaceholderText() throws Exception {
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Phase 209'da chart bo'ladi")));
    }

    // ========== Base layout invariants (P208) ==========

    @Test
    void allPages_includeHtmxScriptTag() throws Exception {
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "https://unpkg.com/htmx.org@2.0.3")));
        }
    }

    @Test
    void allPages_includeBaseLayoutNav() throws Exception {
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"topnav\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"login-link\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"logout-link\"")));
        }
    }

    @Test
    void allPages_loadAuthJsAndAppCss() throws Exception {
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/web/css/app.css")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/web/js/auth.js")));
        }
    }

    @Test
    void allPages_includeFooterPhaseMarker() throws Exception {
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Phase 208")));
        }
    }
}
