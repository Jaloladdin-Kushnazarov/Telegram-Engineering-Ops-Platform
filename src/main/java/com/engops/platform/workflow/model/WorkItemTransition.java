package com.engops.platform.workflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Work item status o'tish tarixi.
 * Har bir holat o'zgarishi qayd qilinadi — append-only.
 */
@Entity
@Table(name = "work_item_transition")
public class WorkItemTransition {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "from_status_code", nullable = false)
    private String fromStatusCode;

    @NotBlank
    @Size(max = 100)
    @Column(name = "to_status_code", nullable = false)
    private String toStatusCode;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "action_source", nullable = false)
    private String actionSource = "MANUAL";

    @Column(name = "transition_reason", columnDefinition = "TEXT")
    private String transitionReason;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected WorkItemTransition() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public WorkItemTransition(UUID tenantId, UUID workItemId, String fromStatusCode,
                               String toStatusCode, UUID actorUserId, String actionSource) {
        this();
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.fromStatusCode = fromStatusCode;
        this.toStatusCode = toStatusCode;
        this.actorUserId = actorUserId;
        this.actionSource = actionSource;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public String getFromStatusCode() { return fromStatusCode; }
    public String getToStatusCode() { return toStatusCode; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActionSource() { return actionSource; }
    public String getTransitionReason() { return transitionReason; }
    public void setTransitionReason(String transitionReason) { this.transitionReason = transitionReason; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkItemTransition that = (WorkItemTransition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
