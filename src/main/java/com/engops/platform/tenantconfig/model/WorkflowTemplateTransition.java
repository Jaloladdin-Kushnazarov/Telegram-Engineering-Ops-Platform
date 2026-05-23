package com.engops.platform.tenantconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;

/**
 * Phase 198 — workflow template ichidagi tranziya yozuvi.
 *
 * {@code fromStatusCode} va {@code toStatusCode}
 * {@link WorkflowTemplateStatus#getStatusCode()} ga shu shablon ichida
 * ishora qiladi, lekin DB darajasida FK constraint yo'q. Composite UNIQUE
 * (template_id + fromStatusCode + toStatusCode) duplikatlarni oldini oladi.
 * Yaratish vaqtidagi status mavjudligi (insert-time integrity) — Phase 199
 * JPA service tomonida tekshiriladi.
 */
@Entity
@Table(name = "workflow_template_transition",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_workflow_template_transition_template_from_to",
               columnNames = {"template_id", "from_status_code", "to_status_code"}))
public class WorkflowTemplateTransition {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkflowTemplate template;

    @NotBlank
    @Size(max = 50)
    @Column(name = "from_status_code", updatable = false, nullable = false)
    private String fromStatusCode;

    @NotBlank
    @Size(max = 50)
    @Column(name = "to_status_code", updatable = false, nullable = false)
    private String toStatusCode;

    @NotBlank
    @Size(max = 100)
    @Column(name = "action_label", nullable = false)
    private String actionLabel;

    protected WorkflowTemplateTransition() {
        this.id = UUID.randomUUID();
    }

    public WorkflowTemplateTransition(WorkflowTemplate template, String fromStatusCode,
                                       String toStatusCode, String actionLabel) {
        this();
        this.template = template;
        this.fromStatusCode = fromStatusCode;
        this.toStatusCode = toStatusCode;
        this.actionLabel = actionLabel;
    }

    public UUID getId() { return id; }
    public WorkflowTemplate getTemplate() { return template; }
    public String getFromStatusCode() { return fromStatusCode; }
    public String getToStatusCode() { return toStatusCode; }
    public String getActionLabel() { return actionLabel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTemplateTransition that = (WorkflowTemplateTransition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
