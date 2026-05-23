package com.engops.platform.tenantconfig.model;

import com.engops.platform.workitem.model.WorkItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 198 — global, tenant-agnostic workflow template katalog yozuvi.
 *
 * Bu katalog tizim darajasidagi shablonlar to'plami; tenant ichidagi
 * workflow_definition / workflow_status / workflow_transition_rule
 * qatorlaridan farqli ravishda tenant_id ustuni yo'q. Qatorlar faqat
 * Flyway V7 migratsiyasining seed bloki orqali yaratiladi — app kodida
 * yozuv (write) servisi yo'q. Phase 199 onboarding endpoint shu shablonni
 * o'qib har bir yangi tenant uchun tenant-scoped workflow_definition
 * qatorlarini yaratadi.
 */
@Entity
@Table(name = "workflow_template")
public class WorkflowTemplate {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", updatable = false, nullable = false, unique = true)
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 1000)
    @Column(name = "description")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "work_item_type", updatable = false, nullable = false, length = 50)
    private WorkItemType workItemType;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected WorkflowTemplate() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public WorkflowTemplate(String code, String name, WorkItemType workItemType, String description) {
        this();
        this.code = code;
        this.name = name;
        this.workItemType = workItemType;
        this.description = description;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public WorkItemType getWorkItemType() { return workItemType; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTemplate that = (WorkflowTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
