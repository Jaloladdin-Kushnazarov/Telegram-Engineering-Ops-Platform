package com.engops.platform.tenantconfig.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Workflow ta'rifi — tenant ichida work item turlari uchun workflow konfiguratsiyasi.
 * Bu faqat metadata, execution engine emas.
 */
@Entity
@Table(name = "workflow_definition", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
public class WorkflowDefinition extends BaseEntity {

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 50)
    @Column(name = "work_item_type", nullable = false)
    private String workItemType;

    @Size(max = 1000)
    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "workflowDefinition", fetch = FetchType.LAZY)
    private List<WorkflowStatus> statuses = new ArrayList<>();

    @OneToMany(mappedBy = "workflowDefinition", fetch = FetchType.LAZY)
    private List<WorkflowTransitionRule> transitionRules = new ArrayList<>();

    protected WorkflowDefinition() {}

    public WorkflowDefinition(UUID tenantId, String name, String workItemType) {
        this.tenantId = tenantId;
        this.name = name;
        this.workItemType = workItemType;
    }

    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorkItemType() { return workItemType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<WorkflowStatus> getStatuses() { return Collections.unmodifiableList(statuses); }
    public List<WorkflowTransitionRule> getTransitionRules() { return Collections.unmodifiableList(transitionRules); }
}
