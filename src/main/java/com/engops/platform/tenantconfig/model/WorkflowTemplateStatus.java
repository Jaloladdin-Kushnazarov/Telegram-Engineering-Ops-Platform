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
 * Phase 198 — workflow template ichidagi status yozuvi.
 *
 * Faqat Flyway V7 migratsiyasi seed bloki orqali yaratiladi. App kodida
 * yozuv (write) servisi yo'q. {@link WorkflowTemplateQueryService} orqali
 * o'qiladi.
 */
@Entity
@Table(name = "workflow_template_status",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_workflow_template_status_template_code",
               columnNames = {"template_id", "status_code"}))
public class WorkflowTemplateStatus {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkflowTemplate template;

    @NotBlank
    @Size(max = 50)
    @Column(name = "status_code", updatable = false, nullable = false)
    private String statusCode;

    @NotBlank
    @Size(max = 200)
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "status_order", nullable = false)
    private int statusOrder;

    protected WorkflowTemplateStatus() {
        this.id = UUID.randomUUID();
    }

    public WorkflowTemplateStatus(WorkflowTemplate template, String statusCode,
                                   String displayName, boolean initial, int statusOrder) {
        this();
        this.template = template;
        this.statusCode = statusCode;
        this.displayName = displayName;
        this.initial = initial;
        this.statusOrder = statusOrder;
    }

    public UUID getId() { return id; }
    public WorkflowTemplate getTemplate() { return template; }
    public String getStatusCode() { return statusCode; }
    public String getDisplayName() { return displayName; }
    public boolean isInitial() { return initial; }
    public int getStatusOrder() { return statusOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTemplateStatus that = (WorkflowTemplateStatus) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
