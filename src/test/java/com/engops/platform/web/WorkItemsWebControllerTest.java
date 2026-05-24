package com.engops.platform.web;

import com.engops.platform.admin.WorkItemSummaryItem;
import com.engops.platform.admin.WorkItemSummaryReadFacade;
import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.workitem.model.WorkItemType;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 210 — {@link WorkItemsWebController} @WebMvcTest.
 */
@WebMvcTest(WorkItemsWebController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WorkItemsWebControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkItemSummaryReadFacade workItemSummaryReadFacade;

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

    private WorkItemSummaryItem item(String code, String title, WorkItemType type,
                                      String status, String severity) {
        return new WorkItemSummaryItem(
                UUID.randomUUID(),
                code,
                title,
                type,
                status,
                null, // priorityCode
                severity,
                null, // currentOwnerUserId
                Instant.parse("2026-05-24T00:00:00Z"),
                null, null,
                0,
                false);
    }

    @Test
    void list_happyPath_rendersRowsWithSeverityTags() throws Exception {
        WorkItemSummaryItem i1 = item("BUG-1", "Login crash", WorkItemType.BUG,
                "PROCESSING", "CRITICAL");
        WorkItemSummaryItem i2 = item("INC-7", "DB outage", WorkItemType.INCIDENT,
                "REPORTED", "HIGH");
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of(i1, i2));

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("BUG-1")))
                .andExpect(content().string(containsString("Login crash")))
                .andExpect(content().string(containsString("INC-7")))
                .andExpect(content().string(containsString("severity-tag critical")))
                .andExpect(content().string(containsString("severity-tag high")))
                .andExpect(content().string(containsString("type-tag")));
    }

    @Test
    void list_emptyResult_rendersEmptyStateRow() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No work items yet")))
                .andExpect(content().string(containsString("empty-state")))
                .andExpect(content().string(containsString("colspan=\"5\"")));
    }

    @Test
    void list_severityNullRow_rendersNoneTag() throws Exception {
        WorkItemSummaryItem itemNoSeverity = item("TASK-1", "Cleanup",
                WorkItemType.TASK, "TODO", null);
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of(itemNoSeverity));

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("severity-tag none")))
                .andExpect(content().string(containsString("—")));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/web/api/work-items/list")
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_unauthorized_returns403() throws Exception {
        doThrow(new AccessDeniedException("TENANT_CONFIG_READ talab qilinadi"))
                .when(workItemSummaryReadFacade).getSummaryList(any(), anyInt(), any());

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_fragment_isolated_noFullHtmlDocument() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("<body"))));
    }
}
