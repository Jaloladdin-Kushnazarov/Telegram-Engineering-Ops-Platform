package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.AccessDeniedException;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 203 — {@link ErrorIngestionService} unit testlari.
 *
 * IntakeApplicationService va AuditService mock. Severity derivation matrix,
 * title/description truncation, validation, audit invariant qoplanadi.
 */
class ErrorIngestionServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORK_ITEM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORKFLOW_DEF_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final IntakeApplicationService intakeApplicationService =
            mock(IntakeApplicationService.class);
    private final AuditService auditService = mock(AuditService.class);

    private final ErrorIngestionService service = new ErrorIngestionService(
            intakeApplicationService, auditService);

    @BeforeEach
    void setUp() {
        when(intakeApplicationService.submit(any(IntakeCommand.class))).thenReturn(
                new IntakeResult(
                        WORK_ITEM_ID, "INC-1", "INCIDENT", "any title",
                        "REPORTED", WORKFLOW_DEF_ID, TENANT_ID,
                        null, null, null,
                        false, null, null, null, null));
    }

    private ErrorIngestionCommand basic(String sourceService, String errorMessage) {
        return new ErrorIngestionCommand(
                TENANT_ID, sourceService, errorMessage,
                null, null, null, null, List.of(), ACTOR_ID);
    }

    // ========== Happy paths ==========

    @Test
    void happyPath_minimalRequest_createsIncidentWorkItem() {
        ErrorIngestionResult result = service.ingest(basic("payment-api", "NPE"));

        assertThat(result.workItemId()).isEqualTo(WORK_ITEM_ID);
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.workItemType()).isEqualTo("INCIDENT");
        assertThat(result.severityCode()).isEqualTo("MEDIUM"); // no hint, no http
        assertThat(result.statusCode()).isEqualTo("REPORTED");

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        IntakeCommand sent = captor.getValue();
        assertThat(sent.getTypeCode()).isEqualTo(WorkItemType.INCIDENT);
        assertThat(sent.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(sent.getCreatedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(sent.getActionSource()).isEqualTo("INTAKE_API");
        assertThat(sent.getTitle()).isEqualTo("[payment-api] NPE");
        assertThat(sent.getSeverityCode()).isEqualTo("MEDIUM");
    }

    @Test
    void happyPath_withStackTrace_includesInDescription() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "payment-api", "NPE",
                "java.lang.NullPointerException\n  at Foo.bar(Foo.java:42)",
                null, null, null, List.of(), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getDescription())
                .contains("Stack trace:")
                .contains("at Foo.bar(Foo.java:42)");
    }

    @Test
    void happyPath_withTags_tagCountInAudit_butValuesNotInAuditPayload() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "payment-api", "NPE",
                null, null, null, null,
                List.of("oncall:bob", "release:1.2.3", "region:eu"), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<String> auditPayload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEvent(
                eq(TENANT_ID), eq("WORK_ITEM"), eq(WORK_ITEM_ID),
                eq("ERROR_INGESTED"), eq(ACTOR_ID), eq("INTAKE_API"),
                eq(null), auditPayload.capture());
        String payload = auditPayload.getValue();
        assertThat(payload).contains("\"tagCount\":3");
        assertThat(payload).doesNotContain("oncall:bob");
        assertThat(payload).doesNotContain("release:1.2.3");
        assertThat(payload).doesNotContain("region:eu");
    }

    @Test
    void happyPath_withEnvironment_includesInTitlePrefix() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "payment-api", "NPE",
                null, null, null, "production", List.of(), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("[production][payment-api] NPE");
    }

    // ========== Severity derivation matrix ==========

    @Test
    void severityDerived_fromHttp5xx_isCritical() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "api", "boom", null, null, 503, null, List.of(), ACTOR_ID);
        ErrorIngestionResult result = service.ingest(cmd);
        assertThat(result.severityCode()).isEqualTo("CRITICAL");
    }

    @Test
    void severityDerived_fromHttp4xx_isHigh() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "api", "boom", null, null, 404, null, List.of(), ACTOR_ID);
        assertThat(service.ingest(cmd).severityCode()).isEqualTo("HIGH");
    }

    @Test
    void severityDerived_fromHttp3xx_isMedium() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "api", "boom", null, null, 301, null, List.of(), ACTOR_ID);
        assertThat(service.ingest(cmd).severityCode()).isEqualTo("MEDIUM");
    }

    @Test
    void severityHint_overridesHttpStatus() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "api", "boom", null, "LOW", 503, null, List.of(), ACTOR_ID);
        assertThat(service.ingest(cmd).severityCode()).isEqualTo("LOW");
    }

    @Test
    void severityDefault_MEDIUM_whenNoHintAndNoHttp() {
        assertThat(service.ingest(basic("api", "boom")).severityCode()).isEqualTo("MEDIUM");
    }

    // ========== Truncation ==========

    @Test
    void titleTruncated_at200Chars() {
        String longMessage = "x".repeat(300);
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "svc", longMessage, null, null, null, null, List.of(), ACTOR_ID);

        // Service should reject before truncation kicks in (errorMessage capped at 500),
        // so build a message under 500 that still pushes title past 200.
        String safeMessage = "x".repeat(400);
        cmd = new ErrorIngestionCommand(
                TENANT_ID, "svc", safeMessage, null, null, null, null, List.of(), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getTitle().length()).isEqualTo(200);
    }

    @Test
    void descriptionTruncated_at5000Chars() {
        String hugeStack = "y".repeat(4900);
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "svc", "msg", hugeStack, null, null, "prod",
                List.of("t1", "t2"), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<IntakeCommand> captor = ArgumentCaptor.forClass(IntakeCommand.class);
        verify(intakeApplicationService).submit(captor.capture());
        assertThat(captor.getValue().getDescription().length()).isLessThanOrEqualTo(5000);
    }

    // ========== Sad paths ==========

    @Test
    void unauthorizedActor_throwsAccessDenied_andNothingPersistedBeyondSubmit() {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new AccessDeniedException("WORK_ITEM_CREATE talab qilinadi"));

        assertThatThrownBy(() -> service.ingest(basic("svc", "msg")))
                .isInstanceOf(AccessDeniedException.class);

        verify(auditService, never()).recordEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void blankSourceService_throwsBusinessRule_INVALID_SOURCE_SERVICE() {
        assertThatThrownBy(() -> service.ingest(basic("", "msg")))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_SOURCE_SERVICE".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void blankErrorMessage_throwsBusinessRule_INVALID_ERROR_MESSAGE() {
        assertThatThrownBy(() -> service.ingest(basic("svc", "")))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_ERROR_MESSAGE".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void invalidSeverityHint_throwsBusinessRule_INVALID_SEVERITY_HINT() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "svc", "msg", null, "WEIRD", null, null, List.of(), ACTOR_ID);
        assertThatThrownBy(() -> service.ingest(cmd))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_SEVERITY_HINT".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void tooManyTags_throwsBusinessRule_TOO_MANY_TAGS() {
        List<String> tags = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "t" + i).toList();
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "svc", "msg", null, null, null, null, tags, ACTOR_ID);
        assertThatThrownBy(() -> service.ingest(cmd))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "TOO_MANY_TAGS".equals(((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void noIncidentWorkflowSeeded_translatesTo_NO_INCIDENT_WORKFLOW() {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("NO_ACTIVE_WORKFLOW",
                        "'INCIDENT' turi uchun aktiv workflow ta'rifi topilmadi"));

        assertThatThrownBy(() -> service.ingest(basic("svc", "msg")))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "NO_INCIDENT_WORKFLOW".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void downstreamBusinessRuleOtherThanNoActiveWorkflow_propagatesAsIs() {
        when(intakeApplicationService.submit(any(IntakeCommand.class)))
                .thenThrow(new BusinessRuleException("INTAKE_VALIDATION", "boom"));

        assertThatThrownBy(() -> service.ingest(basic("svc", "msg")))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INTAKE_VALIDATION".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    // ========== Audit invariant ==========

    @Test
    void auditPayload_containsSourceServiceSeverityHttpAndTagCount_butNotErrorMessageOrStackTrace() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                TENANT_ID, "payment-api", "very-private-error-message",
                "secret-stack-trace-with-internal-paths",
                "HIGH", 503, "prod", List.of("oncall:bob"), ACTOR_ID);

        service.ingest(cmd);

        ArgumentCaptor<String> auditPayload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEvent(
                any(), any(), any(), any(), any(), any(), any(), auditPayload.capture());
        String payload = auditPayload.getValue();

        assertThat(payload).contains("\"sourceService\":\"payment-api\"");
        assertThat(payload).contains("\"severityCode\":\"HIGH\"");
        assertThat(payload).contains("\"httpStatusCode\":503");
        assertThat(payload).contains("\"tagCount\":1");

        assertThat(payload).doesNotContain("very-private-error-message");
        assertThat(payload).doesNotContain("secret-stack-trace");
        assertThat(payload).doesNotContain("oncall:bob");
    }

    @Test
    void blankTenantId_throwsBusinessRule_INVALID_TENANT_ID() {
        ErrorIngestionCommand cmd = new ErrorIngestionCommand(
                null, "svc", "msg", null, null, null, null, List.of(), ACTOR_ID);
        assertThatThrownBy(() -> service.ingest(cmd))
                .isInstanceOf(BusinessRuleException.class)
                .matches(e -> "INVALID_TENANT_ID".equals(
                        ((BusinessRuleException) e).getErrorCode()));
    }

    @Test
    void emptyTags_noTagCountFalsehood_payloadShows0() {
        service.ingest(basic("svc", "msg"));
        ArgumentCaptor<String> auditPayload = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordEvent(
                any(), any(), any(), any(), any(), any(), any(), auditPayload.capture());
        assertThat(auditPayload.getValue()).contains("\"tagCount\":0");
    }
}
