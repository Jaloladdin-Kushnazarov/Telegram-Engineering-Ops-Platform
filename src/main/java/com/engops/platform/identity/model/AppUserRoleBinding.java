package com.engops.platform.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 215 — Platform-level (tenantsiz) rol biriktirish.
 *
 * <p>{@link Membership} {@code tenant_id} NOT NULL talab qiladi va per-tenant
 * a'zolikni ifodalaydi. {@code PLATFORM_OWNER} hech qaysi tenantning a'zosi
 * emas — shu sababli alohida {@code app_user_role_binding} jadvali (V9
 * migration) orqali biriktiriladi.</p>
 *
 * <p>Schema (V9):</p>
 * <pre>
 * CREATE TABLE app_user_role_binding (
 *     id          UUID PRIMARY KEY,
 *     user_id     UUID NOT NULL REFERENCES app_user(id),
 *     role_id     UUID NOT NULL REFERENCES role(id),
 *     created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
 *     UNIQUE (user_id, role_id)
 * );
 * </pre>
 *
 * <p>Entity {@link MembershipRoleBinding} pattern'ini takrorlaydi:</p>
 * <ul>
 *   <li>{@link com.engops.platform.sharedkernel.BaseEntity}'ni EXTEND
 *       QILMAYDI — V9 schema'sida {@code updated_at} va {@code version}
 *       ustunlar yo'q, {@code ddl-auto=validate} buni rad etardi.</li>
 *   <li>O'z {@code @Id} va {@code created_at} maydonlari (no-arg ctor'da
 *       initialize qilinadi).</li>
 *   <li>JPA {@code @ManyToOne} relations YO'Q — faqat UUID columns,
 *       {@link Membership} pattern'iga muvofiq. {@link Role} resolve
 *       service layer'da {@code RoleRepository.findById} orqali.</li>
 * </ul>
 *
 * <p>Phase 215 PURELY ADDITIVE: bu entity hozir hech qaysi service'da
 * ishlatilmaydi. Phase 216 V10 + DevBootstrap update + authorization
 * service rewrite uni faollashtirib bootstrap admin'ga PLATFORM_OWNER
 * biriktiradi.</p>
 */
@Entity
@Table(name = "app_user_role_binding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
public class AppUserRoleBinding {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected AppUserRoleBinding() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public AppUserRoleBinding(UUID userId, UUID roleId) {
        this();
        this.userId = Objects.requireNonNull(userId, "userId null bo'lishi mumkin emas");
        this.roleId = Objects.requireNonNull(roleId, "roleId null bo'lishi mumkin emas");
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppUserRoleBinding that = (AppUserRoleBinding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
