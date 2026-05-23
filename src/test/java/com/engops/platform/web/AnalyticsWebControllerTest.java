package com.engops.platform.web;

import com.engops.platform.analytics.AnalyticsAggregateResult;
import com.engops.platform.analytics.AnalyticsBucket;
import com.engops.platform.analytics.AnalyticsQueryService;
import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 209B — {@link AnalyticsWebController} @WebMvcTest tests.
 *
 * <p>Fragment rendering, JWT enforcement (401 unauth, 403 forbidden),
 * empty-state, severity swatch markup, bar-width invariant all tested.</p>
 */
@WebMvcTest(AnalyticsWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class AnalyticsWebControllerTest {

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

    private AnalyticsAggregateResult sampleStatusResult() {
        AnalyticsBucket b1 = new AnalyticsBucket("RESOLVED", 23);
        AnalyticsBucket b2 = new AnalyticsBucket("REPORTED", 12);
        AnalyticsBucket b3 = new AnalyticsBucket("IN_PROGRESS", 4);
        return new AnalyticsAggregateResult(TENANT_ID, 39L, List.of(b1, b2, b3));
    }

    private AnalyticsAggregateResult sampleSeverityResult() {
        AnalyticsBucket b1 = new AnalyticsBucket("CRITICAL", 11);
        AnalyticsBucket b2 = new AnalyticsBucket("HIGH", 7);
        AnalyticsBucket b3 = new AnalyticsBucket("MEDIUM", 4);
        AnalyticsBucket b4 = new AnalyticsBucket("LOW", 2);
        return new AnalyticsAggregateResult(TENANT_ID, 24L, List.of(b1, b2, b3, b4));
    }

    private AnalyticsAggregateResult emptyResult() {
        return new AnalyticsAggregateResult(TENANT_ID, 0L, List.of());
    }

    // ========== Happy paths ==========

    @Test
    void byStatus_happyPath_rendersFragmentWithBuckets() throws Exception {
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(sampleStatusResult());

        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("By status")))
                .andExpect(content().string(containsString("RESOLVED")))
                .andExpect(content().string(containsString("REPORTED")))
                .andExpect(content().string(containsString("IN_PROGRESS")))
                .andExpect(content().string(containsString("class=\"bar-fill\"")))
                .andExpect(content().string(containsString(">39<"))); // totalCount
    }

    @Test
    void byType_happyPath() throws Exception {
        AnalyticsBucket b1 = new AnalyticsBucket("BUG", 10);
        AnalyticsBucket b2 = new AnalyticsBucket("INCIDENT", 5);
        AnalyticsBucket b3 = new AnalyticsBucket("TASK", 3);
        AnalyticsAggregateResult result =
                new AnalyticsAggregateResult(TENANT_ID, 18L, List.of(b1, b2, b3));
        when(analyticsQueryService.workItemsByType(TENANT_ID, ACTOR_ID)).thenReturn(result);

        mockMvc.perform(get("/web/api/analytics/by-type")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("By type")))
                .andExpect(content().string(containsString("BUG")))
                .andExpect(content().string(containsString("INCIDENT")))
                .andExpect(content().string(containsString("TASK")));
    }

    @Test
    void bySeverity_happyPath_includesSwatchSpanForSeverityLabels() throws Exception {
        when(analyticsQueryService.workItemsBySeverity(TENANT_ID, ACTOR_ID))
                .thenReturn(sampleSeverityResult());

        mockMvc.perform(get("/web/api/analytics/by-severity")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("By severity")))
                // Each severity label gets a swatch span with severity-specific class
                .andExpect(content().string(containsString("swatch critical")))
                .andExpect(content().string(containsString("swatch high")))
                .andExpect(content().string(containsString("swatch medium")))
                .andExpect(content().string(containsString("swatch low")))
                // bar-row also gets the severity class
                .andExpect(content().string(containsString("bar-row critical")));
    }

    // ========== Empty + invariants ==========

    @Test
    void byStatus_emptyResult_rendersEmptyState() throws Exception {
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(emptyResult());

        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("empty-badge")))
                .andExpect(content().string(containsString("empty-state")))
                .andExpect(content().string(containsString("Hozircha bu o'lchamda data yo'q")))
                // No bar-fill / bar-list rows when buckets empty
                .andExpect(content().string(not(containsString("class=\"bar-fill\""))));
    }

    @Test
    void bySeverity_excludesNullSeverityRows_invariantPropagatesFromService() throws Exception {
        // Phase 205 service already filters NULL severity (see WorkItemRepository
        // query WHERE severityCode IS NOT NULL + service-level null-label filter).
        // The fragment trusts the service contract; here we verify the fragment
        // does NOT introduce an inadvertent "null" label rendering for any
        // bucket emitted by the service.
        when(analyticsQueryService.workItemsBySeverity(TENANT_ID, ACTOR_ID))
                .thenReturn(sampleSeverityResult());

        mockMvc.perform(get("/web/api/analytics/by-severity")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                // Rendered bar labels are ONLY the 4 expected severity codes; no "null".
                .andExpect(content().string(not(containsString(">null<"))));
    }

    @Test
    void barFillWidth_isPercentageOfFirstBucketCount() throws Exception {
        // Phase 205 service pre-sorts buckets by count DESC, so buckets[0] is
        // always the max. The fragment uses `100.0 * count / buckets[0].count`
        // as the bar fill width. With 23/12/4 -> 100.0% / 52.17...% / 17.39...%.
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(sampleStatusResult());

        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("width: 100.0%")))
                // 12 / 23 = 0.5217... → check it's > 50%
                .andExpect(content().string(containsString("width: 52.")));
    }

    // ========== Auth / param errors ==========

    @Test
    void byStatus_unauthenticated_returns401() throws Exception {
        // No withActor() — no Authentication context, no Bearer token.
        // /web/api/** is JWT-protected via the Phase 209 SecurityConfig matcher.
        mockMvc.perform(get("/web/api/analytics/by-status")
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byStatus_unauthorized_returns403_envelope() throws Exception {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ talab qilinadi"))
                .when(analyticsQueryService).workItemsByStatus(any(), any());

        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void byStatus_missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID)))
                .andExpect(status().isBadRequest());
    }

    // ========== Fragment isolation ==========

    @Test
    void fragment_isolatedFromBaseLayout_noFullHtmlDocument() throws Exception {
        // The fragment selector "::chart" returns ONLY the <div class="card">
        // fragment, NOT the entire <html><body> wrapper. Verifies HTMX-friendly
        // partial render (no nested <html> tags after hx-swap="outerHTML").
        when(analyticsQueryService.workItemsByStatus(TENANT_ID, ACTOR_ID))
                .thenReturn(sampleStatusResult());

        mockMvc.perform(get("/web/api/analytics/by-status")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("<body"))))
                .andExpect(content().string(not(containsString("<head"))));
    }
}
