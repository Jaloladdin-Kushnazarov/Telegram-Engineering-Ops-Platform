package com.engops.platform.web;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.platform.PlatformTenantQueryService;
import com.engops.platform.platform.PlatformTenantSummary;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 217b — {@link PlatformWebController} @WebMvcTest.
 *
 * <p>Pattern Phase 210 TenantsWebControllerTest pattern bilan parallel:
 * {@code SecurityConfig + SecurityWebMvcConfig} import qilinadi va
 * {@code withActor(UUID)} RequestPostProcessor SecurityContext'ga
 * AuthenticatedActor injects.</p>
 */
@WebMvcTest(PlatformWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class PlatformWebControllerTest {

    private static final UUID ACTOR_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_ID_A =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_ID_B =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformTenantQueryService platformTenantQueryService;

    private static RequestPostProcessor withActor(UUID actorUserId) {
        return request -> {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedActor(actorUserId, null), null, Collections.emptyList());
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            new RequestAttributeSecurityContextRepository().saveContext(ctx, request, null);
            return request;
        };
    }

    private static PlatformTenantSummary summary(UUID id, String name, String slug,
                                                  String timezone, String status) {
        return new PlatformTenantSummary(id, name, slug, timezone, status,
                Instant.parse("2026-03-15T10:00:00Z"),
                Instant.parse("2026-03-15T10:00:00Z"));
    }

    // ========== GET /web/platform/tenants (page) ==========

    @Test
    void getPlatformTenantsPage_returnsBaseLayout() throws Exception {
        mockMvc.perform(get("/web/platform/tenants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Platform tenants")))
                .andExpect(content().string(containsString("class=\"data-table\"")))
                .andExpect(content().string(containsString("hx-get=\"/web/api/platform/tenants\"")));
    }

    @Test
    void getPlatformTenantsPage_includesHtmxHydrationTrigger() throws Exception {
        mockMvc.perform(get("/web/platform/tenants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-trigger=\"load\"")))
                .andExpect(content().string(containsString("Loading tenants")));
    }

    @Test
    void getPlatformTenantsPage_marksPlatformNavActive() throws Exception {
        // base.html: activeNav == 'platform' → nav link 'active' class qo'shadi
        mockMvc.perform(get("/web/platform/tenants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("active\">Platform")));
    }

    // ========== GET /web/api/platform/tenants (HTMX fragment) ==========

    @Test
    void tenantsFragment_callsServiceWithActorId() throws Exception {
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk());

        verify(platformTenantQueryService).listAllTenants(ACTOR_USER_ID);
    }

    @Test
    void tenantsFragment_returnsRowsFragment_onSuccess() throws Exception {
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(List.of(
                        summary(TENANT_ID_A, "Acme Corp", "acme", "UTC", "ACTIVE")));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>Acme Corp</strong>")))
                .andExpect(content().string(containsString("<code>acme</code>")));
    }

    @Test
    void tenantsFragment_rendersStatusTagWithCorrectClass() throws Exception {
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(List.of(
                        summary(TENANT_ID_A, "A", "a", "UTC", "ACTIVE"),
                        summary(TENANT_ID_B, "B", "b", "UTC", "SUSPENDED")));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("status-tag status-active")))
                .andExpect(content().string(containsString("status-tag status-suspended")));
    }

    @Test
    void tenantsFragment_rendersViewLinkWithTenantId() throws Exception {
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(List.of(summary(TENANT_ID_A, "Acme", "acme", "UTC", "ACTIVE")));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/web/dashboard?tenantId=" + TENANT_ID_A)));
    }

    @Test
    void tenantsFragment_disabledActionButtonsPresent() throws Exception {
        // Suspend/Delete tugmalari Phase 218'ga qadar disabled
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(List.of(summary(TENANT_ID_A, "Acme", "acme", "UTC", "ACTIVE")));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString(">Suspend</button>")))
                .andExpect(content().string(containsString(">Delete</button>")));
    }

    @Test
    void tenantsFragment_emptyResult_rendersEmptyState() throws Exception {
        when(platformTenantQueryService.listAllTenants(ACTOR_USER_ID))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hech qanday tenant topilmadi")))
                .andExpect(content().string(containsString("empty-state")));
    }

    @Test
    void tenantsFragment_returnsDeniedFragment_onAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("PLATFORM_TENANT_LIST"))
                .when(platformTenantQueryService).listAllTenants(any(UUID.class));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk()) // HTMX swap target uchun 200 + denied content
                .andExpect(content().string(containsString("denied-state")))
                .andExpect(content().string(containsString("PLATFORM_OWNER")));
    }

    @Test
    void tenantsFragment_deniedFragment_doesNotLeakStackTrace() throws Exception {
        doThrow(new AccessDeniedException("internal-only-message"))
                .when(platformTenantQueryService).listAllTenants(any(UUID.class));

        mockMvc.perform(get("/web/api/platform/tenants").with(withActor(ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("internal-only-message"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("AccessDeniedException"))));
    }

    @Test
    void tenantsFragment_anonymous_returns401() throws Exception {
        // No actor → SecurityConfig /web/api/** matcher (Phase 209B) JWT
        // talab qiladi. JwtActorConverter actorUserId null bo'lganda 401.
        mockMvc.perform(get("/web/api/platform/tenants"))
                .andExpect(status().isUnauthorized());
    }
}
