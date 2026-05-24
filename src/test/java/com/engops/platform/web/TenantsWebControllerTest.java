package com.engops.platform.web;

import com.engops.platform.identity.model.Membership;
import com.engops.platform.identity.model.MembershipStatus;
import com.engops.platform.identity.repository.MembershipRepository;
import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 210 — {@link TenantsWebController} @WebMvcTest.
 */
@WebMvcTest(TenantsWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class TenantsWebControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-1111111111aa");
    private static final UUID TENANT_B = UUID.fromString("11111111-1111-1111-1111-1111111111bb");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MembershipRepository membershipRepository;

    @MockBean
    private TenantConfigQueryService tenantConfigQueryService;

    private static RequestPostProcessor withActor(UUID actorUserId) {
        return request -> {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedActor(actorUserId, null), null, Collections.emptyList());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            request.setAttribute(
                    RequestAttributeSecurityContextRepository.class.getName()
                            + ".SPRING_SECURITY_CONTEXT",
                    context);
            return request;
        };
    }

    private Membership active(UUID tenantId) {
        Membership m = new Membership(tenantId, ACTOR_ID);
        m.setStatus(MembershipStatus.ACTIVE);
        return m;
    }

    private Tenant tenant(UUID id, String slug, String name) {
        Tenant t = mock(Tenant.class);
        when(t.getId()).thenReturn(id);
        when(t.getSlug()).thenReturn(slug);
        when(t.getName()).thenReturn(name);
        return t;
    }

    @Test
    void options_happyPath_rendersSelectWithTenants() throws Exception {
        // Build mocks BEFORE outer when()/thenReturn() to avoid Mockito's
        // "unfinished stubbing" detection (nested when() inside Optional.of()
        // argument expression confuses the framework — same pattern as Phase
        // 198 / 205 test setup).
        Membership m1 = active(TENANT_A);
        Membership m2 = active(TENANT_B);
        Tenant tA = tenant(TENANT_A, "acme", "Acme Corp");
        Tenant tB = tenant(TENANT_B, "widgets", "Widgets Inc");

        when(membershipRepository.findByUserId(ACTOR_ID)).thenReturn(List.of(m1, m2));
        when(tenantConfigQueryService.findTenantById(TENANT_A)).thenReturn(Optional.of(tA));
        when(tenantConfigQueryService.findTenantById(TENANT_B)).thenReturn(Optional.of(tB));

        mockMvc.perform(get("/web/api/tenants/options").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("Acme Corp")))
                .andExpect(content().string(containsString("Widgets Inc")))
                .andExpect(content().string(containsString("onchange=\"onTenantSelected")));
    }

    @Test
    void options_emptyList_rendersSelectWithPlaceholder() throws Exception {
        when(membershipRepository.findByUserId(ACTOR_ID)).thenReturn(List.of());

        mockMvc.perform(get("/web/api/tenants/options").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Select tenant")))
                .andExpect(content().string(containsString("<select")));
    }

    @Test
    void options_inactiveMembership_excludedFromList() throws Exception {
        Membership suspended = new Membership(TENANT_A, ACTOR_ID);
        suspended.setStatus(MembershipStatus.SUSPENDED);
        Membership activeOther = active(TENANT_B);
        Tenant tB = tenant(TENANT_B, "widgets", "Widgets Inc");

        when(membershipRepository.findByUserId(ACTOR_ID))
                .thenReturn(List.of(suspended, activeOther));
        when(tenantConfigQueryService.findTenantById(TENANT_B)).thenReturn(Optional.of(tB));

        mockMvc.perform(get("/web/api/tenants/options").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Widgets Inc")))
                .andExpect(content().string(not(containsString("Acme Corp"))));
    }

    @Test
    void options_activeTenantIdSelected_marksOptionWithSelected() throws Exception {
        Membership m1 = active(TENANT_A);
        Tenant tA = tenant(TENANT_A, "acme", "Acme Corp");
        when(membershipRepository.findByUserId(ACTOR_ID)).thenReturn(List.of(m1));
        when(tenantConfigQueryService.findTenantById(TENANT_A)).thenReturn(Optional.of(tA));

        mockMvc.perform(get("/web/api/tenants/options")
                        .with(withActor(ACTOR_ID))
                        .queryParam("activeTenantId", TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("selected")));
    }

    @Test
    void options_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/web/api/tenants/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void options_renderedAsFragment_noFullHtmlDocument() throws Exception {
        when(membershipRepository.findByUserId(ACTOR_ID)).thenReturn(List.of());

        mockMvc.perform(get("/web/api/tenants/options").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("<body"))));
    }
}
