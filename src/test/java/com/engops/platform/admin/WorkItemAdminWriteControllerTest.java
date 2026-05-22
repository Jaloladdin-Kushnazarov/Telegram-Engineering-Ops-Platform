package com.engops.platform.admin;

import com.engops.platform.infrastructure.security.AuthenticatedActor;
import com.engops.platform.infrastructure.security.SecurityConfig;
import com.engops.platform.infrastructure.security.SecurityWebMvcConfig;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.OperationalAuthorizationService;
import com.engops.platform.workitem.WorkItemCommandService;
import com.engops.platform.workitem.model.WorkItem;
import com.engops.platform.workitem.model.WorkItemType;
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

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 190 — {@link WorkItemAdminWriteController} @WebMvcTest testlari.
 *
 * <p>Tekshiriladigan yo'llar:</p>
 * <ul>
 *   <li>Owner assignment happy path — 200 + barqaror response shape;</li>
 *   <li>Priority update happy path — 200 + response shape;</li>
 *   <li>Severity update happy path — 200 + response shape;</li>
 *   <li>403 — authorize* AccessDeniedException tashlaganda;</li>
 *   <li>404 — command service ResourceNotFoundException tashlaganda;</li>
 *   <li>422 — command service BusinessRuleException tashlaganda (invalid code);</li>
 *   <li>400 — body yo'q yoki tenantId yo'q (controller-level guard).</li>
 * </ul>
 *
 * <p>Authentication actor SecurityContext'ga
 * {@link RequestAttributeSecurityContextRepository} attribute pattern orqali
 * o'rnatiladi — {@code spring-security-test} dependency'sini talab qilmaydi.</p>
 */
