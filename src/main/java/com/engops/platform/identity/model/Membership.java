package com.engops.platform.identity.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A'zolik — foydalanuvchini tenantga bog'laydi.
 * Bitta foydalanuvchi bir nechta tenantda a'zo bo'lishi mumkin,
 * lekin bitta tenantda faqat bitta a'zolik bo'ladi (tenant_id + user_id unique).
 */
@Entity
@Table(name = "membership", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "user_id"}))
public class Membership extends BaseEntity {

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    protected Membership() {}

    public Membership(UUID tenantId, UUID userId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.status = MembershipStatus.ACTIVE;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus status) { this.status = status; }
    public boolean isActive() { return status == MembershipStatus.ACTIVE; }
}
