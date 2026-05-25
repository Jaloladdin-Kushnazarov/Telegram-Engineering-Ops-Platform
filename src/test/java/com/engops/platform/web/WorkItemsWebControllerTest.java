package com.engops.platform.web;

import com.engops.platform.admin.WorkItemSummaryItem;
import com.engops.platform.admin.WorkItemSummaryReadFacade;
import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.intake.IntakeApplicationService;
import com.engops.platform.intake.IntakeCommand;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.workitem.model.WorkItemType;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

    @MockBean
    private IntakeApplicationService intakeApplicationService;

    private static final UUID ASSIGNEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CREATE_API = "/web/api/work-items";

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
        return item(code, title, type, status, severity, null);
    }

    private WorkItemSummaryItem item(String code, String title, WorkItemType type,
                                      String status, String severity, String ownerDisplayName) {
        return new WorkItemSummaryItem(
                UUID.randomUUID(),
                code,
                title,
                type,
                status,
                null, // priorityCode
                severity,
                null, // currentOwnerUserId
                ownerDisplayName, // Phase 220a
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
                .andExpect(content().string(containsString("colspan=\"6\"")));  // Phase 220a — 6 columns
    }

    // ========== Phase 220a — assignee column render ==========

    @Test
    void list_rendersOwnerDisplayName_inAssigneeColumn() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of(item("BUG-1", "Login crash", WorkItemType.BUG,
                        "PROCESSING", "HIGH", "Bobur Karimov")));

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bobur Karimov")));
    }

    @Test
    void list_rendersDashForNullAssignee() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of(item("BUG-2", "No owner", WorkItemType.BUG,
                        "PROCESSING", "LOW", null)));

        mockMvc.perform(get("/web/api/work-items/list")
                        .with(withActor(ACTOR_ID))
                        .queryParam("tenantId", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("—")));
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

    // ========== Phase 220b — POST create (intake-based) ==========

    @Test
    void createWorkItem_valid_returnsRowsAndSetsHxTrigger() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(TENANT_ID, 50, ACTOR_ID))
                .thenReturn(List.of(item("BUG-1", "Login crash", WorkItemType.BUG,
                        "PROCESSING", "HIGH")));

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "HIGH")
                        .param("title", "Login crash")
                        .param("description", "Steps to reproduce")
                        .param("assigneeUserId", ASSIGNEE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/work-item-rows :: rows"))
                .andExpect(header().string("HX-Trigger", "workItemCreated"))
                .andExpect(content().string(containsString("Login crash")));
    }

    @Test
    void createWorkItem_withAssignee_passedAsOwnerUserId() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(any(), anyInt(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "TASK")
                        .param("severity", "LOW")
                        .param("title", "Do thing")
                        .param("assigneeUserId", ASSIGNEE_ID.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        IntakeCommand cmd = captor.getValue();
        assertThat(cmd.getOwnerUserId()).isEqualTo(ASSIGNEE_ID);
        assertThat(cmd.getTypeCode()).isEqualTo(WorkItemType.TASK);
        assertThat(cmd.getSeverityCode()).isEqualTo("LOW");
        assertThat(cmd.getCreatedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(cmd.getActionSource()).isEqualTo("WEB_UI");
    }

    @Test
    void createWorkItem_noAssignee_ownerUserIdNull() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(any(), anyInt(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "MEDIUM")
                        .param("title", "No owner")
                        .param("assigneeUserId", "   "))
                .andExpect(status().isOk());

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isNull();
    }

    @Test
    void createWorkItem_emptyDescription_normalizedToNull() throws Exception {
        when(workItemSummaryReadFacade.getSummaryList(any(), anyInt(), any()))
                .thenReturn(List.of());

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "MEDIUM")
                        .param("title", "Blank desc")
                        .param("description", "   "))
                .andExpect(status().isOk());

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    void createWorkItem_businessRule_returnsCreateErrorFragment() throws Exception {
        doThrow(new BusinessRuleException("INVALID_OWNER", "Foydalanuvchi faol a'zo emas"))
                .when(intakeApplicationService).submit(any(IntakeCommand.class));

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "HIGH")
                        .param("title", "Bad owner")
                        .param("assigneeUserId", ASSIGNEE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/work-item-rows :: createError"))
                .andExpect(header().string("HX-Retarget", "#work-item-error"))
                .andExpect(content().string(containsString("Foydalanuvchi")));
    }

    @Test
    void createWorkItem_accessDenied_returnsCreateErrorFragment() throws Exception {
        doThrow(new AccessDeniedException("WORK_ITEM_CREATE talab qilinadi"))
                .when(intakeApplicationService).submit(any(IntakeCommand.class));

        mockMvc.perform(post(CREATE_API)
                        .with(withActor(ACTOR_ID))
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "HIGH")
                        .param("title", "No perm"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/fragments/work-item-rows :: createError"))
                .andExpect(header().string("HX-Retarget", "#work-item-error"));
    }

    @Test
    void createWorkItem_anonymous_returns401() throws Exception {
        mockMvc.perform(post(CREATE_API)
                        .param("tenantId", TENANT_ID.toString())
                        .param("type", "BUG")
                        .param("severity", "HIGH")
                        .param("title", "Anon"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(intakeApplicationService);
    }
}
