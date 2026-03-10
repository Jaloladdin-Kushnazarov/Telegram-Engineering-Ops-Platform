package com.engops.platform.tenantconfig.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Workflow ichidagi holat (status).
 * Masalan: BUGS, PROCESSING, TESTING, FIXED.
 */
@Entity
@Table(name = "workflow_status")
public class WorkflowStatus {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status_order", nullable = false)
    private int statusOrder;

    @Column(name = "initial", nullable = false)
    private boolean initial;

    @Column(name = "terminal", nullable = false)
    private boolean terminal;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected WorkflowStatus() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public WorkflowStatus(WorkflowDefinition definition, String name, int statusOrder,
                           boolean initial, boolean terminal) {
        this();
        this.workflowDefinition = definition;
        this.name = name;
        this.statusOrder = statusOrder;
        this.initial = initial;
        this.terminal = terminal;
    }

    public UUID getId() { return id; }
    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public String getName() { return name; }
    public int getStatusOrder() { return statusOrder; }
    public boolean isInitial() { return initial; }
    public boolean isTerminal() { return terminal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowStatus that = (WorkflowStatus) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
