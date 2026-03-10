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
 * A'zolik va rol o'rtasidagi bog'lanish.
 * Rollar foydalanuvchiga emas, balki membership orqali tayinlanadi.
 */
@Entity
@Table(name = "membership_role_binding")
public class MembershipRoleBinding {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected MembershipRoleBinding() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public MembershipRoleBinding(Membership membership, Role role) {
        this();
        this.membership = membership;
        this.role = role;
    }

    public UUID getId() { return id; }
    public Membership getMembership() { return membership; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MembershipRoleBinding that = (MembershipRoleBinding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
