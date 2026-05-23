package com.engops.platform.intake;

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
 * Phase 203 — {@link ErrorIngestionController} @WebMvcTest testlari.
 *
 * Mirror IntakeControllerTest pattern: RequestAttribute SecurityContext;
 * mocked ErrorIngestionService; standard envelope assertions.
 */
@WebMvcTest(ErrorIngestionController.class)
@Import({SecurityConfig.class, SecurityWebMvcConfig.class})
class ErrorIngestionControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORK_ITEM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ErrorIngestionService errorIngestionService;

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

    private static String validBody(String severityHint, Integer httpStatus) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"tenantId\":\"").append(TENANT_ID).append("\",");
        sb.append("\"sourceService\":\"payment-api\",");
        sb.append("\"errorMessage\":\"NullPointerException\"");
        if (severityHint != null) sb.append(",\"severityHint\":\"").append(severityHint).append("\"");
        if (httpStatus != null) sb.append(",\"httpStatusCode\":").append(httpStatus);
        sb.append("}");
        return sb.toString();
    }

    private ErrorIngestionResult resultWith(String severity) {
        return new ErrorIngestionResult(
                WORK_ITEM_ID, TENANT_ID,
                "[payment-api] NullPointerException", "INCIDENT",
                severity, "REPORTED",
                Instant.parse("2026-05-23T00:00:00Z"));
    }

    // ========== Happy paths ==========

    @Test
    void submit_validRequest_returns201AndEchoesIds() throws Exception {
        when(errorIngestionService.ingest(any(ErrorIngestionCommand.class)))
                .thenReturn(resultWith("MEDIUM"));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.workItemId").value(WORK_ITEM_ID.toString()))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.workItemType").value("INCIDENT"))
                .andExpect(jsonPath("$.severityCode").value("MEDIUM"))
                .andExpect(jsonPath("$.statusCode").value("REPORTED"));
    }

    @Test
    void submit_5xxHttpStatus_severityIsCritical() throws Exception {
        when(errorIngestionService.ingest(any(ErrorIngestionCommand.class)))
                .thenReturn(resultWith("CRITICAL"));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, 503)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severityCode").value("CRITICAL"));
    }

    @Test
    void submit_4xxHttpStatus_severityIsHigh() throws Exception {
        when(errorIngestionService.ingest(any(ErrorIngestionCommand.class)))
                .thenReturn(resultWith("HIGH"));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, 404)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severityCode").value("HIGH"));
    }

    @Test
    void submit_severityHintOverridesHttpStatus() throws Exception {
        // Service returns LOW regardless of HTTP — the controller doesn't decide.
        when(errorIngestionService.ingest(any(ErrorIngestionCommand.class)))
                .thenReturn(resultWith("LOW"));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("LOW", 503)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severityCode").value("LOW"));
    }

    // ========== Sad paths ==========

    @Test
    void submit_unauthorizedActor_returns403_envelope() throws Exception {
        doThrow(new AccessDeniedException("Bu operatsiya uchun WORK_ITEM_CREATE talab qilinadi"))
                .when(errorIngestionService).ingest(any(ErrorIngestionCommand.class));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void submit_blankSourceService_returns422_envelope() throws Exception {
        doThrow(new BusinessRuleException("INVALID_SOURCE_SERVICE", "blank"))
                .when(errorIngestionService).ingest(any(ErrorIngestionCommand.class));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","sourceService":"","errorMessage":"NPE"}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SOURCE_SERVICE"));
    }

    @Test
    void submit_blankErrorMessage_returns422_envelope() throws Exception {
        doThrow(new BusinessRuleException("INVALID_ERROR_MESSAGE", "blank"))
                .when(errorIngestionService).ingest(any(ErrorIngestionCommand.class));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","sourceService":"svc","errorMessage":""}
                                """.formatted(TENANT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ERROR_MESSAGE"));
    }

    @Test
    void submit_invalidSeverityHint_returns422_envelope() throws Exception {
        doThrow(new BusinessRuleException("INVALID_SEVERITY_HINT", "weird"))
                .when(errorIngestionService).ingest(any(ErrorIngestionCommand.class));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("WEIRD", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SEVERITY_HINT"));
    }

    @Test
    void submit_noIncidentWorkflow_returns422_envelope() throws Exception {
        doThrow(new BusinessRuleException("NO_INCIDENT_WORKFLOW", "no workflow"))
                .when(errorIngestionService).ingest(any(ErrorIngestionCommand.class));

        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_INCIDENT_WORKFLOW"));
    }

    @Test
    void submit_missingBody_returns400_envelope() throws Exception {
        mockMvc.perform(post("/api/intake/errors")
                        .with(withActor(ACTOR_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(errorIngestionService);
    }

    @Test
    void submit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/intake/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(null, null)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(errorIngestionService);
    }
}
