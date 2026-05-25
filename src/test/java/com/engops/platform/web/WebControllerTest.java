package com.engops.platform.web;

import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 207–212 — {@link WebController} @WebMvcTest.
 *
 * <p>Phase 212 polish: tenant displayName subtitle, nav preserve tenantId,
 * Health page modernized (Phase 208 marker olib tashlandi). WebController
 * endi {@code TenantConfigQueryService} (@MockBean) ga bog'liq.</p>
 */
@WebMvcTest(WebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantConfigQueryService tenantConfigQueryService;

    @BeforeEach
    void setUp() {
        // Default tenant lookup — unknown tenant returns empty.
        // Specific tests override per-UUID below.
        when(tenantConfigQueryService.findTenantById(any(UUID.class)))
                .thenReturn(Optional.empty());
    }

    // ========== /web/health (P207 surface, now layout-aware) ==========

    @Test
    void health_returnsOk_andRendersHealthTemplate() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void health_renderedHtml_containsStatusOK() throws Exception {
        // Phase 212 — info-grid bilan render qilingan; "OK" label info-item
        // ichida. "Status:" plain prefix endi yo'q (info-label "Status"
        // capitalized va separator yo'q). "Status" + "OK" hali ham bor.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")));
    }

    @Test
    void health_renderedHtml_containsSystemStatusHeading() throws Exception {
        // Phase 212 D3 — Phase 208 marker o'chirildi. Yangi h1 "System status".
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "System status")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Phase 208"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">208<"))));
    }

    @Test
    void health_endpointReachable_withoutJwt_dueToPermitAll() throws Exception {
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_rendersActuatorPointer() throws Exception {
        // Phase 212 D3 — Uzbek confirmation sentence olib tashlandi. O'rniga
        // structured info-grid + actuator pointer ko'rsatilgan.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/actuator/health")));
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

    // ========== Phase 220b — create work item modal ==========

    @Test
    void workItems_withTenantId_rendersNewWorkItemButton() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/web/work-items").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "+ New work item")));
    }

    @Test
    void workItems_withTenantId_rendersCreateDialog() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/web/work-items").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"create-work-item-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "showModal()")));
    }

    @Test
    void workItems_withTenantId_rendersTypeAndSeverityOptions() throws Exception {
        // Phase 220b — WebController model'idan workItemTypes + severities
        // dropdown'larda render qilinadi.
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String body = mockMvc.perform(get("/web/work-items")
                        .queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("value=\"BUG\"")
                .contains("value=\"INCIDENT\"")
                .contains("value=\"TASK\"")
                .contains("value=\"CRITICAL\"")
                .contains("value=\"LOW\"");
    }

    @Test
    void workItems_withoutTenantId_doesNotRenderCreateButton() throws Exception {
        // Modal + tugma th:if="${tenantId != null}" bilan himoyalangan.
        mockMvc.perform(get("/web/work-items"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("+ New work item"))));
    }

    @Test
    void workItems_withTenantId_rendersAssigneeColumnHeader() throws Exception {
        // Phase 220a — jadval header'iga Assignee ustuni qo'shildi.
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        mockMvc.perform(get("/web/work-items").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<th>Assignee</th>")));
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

    // ========== Phase 212 — tenant displayName + nav preserve + health modernize ==========

    @Test
    void dashboard_withValidTenantId_rendersTenantNameAndUuid() throws Exception {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Tenant tenant = mock(Tenant.class);
        when(tenant.getName()).thenReturn("Demo Tenant");
        when(tenantConfigQueryService.findTenantById(tenantId))
                .thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/web/dashboard").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>Demo Tenant</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"tenant-id-pill\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        tenantId.toString())));
    }

    @Test
    void dashboard_withUnknownTenantId_rendersUnknownTenantFallback() throws Exception {
        // setUp default: findTenantById returns empty for any UUID
        UUID unknown = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        mockMvc.perform(get("/web/dashboard").queryParam("tenantId", unknown.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>Unknown tenant</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        unknown.toString())));
    }

    @Test
    void workItems_withValidTenantId_rendersTenantNameAndUuid() throws Exception {
        UUID tenantId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Tenant tenant = mock(Tenant.class);
        when(tenant.getName()).thenReturn("Acme Corp");
        when(tenantConfigQueryService.findTenantById(tenantId))
                .thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/web/work-items").queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>Acme Corp</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"tenant-id-pill\"")));
    }

    @Test
    void navLinks_preserveTenantIdAcrossPages() throws Exception {
        // Phase 212 D2 — har 3 nav link tenantId param'ini saqlaydi.
        UUID tenantId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Tenant tenant = mock(Tenant.class);
        when(tenant.getName()).thenReturn("T");
        when(tenantConfigQueryService.findTenantById(tenantId))
                .thenReturn(Optional.of(tenant));

        String body = mockMvc.perform(get("/web/dashboard")
                        .queryParam("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Har bir nav link tenantId ?param bilan ko'rsatilgan bo'lishi shart.
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("/web/dashboard?tenantId=" + tenantId)
                .contains("/web/work-items?tenantId=" + tenantId)
                .contains("/web/health?tenantId=" + tenantId);
    }

    @Test
    void navLinks_noTenantIdParam_whenTenantIdNull() throws Exception {
        // tenantId yo'q bo'lganda Thymeleaf @{(name=null)} param'ni
        // URL'ga qo'shmaydi → nav link'lar toza href'lar bilan keladi.
        // Faqat <a href="..."> attributelarini tekshiramiz — login.html
        // dagi inline JS string literal'lari (`'/web/dashboard?tenantId='`)
        // bu tekshiruvga aralashmasligi uchun.
        String body = mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .contains("href=\"/web/dashboard\"")
                .contains("href=\"/web/work-items\"")
                .contains("href=\"/web/health\"");
    }

    @Test
    void health_modernized_noPhase208Marker() throws Exception {
        // Phase 212 D3 — Phase 208 marker olib tashlandi.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Phase 208"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Platform Web —"))));
    }

    @Test
    void health_rendersSystemStatusGrid() throws Exception {
        // Phase 212 D3 — info-grid bilan 4 ta info-item ko'rsatiladi.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"info-grid\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"info-item\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"info-label\"")));
    }

    @Test
    void health_rendersBuildVersionAndProfile() throws Exception {
        // Phase 212 D3 — 4 ta label: Status, Version, Profile, JWT decoder.
        mockMvc.perform(get("/web/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Version")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Profile")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("JWT decoder")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("v0.1")));
    }
}
