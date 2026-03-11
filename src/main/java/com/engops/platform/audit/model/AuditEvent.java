package com.engops.platform.audit.model;

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
 * Audit event — biznes uchun muhim o'zgarishlarni qayd qiladi.
 * Append-only — yaratilgandan keyin o'zgartirilmaydi.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Size(max = 50)
    @Column(name = "action_source")
    private String actionSource;

    @Column(name = "old_value_json", columnDefinition = "TEXT")
    private String oldValueJson;

    @Column(name = "new_value_json", columnDefinition = "TEXT")
    private String newValueJson;

    @Size(max = 255)
    @Column(name = "correlation_id")
    private String correlationId;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public AuditEvent(UUID tenantId, String entityType, UUID entityId,
                       String eventType, UUID actorUserId) {
        this();
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getEventType() { return eventType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActionSource() { return actionSource; }
    public void setActionSource(String actionSource) { this.actionSource = actionSource; }
    public String getOldValueJson() { return oldValueJson; }
    public void setOldValueJson(String oldValueJson) { this.oldValueJson = oldValueJson; }
    public String getNewValueJson() { return newValueJson; }
    public void setNewValueJson(String newValueJson) { this.newValueJson = newValueJson; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
