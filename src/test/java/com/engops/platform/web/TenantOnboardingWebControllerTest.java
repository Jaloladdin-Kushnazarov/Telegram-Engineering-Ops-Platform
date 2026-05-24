package com.engops.platform.web;

import com.engops.platform.admin.TenantOnboardingCommand;
import com.engops.platform.admin.TenantOnboardingResult;
import com.engops.platform.admin.TenantOnboardingService;
import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 213 — {@link TenantOnboardingWebController} @WebMvcTest.
 *
 * <p>TenantOnboardingService @MockBean — hech qanday haqiqiy DB write yo'q.
 * Shim controller validation + adapter + exception mapping'ni tekshiradi.</p>
 */
@WebMvcTest(TenantOnboardingWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class TenantOnboardingWebControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_TENANT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantOnboardingService tenantOnboardingService;

    // SecurityWebMvcConfig'da @CurrentActor resolver mavjud, lekin
    // tenantconfig.TenantConfigQueryService base.html (tenant-select-wrapper)
    // orqali emas — bu controller WebController'dan alohida; lekin base
    // layout includes hot-loaded chunklar bo'lganligi sababli mock kerak emas.

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

    // ========== GET /web/onboarding ==========

    @Test
    void getOnboarding_returnsForm_withEmptyFields() throws Exception {
        mockMvc.perform(get("/web/onboarding").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create tenant")))
                .andExpect(content().string(containsString("name=\"name\"")))
                .andExpect(content().string(containsString("name=\"slug\"")))
                .andExpect(content().string(containsString("name=\"adminTelegramUserId\"")))
                .andExpect(content().string(containsString("name=\"adminDisplayName\"")))
                .andExpect(content().string(containsString("name=\"timezone\"")));
    }

    @Test
    void getOnboarding_includesBaseLayoutNav() throws Exception {
        mockMvc.perform(get("/web/onboarding").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"topnav\"")))
                .andExpect(content().string(containsString(">Dashboard</a>")));
    }

    @Test
    void onboarding_page_includesFormCard() throws Exception {
        mockMvc.perform(get("/web/onboarding").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"form-card\"")))
                .andExpect(content().string(containsString("class=\"form-actions\"")))
                .andExpect(content().string(containsString("button type=\"submit\"")));
    }

    @Test
    void onboarding_page_includesAllRequiredFields() throws Exception {
        // 5 fields: name, slug, adminTelegramUserId, adminDisplayName, timezone
        String body = mockMvc.perform(get("/web/onboarding").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .contains("for=\"name\"")
                .contains("for=\"slug\"")
                .contains("for=\"adminTelegramUserId\"")
                .contains("for=\"adminDisplayName\"")
                .contains("for=\"timezone\"");
    }

    @Test
    void navLinks_includeNewTenantLink() throws Exception {
        mockMvc.perform(get("/web/onboarding").with(withActor(ACTOR_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"onboarding-link\"")))
                .andExpect(content().string(containsString("+ New tenant")));
    }

    // ========== POST /web/onboarding — happy path ==========

    @Test
    void postOnboarding_validInput_callsServiceAndRedirects() throws Exception {
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001")
                        .param("adminDisplayName", "Demo Admin")
                        .param("timezone", "UTC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/dashboard?tenantId=" + NEW_TENANT_ID));

        verify(tenantOnboardingService, times(1))
                .onboard(any(TenantOnboardingCommand.class));
    }

    @Test
    void postOnboarding_redirectIncludesNewTenantId() throws Exception {
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        containsString(NEW_TENANT_ID.toString())));
    }

    @Test
    void postOnboarding_serviceCalled_withCorrectActorAndRequest() throws Exception {
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001")
                        .param("timezone", "Asia/Tashkent"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<TenantOnboardingCommand> captor =
                ArgumentCaptor.forClass(TenantOnboardingCommand.class);
        verify(tenantOnboardingService).onboard(captor.capture());
        TenantOnboardingCommand cmd = captor.getValue();
        assertThat(cmd.tenantName()).isEqualTo("Acme Corp");
        assertThat(cmd.tenantSlug()).isEqualTo("acme");
        assertThat(cmd.adminTelegramUserId()).isEqualTo(100000001L);
        assertThat(cmd.tenantTimezone()).isEqualTo("Asia/Tashkent");
        assertThat(cmd.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(cmd.workflowTemplateCodes()).containsExactly("BUG_MINIMAL");
        // adminDisplayName default applied
        assertThat(cmd.adminDisplayName()).isEqualTo("Tenant Admin");
    }

    // ========== POST /web/onboarding — validation errors ==========

    @Test
    void postOnboarding_emptyName_rejected_withFieldError() throws Exception {
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tenant nomi majburiy")));

        verify(tenantOnboardingService, never()).onboard(any());
    }

    @Test
    void postOnboarding_invalidSlug_rejected_withFieldError() throws Exception {
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "Acme Corp WITH SPACES")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Slug faqat kichik harf, raqam va tire")));

        verify(tenantOnboardingService, never()).onboard(any());
    }

    @Test
    void postOnboarding_negativeTelegramId_rejected_withFieldError() throws Exception {
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "-5"))
                .andExpect(status().isOk())
                // Thymeleaf apostrophe'ni &#39; deb escape qiladi —
                // shu sababli apostrofe'siz substring tekshiramiz
                .andExpect(content().string(containsString("musbat raqam")));

        verify(tenantOnboardingService, never()).onboard(any());
    }

    @Test
    void postOnboarding_invalidTimezone_rejected_withFieldError() throws Exception {
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001")
                        .param("timezone", "Mars/Olympus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "IANA timezone identifikatori")));

        verify(tenantOnboardingService, never()).onboard(any());
    }

    @Test
    void postOnboarding_preservesUserInputOnError() throws Exception {
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "")
                        .param("slug", "valid-slug")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                // Slug qiymati saqlangan (re-typing yo'q)
                .andExpect(content().string(containsString("value=\"valid-slug\"")));
    }

    // ========== POST /web/onboarding — service exception mapping ==========

    @Test
    void postOnboarding_duplicateSlugFromService_renders_withFieldError() throws Exception {
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("SLUG_TAKEN",
                        "'acme' slug bilan tenant allaqachon mavjud"));

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Bu slug allaqachon mavjud")));
    }

    @Test
    void postOnboarding_serviceValidationException_rendersGlobalError() throws Exception {
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("UNKNOWN_WORKFLOW_TEMPLATE",
                        "Noma'lum workflow shablon kodi: 'BUG_MISSING'"));

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("form-error-banner")))
                .andExpect(content().string(containsString("qabul qilinmadi")));
    }

    @Test
    void postOnboarding_accessDeniedFromService_rendersGlobalError() throws Exception {
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new com.engops.platform.sharedkernel.exception
                        .AccessDeniedException("TENANT_ONBOARD permission yo'q"));

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "TENANT_ONBOARD")))
                .andExpect(content().string(containsString("form-error-banner")));
    }

    @Test
    void postOnboarding_invalidSlugFromService_mapsToFieldError() throws Exception {
        // Service-layer INVALID_SLUG (shim bypass qilgan edge case)
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("INVALID_SLUG",
                        "Service-level slug rejection"));

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Service-level slug rejection")))
                .andExpect(content().string(not(containsString("form-error-banner"))));
    }

    // ========== Phase 213a — HTMX-aware submission ==========

    @Test
    void postOnboarding_htmxRequest_returnsHxRedirectHeader() throws Exception {
        // HTMX so'rov: HX-Request header + valid input → 200 OK + HX-Redirect.
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .header("HX-Request", "true")
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        containsString(NEW_TENANT_ID.toString())))
                .andExpect(header().string("HX-Redirect",
                        containsString("/web/dashboard?tenantId=")));
    }

    @Test
    void postOnboarding_htmxRequest_doesNotReturn302() throws Exception {
        // HTMX so'rov 302 emas, 200 OK qaytarishi shart (server-side
        // redirect HTMX'da silent fail bo'ladi; client-side
        // HX-Redirect header'i ishlatiladi).
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .header("HX-Request", "true")
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void postOnboarding_nonHtmxRequest_returns302WithLocation() throws Exception {
        // Regression: curl / native form (HX-Request header yo'q) hali
        // ham 302 Found + Location header qaytaradi.
        TenantOnboardingResult result = new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), List.of());
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(result);

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .param("name", "Acme Corp")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        containsString("/web/dashboard?tenantId=" + NEW_TENANT_ID)))
                .andExpect(header().doesNotExist("HX-Redirect"));
    }

    @Test
    void postOnboarding_htmxRequest_validationError_returnsFormFragment() throws Exception {
        // HTMX + validation error → 200 OK, HX-Redirect YO'Q, form HTML render.
        // HTMX hx-select="form" client'da <form> elementni ajratib oladi.
        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .header("HX-Request", "true")
                        .param("name", "")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("HX-Redirect"))
                .andExpect(content().string(containsString("Tenant nomi majburiy")))
                .andExpect(content().string(containsString("<form")));

        verify(tenantOnboardingService, never()).onboard(any());
    }

    @Test
    void postOnboarding_htmxRequest_serviceError_returnsFormFragment() throws Exception {
        // HTMX + service SLUG_TAKEN → 200 OK, HX-Redirect YO'Q, error banner.
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenThrow(new BusinessRuleException("SLUG_TAKEN",
                        "'acme' slug bilan tenant allaqachon mavjud"));

        mockMvc.perform(post("/web/onboarding")
                        .with(withActor(ACTOR_ID))
                        .header("HX-Request", "true")
                        .param("name", "Acme")
                        .param("slug", "acme")
                        .param("adminTelegramUserId", "100000001"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("HX-Redirect"))
                .andExpect(content().string(containsString("Bu slug allaqachon mavjud")))
                .andExpect(content().string(containsString("<form")));
    }
}
