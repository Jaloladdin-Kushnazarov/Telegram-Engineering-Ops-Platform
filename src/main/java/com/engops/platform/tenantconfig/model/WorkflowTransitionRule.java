package com.engops.platform.tenantconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Workflow holat o'tish qoidasi.
 * Qaysi holatdan qaysi holatga o'tish mumkinligini belgilaydi.
 */
@Entity
@Table(name = "workflow_transition_rule")
public class WorkflowTransitionRule {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id", nullable = false)
    private WorkflowStatus fromStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", nullable = false)
    private WorkflowStatus toStatus;

    @Column(name = "required_permission_id")
    private UUID requiredPermissionId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected WorkflowTransitionRule() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public WorkflowTransitionRule(WorkflowDefinition definition, WorkflowStatus fromStatus,
                                   WorkflowStatus toStatus) {
        this();
        this.workflowDefinition = definition;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public UUID getId() { return id; }
    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public WorkflowStatus getFromStatus() { return fromStatus; }
    public WorkflowStatus getToStatus() { return toStatus; }
    public UUID getRequiredPermissionId() { return requiredPermissionId; }
    public void setRequiredPermissionId(UUID requiredPermissionId) { this.requiredPermissionId = requiredPermissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTransitionRule that = (WorkflowTransitionRule) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