@WebMvcTest(WorkItemAdminWriteController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class WorkItemAdminWriteControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORK_ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ACTOR_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkItemCommandService workItemCommandService;

    @MockBean
    private OperationalAuthorizationService operationalAuthorizationService;

    private WorkItem newWorkItem(String priority, String severity, UUID owner) {
        WorkItem wi = new WorkItem(TENANT_ID, "BUG-1", WorkItemType.BUG,
                WORKFLOW_DEF_ID, "Login xato", "BUGS", ACTOR_USER_ID);
        if (priority != null) wi.setPriorityCode(priority);
        if (severity != null) wi.setSeverityCode(severity);
        if (owner != null) wi.assignOwner(owner);
        return wi;
    }

    // ========== POST /owner ==========

    @Test
    void assignOwnerHappyPathReturns200() throws Exception {
        WorkItem updated = newWorkItem("HIGH", "MEDIUM", OWNER_USER_ID);
        when(workItemCommandService.assignOwner(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq(OWNER_USER_ID),
                eq(ACTOR_USER_ID), eq("ADMIN_API"))).thenReturn(updated);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","ownerUserId":"%s"}
                                """.formatted(TENANT_ID, OWNER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemId").value(updated.getId().toString()))
                .andExpect(jsonPath("$.workItemCode").value("BUG-1"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.currentOwnerUserId").value(OWNER_USER_ID.toString()))
                .andExpect(jsonPath("$.priorityCode").value("HIGH"))
                .andExpect(jsonPath("$.severityCode").value("MEDIUM"));
    }

    @Test
    void assignOwnerWithoutAssignPermissionReturns403() throws Exception {
        doThrow(new AccessDeniedException("WORK_ITEM_ASSIGN ruxsati talab qilinadi"))
                .when(operationalAuthorizationService).authorizeAssignOwner(TENANT_ID, ACTOR_USER_ID);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","ownerUserId":"%s"}
                                """.formatted(TENANT_ID, OWNER_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(workItemCommandService);
    }

    @Test
    void assignOwnerWorkItemNotFoundReturns404() throws Exception {
        when(workItemCommandService.assignOwner(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","ownerUserId":"%s"}
                                """.formatted(TENANT_ID, OWNER_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assignOwnerInvalidOwnerReturns422() throws Exception {
        when(workItemCommandService.assignOwner(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("INVALID_OWNER",
                        "Foydalanuvchi shu tenantda faol a'zo emas"));

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","ownerUserId":"%s"}
                                """.formatted(TENANT_ID, OWNER_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OWNER"));
    }

    @Test
    void assignOwnerMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerUserId":"%s"}
                                """.formatted(OWNER_USER_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workItemCommandService);
    }

    @Test
    void assignOwnerMissingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workItemCommandService);
    }

    // ========== POST /priority ==========

    @Test
    void updatePriorityHappyPathReturns200() throws Exception {
        WorkItem updated = newWorkItem("CRITICAL", null, null);
        when(workItemCommandService.updatePriority(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("CRITICAL"),
                eq(ACTOR_USER_ID), eq("ADMIN_API"))).thenReturn(updated);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/priority", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","priorityCode":"CRITICAL"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityCode").value("CRITICAL"))
                .andExpect(jsonPath("$.workItemId").value(updated.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
    }

    @Test
    void updatePriorityWithoutUpdatePermissionReturns403() throws Exception {
        doThrow(new AccessDeniedException("WORK_ITEM_UPDATE ruxsati talab qilinadi"))
                .when(operationalAuthorizationService).authorizeUpdate(TENANT_ID, ACTOR_USER_ID);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/priority", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","priorityCode":"HIGH"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(workItemCommandService);
    }

    @Test
    void updatePriorityInvalidCodeReturns422() throws Exception {
        when(workItemCommandService.updatePriority(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("INVALID_PRIORITY_CODE",
                        "priorityCode noto'g'ri: 'URGENT'"));

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/priority", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","priorityCode":"URGENT"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PRIORITY_CODE"));
    }

    @Test
    void updatePriorityWorkItemNotFoundReturns404() throws Exception {
        when(workItemCommandService.updatePriority(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("WorkItem", WORK_ITEM_ID));

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/priority", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","priorityCode":"HIGH"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updatePriorityMissingTenantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/work-items/{workItemId}/priority", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priorityCode":"HIGH"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workItemCommandService);
    }

    // ========== POST /severity ==========

    @Test
    void updateSeverityHappyPathReturns200() throws Exception {
        WorkItem updated = newWorkItem(null, "HIGH", null);
        when(workItemCommandService.updateSeverity(
                eq(TENANT_ID), eq(WORK_ITEM_ID), eq("HIGH"),
                eq(ACTOR_USER_ID), eq("ADMIN_API"))).thenReturn(updated);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/severity", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","severityCode":"HIGH"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severityCode").value("HIGH"))
                .andExpect(jsonPath("$.workItemId").value(updated.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
    }

    @Test
    void updateSeverityWithoutUpdatePermissionReturns403() throws Exception {
        doThrow(new AccessDeniedException("WORK_ITEM_UPDATE ruxsati talab qilinadi"))
                .when(operationalAuthorizationService).authorizeUpdate(TENANT_ID, ACTOR_USER_ID);

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/severity", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","severityCode":"CRITICAL"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(workItemCommandService);
    }

    @Test
    void updateSeverityInvalidCodeReturns422() throws Exception {
        when(workItemCommandService.updateSeverity(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("INVALID_SEVERITY_CODE",
                        "severityCode noto'g'ri: 'BLOCKER'"));

        mockMvc.perform(post("/api/admin/work-items/{workItemId}/severity", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","severityCode":"BLOCKER"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SEVERITY_CODE"));
    }

    @Test
    void updateSeverityMissingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/work-items/{workItemId}/severity", WORK_ITEM_ID)
                        .with(withActor(ACTOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workItemCommandService);
    }

    // ========== Cross-endpoint: unauthenticated request ==========

    @Test
    void unauthenticatedAssignOwnerRequestReturns401() throws Exception {
        // No withActor(...) → no SecurityContext → filter chain rejects with 401
        mockMvc.perform(post("/api/admin/work-items/{workItemId}/owner", WORK_ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","ownerUserId":"%s"}
                                """.formatted(TENANT_ID, OWNER_USER_ID)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(workItemCommandService);
        verifyNoInteractions(operationalAuthorizationService);
    }
}
