package com.engops.platform.workflow;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WorkflowTransitionController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - 200 success: response body to'g'ri map qilinadi
 * - empty body ({}) → service'ga uzatiladi va service'ning mavjud xulqini hurmat qiladi
 * - SAME_STATUS BusinessRuleException → 422 errorCode bilan
 * - INVALID_TRANSITION BusinessRuleException → 422 errorCode bilan
 * - ResourceNotFoundException (work item topilmadi) → 404
 * - Controller WorkflowTransitionService'ga aynan bir marta delegate qiladi va
 *   barcha argumentlar to'g'ri uzatiladi (tenantId, workItemId, targetStatusCode,
 *   actorUserId, actionSource, reason)
 * - Phase 122 xavfsizlik konteksti: hech qanday authorization service chaqirilmaydi
 *   (faqat WorkflowTransitionService mock qilingan; agar boshqa security bean kerak
 *   bo'lganida @WebMvcTest context yuklab bo'lmas edi).
 */
@WebMvcTest(WorkflowTransitionController.class)
class WorkflowTransitionControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID ACTOR_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORKFLOW_DEFINITION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID OWNER_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowTransitionService workflowTransitionService;

    private WorkItem successWorkItem(String status) {
        WorkItem wi = org.mockito.Mockito.mock(WorkItem.class);
        when(wi.getId()).thenReturn(WORK_ITEM_ID);
        when(wi.getTenantId()).thenReturn(TENANT_ID);
        when(wi.getWorkItemCode()).thenReturn("BUG-1");
        when(wi.getTypeCode()).thenReturn(WorkItemType.BUG);
        when(wi.getTitle()).thenReturn("Login broken");
        when(wi.getCurrentStatusCode()).thenReturn(status);
        when(wi.getWorkflowDefinitionId()).thenReturn(WORKFLOW_DEFINITION_ID);
        when(wi.getCurrentOwnerUserId()).thenReturn(OWNER_USER_ID);
        when(wi.getLastTransitionAt()).thenReturn(Instant.parse("2026-04-29T10:00:00Z"));
        when(wi.getResolvedAt()).thenReturn(null);
        when(wi.getReopenedCount()).thenReturn(0);
        when(wi.getUpdatedAt()).thenReturn(Instant.parse("2026-04-29T10:00:00Z"));
        return wi;
    }

    @Test
    void transitionReturns200AndMapsResponseBody() throws Exception {
        WorkItem wi = successWorkItem("PROCESSING");
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenReturn(wi);

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL",
                                  "reason":"started investigation"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value("BUG-1"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.title").value("Login broken"))
                .andExpect(jsonPath("$.currentStatusCode").value("PROCESSING"))
                .andExpect(jsonPath("$.workflowDefinitionId").value(WORKFLOW_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.lastTransitionAt").value("2026-04-29T10:00:00Z"))
                .andExpect(jsonPath("$.reopenedCount").value(0))
                .andExpect(jsonPath("$.updatedAt").value("2026-04-29T10:00:00Z"))
                // resolvedAt null — JsonInclude(NON_NULL) tushiradi
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    void transitionDelegatesExactlyOnceWithMappedArguments() throws Exception {
        WorkItem wi = successWorkItem("PROCESSING");
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("TELEGRAM"), eq("started investigation")))
                .thenReturn(wi);

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"TELEGRAM",
                                  "reason":"started investigation"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isOk());

        verify(workflowTransitionService, times(1)).transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("TELEGRAM"), eq("started investigation"));
    }

    @Test
    void transitionEmptyBodyReturns400WithoutDelegating() throws Exception {
        // Empty {} → barcha field'lar null. REST boundary tenantId null'da
        // 400 BAD_REQUEST qaytaradi va service hech qachon chaqirilmaydi.
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionMissingTenantIdReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionBlankTargetStatusCodeReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"   ",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionMissingActorUserIdReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionBlankActionSourceReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"  "
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionMalformedWorkItemIdReturns400() throws Exception {
        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflowTransitionService);
    }

    @Test
    void transitionSameStatusBubblesAs422() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("BUGS"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new BusinessRuleException("SAME_STATUS",
                        "Work item allaqachon 'BUGS' holatida"));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"BUGS",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SAME_STATUS"));
    }

    @Test
    void transitionInvalidTransitionBubblesAs422() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("FIXED"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new BusinessRuleException("INVALID_TRANSITION",
                        "'BUGS' dan 'FIXED' ga o'tish ruxsat etilmagan"));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"FIXED",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void transitionWorkItemNotFoundBubblesAs404() throws Exception {
        when(workflowTransitionService.transition(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("PROCESSING"),
                eq(ACTOR_USER_ID), eq("MANUAL"), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        mockMvc.perform(post("/api/work-items/{workItemId}/transitions", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "targetStatusCode":"PROCESSING",
                                  "actorUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, ACTOR_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
