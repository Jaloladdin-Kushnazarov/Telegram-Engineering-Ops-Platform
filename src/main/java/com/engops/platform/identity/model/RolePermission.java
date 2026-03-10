package com.engops.platform.identity.model;

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
 * Rol va ruxsat o'rtasidagi bog'lanish.
 */
@Entity
@Table(name = "role_permission")
public class RolePermission {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected RolePermission() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public RolePermission(Role role, Permission permission) {
        this();
        this.role = role;
        this.permission = permission;
    }

    public UUID getId() { return id; }
    public Role getRole() { return role; }
    public Permission getPermission() { return permission; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RolePermission that = (RolePermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
