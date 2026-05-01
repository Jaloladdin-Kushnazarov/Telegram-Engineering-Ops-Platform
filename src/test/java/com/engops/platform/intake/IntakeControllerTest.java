package com.engops.platform.intake;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IntakeController @WebMvcTest testlari.
 *
 * Tekshiruvlar:
 * - 201 success: response body to'g'ri map qilinadi (routing fields ham)
 * - empty body ({}) → IntakeApplicationService.validateCommand orqali 422
 * - INTAKE_VALIDATION (BusinessRuleException) → 422 errorCode bilan
 * - ResourceNotFoundException → 404
 * - Noto'g'ri typeCode → 400 (controller'da WorkItemType.valueOf)
 * - Controller IntakeApplicationService'ga aynan bir marta delegate qiladi va
 *   IntakeCommand field'lari to'g'ri map qilinadi
 * - Phase 121 xavfsizlik konteksti: hech qanday authorization service chaqirilmaydi
 *   (faqat IntakeApplicationService mock qilingan; agar boshqa security bean kerak
 *   bo'lganida @WebMvcTest context yuklab bo'lmas edi).
 */
@WebMvcTest(IntakeController.class)
class IntakeControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORKFLOW_DEFINITION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID CREATED_BY_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORK_ITEM_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777771");
    private static final UUID ROUTING_RULE_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID TOPIC_BINDING_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID CHAT_BINDING_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666663");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IntakeApplicationService intakeApplicationService;

    @Test
    void submitReturns201AndMapsResponseIncludingRoutingFields() throws Exception {
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID,
                "BUG-1",
                "BUG",
                "Login broken",
                "BUGS",
                WORKFLOW_DEFINITION_ID,
                TENANT_ID,
                true,
                ROUTING_RULE_ID,
                TOPIC_BINDING_ID,
                CHAT_BINDING_ID,
                42L);

        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "description":"500 error",
                                  "workflowDefinitionId":"%s",
                                  "initialStatusCode":"BUGS",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.workItemCode").value("BUG-1"))
                .andExpect(jsonPath("$.typeCode").value("BUG"))
                .andExpect(jsonPath("$.title").value("Login broken"))
                .andExpect(jsonPath("$.currentStatusCode").value("BUGS"))
                .andExpect(jsonPath("$.workflowDefinitionId").value(WORKFLOW_DEFINITION_ID.toString()))
                .andExpect(jsonPath("$.routingPrepared").value(true))
                .andExpect(jsonPath("$.matchedRoutingRuleId").value(ROUTING_RULE_ID.toString()))
                .andExpect(jsonPath("$.targetTopicBindingId").value(TOPIC_BINDING_ID.toString()))
                .andExpect(jsonPath("$.targetChatBindingId").value(CHAT_BINDING_ID.toString()))
                .andExpect(jsonPath("$.targetTopicId").value(42));
    }

    @Test
    void submitOmitsRoutingFieldsWhenRoutingNotPrepared() throws Exception {
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID,
                "TASK-1",
                "TASK",
                "Cleanup logs",
                "OPEN",
                WORKFLOW_DEFINITION_ID,
                TENANT_ID,
                false,
                null, null, null, null);

        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"TASK",
                                  "title":"Cleanup logs",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingPrepared").value(false))
                .andExpect(jsonPath("$.matchedRoutingRuleId").doesNotExist())
                .andExpect(jsonPath("$.targetTopicBindingId").doesNotExist())
                .andExpect(jsonPath("$.targetChatBindingId").doesNotExist())
                .andExpect(jsonPath("$.targetTopicId").doesNotExist());
    }

    @Test
    void submitDelegatesToApplicationServiceWithMappedCommand() throws Exception {
        IntakeResult result = new IntakeResult(
                WORK_ITEM_ID, "BUG-2", "BUG", "Title", "BUGS",
                WORKFLOW_DEFINITION_ID, TENANT_ID,
                false, null, null, null, null);
        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(result);

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Title",
                                  "description":"Body",
                                  "workflowDefinitionId":"%s",
                                  "initialStatusCode":"BUGS",
                                  "createdByUserId":"%s",
                                  "actionSource":"TELEGRAM"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isCreated());

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService, times(1)).submit(captor.capture());
        IntakeCommand command = captor.getValue();
        assertThat(command.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(command.getTypeCode()).isEqualTo(WorkItemType.BUG);
        assertThat(command.getTitle()).isEqualTo("Title");
        assertThat(command.getDescription()).isEqualTo("Body");
        assertThat(command.getWorkflowDefinitionId()).isEqualTo(WORKFLOW_DEFINITION_ID);
        assertThat(command.getInitialStatusCode()).isEqualTo("BUGS");
        assertThat(command.getCreatedByUserId()).isEqualTo(CREATED_BY_USER_ID);
        assertThat(command.getActionSource()).isEqualTo("TELEGRAM");
    }

    @Test
    void submitInvalidTypeCodeReturns400WithoutDelegating() throws Exception {
        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"FEATURE",
                                  "title":"Some title",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(intakeApplicationService);
    }

    @Test
    void submitIntakeValidationBubblesAs422() throws Exception {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("INTAKE_VALIDATION",
                        "title bo'sh bo'lishi mumkin emas"));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INTAKE_VALIDATION"));
    }

    @Test
    void submitResourceNotFoundBubblesAs404() throws Exception {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new ResourceNotFoundException("WorkflowDefinition", WORKFLOW_DEFINITION_ID));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "typeCode":"BUG",
                                  "title":"Login broken",
                                  "workflowDefinitionId":"%s",
                                  "createdByUserId":"%s",
                                  "actionSource":"MANUAL"
                                }
                                """.formatted(TENANT_ID, WORKFLOW_DEFINITION_ID, CREATED_BY_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void submitEmptyJsonBodyDelegatesAndBubblesValidation422() throws Exception {
        // Empty {} — Jackson barcha field'larni null qiladi.
        // Controller IntakeCommand'ni tuzadi va submit'ga uzatadi.
        // IntakeApplicationService.validateCommand "tenantId majburiy" → 422 INTAKE_VALIDATION.
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("INTAKE_VALIDATION", "tenantId majburiy"));

        mockMvc.perform(post("/api/intake/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INTAKE_VALIDATION"));
    }
}
