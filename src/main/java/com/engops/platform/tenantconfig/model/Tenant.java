package com.engops.platform.tenantconfig.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Tashkilot (tenant) — platformadagi asosiy tashkiliy birlik.
 * Har bir tenant o'z foydalanuvchilari, workflow va konfiguratsiyasiga ega.
 *
 * timezone — tenant uchun vaqt zonasi (analytics va operational day uchun kerak).
 */
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @NotBlank
    @Size(max = 50)
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    protected Tenant() {}

    public Tenant(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.status = TenantStatus.ACTIVE;
    }

    public Tenant(UUID id, String name, String slug) {
        super(id);
        this.name = name;
        this.slug = slug;
        this.status = TenantStatus.ACTIVE;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public boolean isActive() { return status == TenantStatus.ACTIVE; }
}
