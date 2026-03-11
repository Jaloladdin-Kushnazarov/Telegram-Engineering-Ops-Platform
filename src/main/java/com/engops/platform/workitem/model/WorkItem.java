package com.engops.platform.workitem.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Asosiy operatsion yozuv — bug, incident yoki task.
 * Current status va owner to'g'ridan-to'g'ri shu entity'da saqlanadi (hot-path).
 * Telegram-specific maydonlar bu yerda YO'Q.
 */
@Entity
@Table(name = "work_item", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "work_item_code"}))
public class WorkItem extends BaseEntity {

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "work_item_code", nullable = false)
    private String workItemCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private WorkItemType typeCode;

    @NotNull
    @Column(name = "workflow_definition_id", nullable = false)
    private UUID workflowDefinitionId;

    @NotBlank
    @Size(max = 500)
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 50)
    @Column(name = "environment_code")
    private String environmentCode;

    @Size(max = 255)
    @Column(name = "source_service")
    private String sourceService;

    @NotBlank
    @Size(max = 100)
    @Column(name = "current_status_code", nullable = false)
    private String currentStatusCode;

    @Column(name = "current_owner_user_id")
    private UUID currentOwnerUserId;

    @Size(max = 50)
    @Column(name = "priority_code")
    private String priorityCode;

    @Size(max = 50)
    @Column(name = "severity_code")
    private String severityCode;

    @Size(max = 255)
    @Column(name = "correlation_key")
    private String correlationKey;

    @NotNull
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "last_transition_at")
    private Instant lastTransitionAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount = 0;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    protected WorkItem() {}

    public WorkItem(UUID tenantId, String workItemCode, WorkItemType typeCode,
                    UUID workflowDefinitionId, String title, String initialStatusCode,
                    UUID createdByUserId) {
        this.tenantId = tenantId;
        this.workItemCode = workItemCode;
        this.typeCode = typeCode;
        this.workflowDefinitionId = workflowDefinitionId;
        this.title = title;
        this.currentStatusCode = initialStatusCode;
        this.createdByUserId = createdByUserId;
        this.openedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getTenantId() { return tenantId; }
    public String getWorkItemCode() { return workItemCode; }
    public WorkItemType getTypeCode() { return typeCode; }
    public UUID getWorkflowDefinitionId() { return workflowDefinitionId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getEnvironmentCode() { return environmentCode; }
    public String getSourceService() { return sourceService; }
    public String getCurrentStatusCode() { return currentStatusCode; }
    public UUID getCurrentOwnerUserId() { return currentOwnerUserId; }
    public String getPriorityCode() { return priorityCode; }
    public String getSeverityCode() { return severityCode; }
    public String getCorrelationKey() { return correlationKey; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getLastTransitionAt() { return lastTransitionAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public int getReopenedCount() { return reopenedCount; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
    public boolean isArchived() { return archived; }

    // --- Biznes metodlari ---

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setEnvironmentCode(String environmentCode) { this.environmentCode = environmentCode; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public void setPriorityCode(String priorityCode) { this.priorityCode = priorityCode; }
    public void setSeverityCode(String severityCode) { this.severityCode = severityCode; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
    public void setUpdatedByUserId(UUID updatedByUserId) { this.updatedByUserId = updatedByUserId; }

    /**
     * Status o'tkazish — faqat WorkflowTransitionService orqali chaqirilishi kerak.
     */
    public void transitionTo(String newStatusCode) {
        this.currentStatusCode = newStatusCode;
        this.lastTransitionAt = Instant.now();
    }

    /**
     * Owner tayinlash.
     */
    public void assignOwner(UUID ownerUserId) {
        this.currentOwnerUserId = ownerUserId;
    }

    /**
     * Qayta ochilganda reopenedCount oshiriladi va resolvedAt tozalanadi.
     */
    public void markReopened() {
        this.reopenedCount++;
        this.resolvedAt = null;
    }

    /**
     * Yechilgan (resolved) deb belgilash.
     */
    public void markResolved() {
        this.resolvedAt = Instant.now();
    }

    /**
     * Arxivlash.
     */
    public void archive() {
        this.archived = true;
    }
}
