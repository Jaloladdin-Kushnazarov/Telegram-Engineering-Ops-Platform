package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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

    // ========== /web/dashboard (P208/P209B) ==========

    @Test
    void dashboard_withTenantId_rendersAllThreeHtmxCards() throws Exception {
        // Phase 209B — dashboard cards issue hx-get to /web/api/analytics/by-*.
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/web/dashboard").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/web/api/analytics/by-status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/web/api/analytics/by-type")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/web/api/analytics/by-severity")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("card skeleton")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-trigger=\"load")));
    }

    @Test
    void dashboard_withoutTenantId_rendersNoTenantSelectedState() throws Exception {
        // Phase 209B D5 — no ?tenantId param → empty state with helpful message.
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No tenant selected")))
                // Skeleton cards must NOT be rendered without a tenant.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/web/api/analytics/by-status"))));
    }

    @Test
    void dashboard_endpointReachable_withoutJwt() throws Exception {
        // Phase 208 D1 — anonymous at the shell level (/web/** permitAll).
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    void dashboard_baseLayoutHasActiveNavIndicator() throws Exception {
        // Phase 209B — Dashboard link receives "active" class via th:classappend.
        mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                // The Dashboard <a> tag must carry the active class (rendered as
                // class="active" — note attribute may include leading space from
                // th:classappend, so we check for "active" presence near the
                // Dashboard link).
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "active\">Dashboard")));
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
    void allPages_includeFooterBrandMarker() throws Exception {
        // Phase 209B base.html updated footer text to "v0.1" (replaced
        // "Phase 208" marker). Assert the stable brand string still present.
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "Engineering Ops Platform")));
        }
    }

    @Test
    void healthPage_baseLayoutHasActiveNavIndicator_onHealthLink() throws Exception {
        // Phase 209B — Health link receives "active" class when on /web/health.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "active\">Health")));
    }

    // ========== Phase 210 — /web/work-items + tenant selector ==========

    @Test
    void workItems_withTenantId_rendersTableWithHtmxLoad() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/web/work-items").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/web/api/work-items/list")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "hx-trigger=\"load")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"data-table\"")));
    }

    @Test
    void workItems_withoutTenantId_rendersNoTenantHint() throws Exception {
        mockMvc.perform(get("/web/work-items"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No tenant selected")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/web/api/work-items/list"))));
    }

    @Test
    void workItems_baseLayoutHasActiveNavIndicator_onWorkItemsLink() throws Exception {
        mockMvc.perform(get("/web/work-items"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "active\">Work items")));
    }

    @Test
    void allPages_includeTenantSelectWrapper() throws Exception {
        // Phase 210 — base.html nav must contain the tenant-select wrapper
        // div for the HTMX-loaded dropdown across all pages.
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard", "/web/work-items"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "id=\"tenant-select-wrapper\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "/web/api/tenants/options")));
        }
    }

    @Test
    void allPages_navIncludesWorkItemsLink() throws Exception {
        // Phase 210 — nav-links extended with "Work items" link.
        for (String path : new String[]{"/web/health", "/web/login", "/web/dashboard"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            ">Work items</a>")));
        }
    }
}
