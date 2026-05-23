package com.engops.platform.intake;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.workitem.model.WorkItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Phase 203 — error ingestion orchestrator.
 *
 * <p>{@code POST /api/intake/errors} endpoint'i ortidagi atomik service.
 * Mavjud {@link IntakeApplicationService#submit} use-case'ini qayta ishlatadi:
 * INCIDENT type'i bilan WorkItem yaratiladi, severity HTTP status / hint dan
 * derive qilinadi, title sourceService va errorMessage'dan, description
 * stack trace + tag'lar bilan to'ldiriladi.</p>
 *
 * <p><strong>Authorization:</strong> {@link IntakeApplicationService} ichida
 * {@code authorizeIntake(tenantId, actorUserId)} bajariladi
 * ({@code WORK_ITEM_CREATE} ruxsati). Yangi permission kiritilmaydi
 * (Phase 203 D4 / D13).</p>
 *
 * <p><strong>Audit:</strong> mavjud Phase 195+ {@code WORK_ITEM_CREATED}
 * audit qatori intake service tomonidan yoziladi. Bu service qo'shimcha
 * BIR qator yozadi: {@code ERROR_INGESTED} (entity_type=WORK_ITEM).
 * Payload bounded — faqat sourceService + severityCode + httpStatusCode +
 * tagCount. errorMessage va stackTrace AUDIT'GA HECH QACHON kirmaydi
 * (D11, D15 — sensitive data: internal endpoint'lar / secret'lar / PII).</p>
 *
 * <p><strong>Atomiklik:</strong> class-level {@code @Transactional}; har
 * qanday xatolik (validation, authorization, downstream submit, audit
 * write) butun ingestion'ni rollback qiladi.</p>
 */
@Service
@Transactional
public class ErrorIngestionService {

    private static final Set<String> ALLOWED_SEVERITIES =
            Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    static final String ACTION_SOURCE = "INTAKE_API";
    static final String AUDIT_EVENT_ERROR_INGESTED = "ERROR_INGESTED";

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 5000;
    static final int MAX_SOURCE_SERVICE_LENGTH = 100;
    static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    static final int MAX_STACK_TRACE_LENGTH = 5000;
    static final int MAX_ENVIRONMENT_LENGTH = 50;
    static final int MAX_TAG_VALUE_LENGTH = 50;
    static final int MAX_TAG_COUNT = 10;

    private final IntakeApplicationService intakeApplicationService;
    private final AuditService auditService;

    public ErrorIngestionService(IntakeApplicationService intakeApplicationService,
                                  AuditService auditService) {
        this.intakeApplicationService = intakeApplicationService;
        this.auditService = auditService;
    }

    public ErrorIngestionResult ingest(ErrorIngestionCommand command) {
        // 1. Validate inputs (service-layer authoritative).
        validate(command);

        // 2. Derive severity, title, description (bounded transforms).
        String severityCode = deriveSeverity(command);
        String title = deriveTitle(command);
        String description = deriveDescription(command, severityCode);

        // 3. Build IntakeCommand and delegate to existing submit() use-case.
        //    INCIDENT type forces the INCIDENT workflow lookup (D6/D16).
        //    Authorization (WORK_ITEM_CREATE) bajariladi submit() ichida.
        IntakeCommand intakeCommand = IntakeCommand.builder()
                .tenantId(command.tenantId())
                .typeCode(WorkItemType.INCIDENT)
                .title(title)
                .description(description)
                .createdByUserId(command.actorUserId())
                .actionSource(ACTION_SOURCE)
                .severityCode(severityCode)
                .build();

        IntakeResult intakeResult;
        try {
            intakeResult = intakeApplicationService.submit(intakeCommand);
        } catch (BusinessRuleException ex) {
            // Translate intake's NO_ACTIVE_WORKFLOW to Phase 203's contract
            // (D16): NO_INCIDENT_WORKFLOW conveys the specific shape.
            if ("NO_ACTIVE_WORKFLOW".equals(ex.getErrorCode())) {
                throw new BusinessRuleException("NO_INCIDENT_WORKFLOW",
                        "Tenant uchun INCIDENT type'idagi aktiv workflow topilmadi");
            }
            throw ex;
        }

        // 4. Audit ERROR_INGESTED — bounded payload (D11/D15).
        int tagCount = command.tags() == null ? 0 : command.tags().size();
        Integer httpStatus = command.httpStatusCode();
        String auditPayload = "{\"sourceService\":\"" + escape(command.sourceService())
                + "\",\"severityCode\":\"" + severityCode
                + "\",\"httpStatusCode\":" + (httpStatus == null ? "null" : httpStatus)
                + ",\"tagCount\":" + tagCount + "}";

        auditService.recordEvent(
                command.tenantId(), "WORK_ITEM", intakeResult.getWorkItemId(),
                AUDIT_EVENT_ERROR_INGESTED, command.actorUserId(),
                ACTION_SOURCE, null, auditPayload);

        // 5. Build result.  createdAt is captured as Instant.now() at service
        //    entry — IntakeResult does not carry the persisted createdAt and
        //    re-reading the WorkItem would add a round-trip; the value
        //    represents the ingestion moment, consistent with operator intent.
        return new ErrorIngestionResult(
                intakeResult.getWorkItemId(),
                intakeResult.getTenantId(),
                intakeResult.getTitle(),
                intakeResult.getWorkItemType(),
                severityCode,
                intakeResult.getCurrentStatusCode(),
                Instant.now());
    }

    // ========== Validation ==========

    private void validate(ErrorIngestionCommand command) {
        if (command.tenantId() == null) {
            throw new BusinessRuleException("INVALID_TENANT_ID", "tenantId majburiy");
        }
        validateSourceService(command.sourceService());
        validateErrorMessage(command.errorMessage());
        validateSeverityHint(command.severityHint());
        validateStackTrace(command.errorStackTrace());
        validateEnvironment(command.environment());
        validateTags(command.tags());
    }

    private void validateSourceService(String s) {
        if (s == null || s.isBlank()) {
            throw new BusinessRuleException("INVALID_SOURCE_SERVICE",
                    "sourceService bo'sh bo'la olmaydi");
        }
        if (s.length() > MAX_SOURCE_SERVICE_LENGTH) {
            throw new BusinessRuleException("INVALID_SOURCE_SERVICE",
                    "sourceService " + MAX_SOURCE_SERVICE_LENGTH + " belgidan oshmasligi shart");
        }
    }

    private void validateErrorMessage(String s) {
        if (s == null || s.isBlank()) {
            throw new BusinessRuleException("INVALID_ERROR_MESSAGE",
                    "errorMessage bo'sh bo'la olmaydi");
        }
        if (s.length() > MAX_ERROR_MESSAGE_LENGTH) {
            throw new BusinessRuleException("INVALID_ERROR_MESSAGE",
                    "errorMessage " + MAX_ERROR_MESSAGE_LENGTH + " belgidan oshmasligi shart");
        }
    }

    private void validateSeverityHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return; // optional
        }
        if (!ALLOWED_SEVERITIES.contains(hint)) {
            throw new BusinessRuleException("INVALID_SEVERITY_HINT",
                    "severityHint quyidagilardan biri bo'lishi shart: CRITICAL/HIGH/MEDIUM/LOW");
        }
    }

    private void validateStackTrace(String s) {
        if (s == null) return;
        if (s.length() > MAX_STACK_TRACE_LENGTH) {
            throw new BusinessRuleException("INVALID_STACK_TRACE",
                    "errorStackTrace " + MAX_STACK_TRACE_LENGTH + " belgidan oshmasligi shart");
        }
    }

    private void validateEnvironment(String s) {
        if (s == null || s.isBlank()) return;
        if (s.length() > MAX_ENVIRONMENT_LENGTH) {
            throw new BusinessRuleException("INVALID_ENVIRONMENT",
                    "environment " + MAX_ENVIRONMENT_LENGTH + " belgidan oshmasligi shart");
        }
    }

    private void validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return;
        if (tags.size() > MAX_TAG_COUNT) {
            throw new BusinessRuleException("TOO_MANY_TAGS",
                    "tags " + MAX_TAG_COUNT + " ta dan oshmasligi shart");
        }
        for (String t : tags) {
            if (t == null || t.isBlank()) {
                throw new BusinessRuleException("INVALID_TAG", "tags ichida bo'sh tag uchradi");
            }
            if (t.length() > MAX_TAG_VALUE_LENGTH) {
                throw new BusinessRuleException("INVALID_TAG",
                        "tag " + MAX_TAG_VALUE_LENGTH + " belgidan oshmasligi shart");
            }
        }
    }

    // ========== Derivation ==========

    private String deriveSeverity(ErrorIngestionCommand command) {
        if (command.severityHint() != null && !command.severityHint().isBlank()) {
            return command.severityHint();
        }
        Integer http = command.httpStatusCode();
        if (http != null) {
            if (http >= 500 && http <= 599) return "CRITICAL";
            if (http >= 400 && http <= 499) return "HIGH";
        }
        return "MEDIUM";
    }

    private String deriveTitle(ErrorIngestionCommand command) {
        String env = command.environment();
        String prefix;
        if (env != null && !env.isBlank()) {
            prefix = "[" + env + "][" + command.sourceService() + "] ";
        } else {
            prefix = "[" + command.sourceService() + "] ";
        }
        String full = prefix + command.errorMessage();
        if (full.length() > MAX_TITLE_LENGTH) {
            return full.substring(0, MAX_TITLE_LENGTH);
        }
        return full;
    }

    private String deriveDescription(ErrorIngestionCommand command, String severityCode) {
        String tagsJoined = (command.tags() == null || command.tags().isEmpty())
                ? "none"
                : String.join(", ", command.tags());
        String stackTrace = (command.errorStackTrace() == null || command.errorStackTrace().isBlank())
                ? "No stack trace provided"
                : command.errorStackTrace();

        StringBuilder sb = new StringBuilder(1024);
        sb.append("Source service: ").append(command.sourceService()).append('\n');
        sb.append("Error message: ").append(command.errorMessage()).append('\n');
        sb.append("Severity hint: ").append(severityCode).append('\n');
        sb.append("HTTP status: ")
                .append(command.httpStatusCode() == null ? "N/A" : command.httpStatusCode())
                .append('\n');
        sb.append("Environment: ")
                .append(command.environment() == null || command.environment().isBlank() ? "N/A" : command.environment())
                .append('\n');
        sb.append("Tags: ").append(tagsJoined).append("\n\n");
        sb.append("Stack trace:\n").append("```\n").append(stackTrace).append("\n```");

        String full = sb.toString();
        if (full.length() > MAX_DESCRIPTION_LENGTH) {
            return full.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return full;
    }

    // ========== Helpers ==========

    /**
     * Defensive JSON-string escape for audit payload — only escapes characters
     * that could break the bounded JSON shape. Tags / errorMessage are NEVER
     * passed through this method (they don't enter audit per D11/D15);
     * sourceService is bounded (max 100, alphanumeric+dash conventionally).
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
