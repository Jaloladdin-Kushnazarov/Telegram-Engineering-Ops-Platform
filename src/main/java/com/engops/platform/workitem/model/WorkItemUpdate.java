package com.engops.platform.workitem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Work item uchun tizimli yangilanish — izoh, status o'zgarishi va h.k.
 * Append-only — yaratilgandan keyin o'zgartirilmaydi.
 */
@Entity
@Table(name = "work_item_update")
public class WorkItemUpdate {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "update_type_code", nullable = false)
    private UpdateType updateTypeCode;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_code", nullable = false)
    private Visibility visibilityCode = Visibility.INTERNAL;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected WorkItemUpdate() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public WorkItemUpdate(UUID tenantId, UUID workItemId, UUID authorUserId,
                           UpdateType updateTypeCode, String body) {
        this();
        this.tenantId = tenantId;
        this.workItemId = workItemId;
        this.authorUserId = authorUserId;
        this.updateTypeCode = updateTypeCode;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public UpdateType getUpdateTypeCode() { return updateTypeCode; }
    public String getBody() { return body; }
    public Visibility getVisibilityCode() { return visibilityCode; }
    public void setVisibilityCode(Visibility visibilityCode) { this.visibilityCode = visibilityCode; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkItemUpdate that = (WorkItemUpdate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
