package com.engops.platform.analytics;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class AnalyticsControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsQueryService analyticsQueryService;

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

    private AnalyticsAggregateResult sample() {
        return new AnalyticsAggregateResult(
                TENANT_ID, 39L,
                List.of(new AnalyticsBucket("RESOLVED", 23),
                        new AnalyticsBucket("REPORTED", 12),
                        new AnalyticsBucket("IN_PROGRESS", 4)));
    }

    // ========== Happy paths (one per endpoint) ==========

    @Test
    void byStatus_returns200_andUniformShape() throws Exception {
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(sample());

        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.totalCount").value(39))
                .andExpect(jsonPath("$.buckets[0].label").value("RESOLVED"))
                .andExpect(jsonPath("$.buckets[0].count").value(23))
                .andExpect(jsonPath("$.buckets[2].label").value("IN_PROGRESS"));
    }

    @Test
    void byType_returns200() throws Exception {
        when(analyticsQueryService.workItemsByType(TENANT_ID, ACTOR_ID))
                .thenReturn(sample());

        mockMvc.perform(get("/api/analytics/work-items/by-type")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(39));
    }

    @Test
    void bySeverity_returns200() throws Exception {
        when(analyticsQueryService.workItemsBySeverity(TENANT_ID, ACTOR_ID))
                .thenReturn(sample());

        mockMvc.perform(get("/api/analytics/work-items/by-severity")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
    }

    @Test
    void emptyBuckets_returns200WithZeroTotal() throws Exception {
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(new AnalyticsAggregateResult(TENANT_ID, 0L, List.of()));

        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.buckets").isEmpty());
    }

    // ========== 403 (one per endpoint) ==========

    @Test
    void byStatus_unauthorizedActor_returns403_envelope() throws Exception {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ talab qilinadi"))
                .when(analyticsQueryService).workItemsByStatus(any(), any());

        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void byType_unauthorizedActor_returns403() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(analyticsQueryService).workItemsByType(any(), any());

        mockMvc.perform(get("/api/analytics/work-items/by-type")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void bySeverity_unauthorizedActor_returns403() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(analyticsQueryService).workItemsBySeverity(any(), any());

        mockMvc.perform(get("/api/analytics/work-items/by-severity")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    // ========== 422 (service-level validation) ==========

    @Test
    void invalidTenantId_returns422_envelope() throws Exception {
        // Spring resolves the UUID query param fine; service throws on null
        // tenantId in unit tests. At controller layer with a syntactically
        // valid UUID, simulate a downstream BusinessRuleException scenario.
        doThrow(new BusinessRuleException("INVALID_TENANT_ID", "tenantId majburiy"))
                .when(analyticsQueryService).workItemsByStatus(any(), any());

        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TENANT_ID"));
    }

    // ========== 400 (missing query param, one per endpoint) ==========

    @Test
    void byStatus_missingTenantIdQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsQueryService);
    }

    @Test
    void byType_missingTenantIdQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/work-items/by-type")
                        .with(withActor(ACTOR_ID)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsQueryService);
    }

    @Test
    void bySeverity_missingTenantIdQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/work-items/by-severity")
                        .with(withActor(ACTOR_ID)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analyticsQueryService);
    }

    @Test
    void malformedTenantIdUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ========== 401 (no JWT) ==========

    @Test
    void byStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/analytics/work-items/by-status")
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(analyticsQueryService);
    }
}
