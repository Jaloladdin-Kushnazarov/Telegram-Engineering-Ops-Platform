package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 199 — {@link TenantOnboardingController} @WebMvcTest testlari.
 *
 * Authentication actor SecurityContext'ga RequestAttribute pattern orqali
 * o'rnatiladi (spring-security-test'siz; WorkItemAdminWriteControllerTest
 * pattern bilan bir xil).
 */
@WebMvcTest(TenantOnboardingController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class TenantOnboardingControllerTest {

    private static final UUID ACTOR_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NEW_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NEW_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_MEMBERSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantOnboardingService tenantOnboardingService;

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

    private static String validRequestBody() {
        return """
                {
                  "tenantName": "Acme Corp",
                  "tenantSlug": "acme",
                  "tenantTimezone": "Asia/Tashkent",
                  "adminTelegramUserId": 123456789,
                  "adminDisplayName": "Demo Admin",
                  "adminUsername": "demo_admin",
                  "workflowTemplateCodes": ["BUG_MINIMAL"]
                }
                """;
    }

    private static TenantOnboardingResult happyResult() {
        return new TenantOnboardingResult(
                NEW_TENANT_ID, "acme", "Acme Corp",
                Instant.parse("2026-05-23T00:00:00Z"),
                NEW_USER_ID, NEW_MEMBERSHIP_ID,
                List.of(new TenantOnboardingResult.WorkflowDefinitionSummary(
                        WORKFLOW_DEF_ID, "BUG_MINIMAL", "BUG")));
    }

    @Test
    void submit_validRequest_returns201AndLocationAndResponseEchoesIds() throws Exception {
        when(tenantOnboardingService.onboard(any(TenantOnboardingCommand.class)))
                .thenReturn(happyResult());

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/admin/tenants/" + NEW_TENANT_ID))
                .andExpect(jsonPath("$.tenantId").value(NEW_TENANT_ID.toString()))
                .andExpect(jsonPath("$.tenantSlug").value("acme"))
                .andExpect(jsonPath("$.tenantName").value("Acme Corp"))
                .andExpect(jsonPath("$.adminAppUserId").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.adminMembershipId").value(NEW_MEMBERSHIP_ID.toString()))
                .andExpect(jsonPath("$.workflowDefinitions[0].workflowDefinitionId").value(WORKFLOW_DEF_ID.toString()))
                .andExpect(jsonPath("$.workflowDefinitions[0].templateCode").value("BUG_MINIMAL"))
                .andExpect(jsonPath("$.workflowDefinitions[0].workItemType").value("BUG"));
    }

    @Test
    void submit_unauthorizedActor_returns403_envelope() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun TENANT_ONBOARD ruxsati talab qilinadi"))
                .when(tenantOnboardingService).onboard(any(TenantOnboardingCommand.class));

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void submit_unknownTemplateCode_returns422_envelope() throws Exception {
        doThrow(new BusinessRuleException("UNKNOWN_WORKFLOW_TEMPLATE",
                "Noma'lum workflow shablon kodi: 'UNKNOWN'"))
                .when(tenantOnboardingService).onboard(any(TenantOnboardingCommand.class));

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_WORKFLOW_TEMPLATE"));
    }

    @Test
    void submit_duplicateSlug_returns422_envelope_SLUG_TAKEN() throws Exception {
        doThrow(new BusinessRuleException("SLUG_TAKEN", "'acme' slug bilan tenant allaqachon mavjud"))
                .when(tenantOnboardingService).onboard(any(TenantOnboardingCommand.class));

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SLUG_TAKEN"));
    }

    @Test
    void submit_invalidSlug_returns422_envelope_INVALID_SLUG() throws Exception {
        doThrow(new BusinessRuleException("INVALID_SLUG", "slug noto'g'ri"))
                .when(tenantOnboardingService).onboard(any(TenantOnboardingCommand.class));

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantName":"X","tenantSlug":"BAD","tenantTimezone":"UTC",
                                 "adminTelegramUserId":1,"adminDisplayName":"X",
                                 "workflowTemplateCodes":["BUG_MINIMAL"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SLUG"));
    }

    @Test
    void submit_emptyTemplateCodes_returns422_envelope_NO_TEMPLATES_REQUESTED() throws Exception {
        doThrow(new BusinessRuleException("NO_TEMPLATES_REQUESTED",
                "workflowTemplateCodes kamida 1 ta shablon kodini o'z ichiga olishi shart"))
                .when(tenantOnboardingService).onboard(any(TenantOnboardingCommand.class));

        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantName":"X","tenantSlug":"acme","tenantTimezone":"UTC",
                                 "adminTelegramUserId":1,"adminDisplayName":"X",
                                 "workflowTemplateCodes":[]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_TEMPLATES_REQUESTED"));
    }

    @Test
    void submit_missingBody_returns400_envelope() throws Exception {
        mockMvc.perform(post("/api/admin/tenants")
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tenantOnboardingService);
    }

    @Test
    void submit_unauthenticated_returns401() throws Exception {
        // No withActor() — request has no Authentication; security filter chain
        // intercepts the request before the controller is invoked. The platform
        // emits 401 UNAUTHORIZED on missing JWT (default-deny on /api/**).
        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tenantOnboardingService);
    }
}
