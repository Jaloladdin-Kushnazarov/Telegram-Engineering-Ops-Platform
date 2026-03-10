package com.engops.platform.identity.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rol — global katalog.
 * Rollar tenant'ga bog'liq emas, ular tizim darajasida aniqlanadi.
 * Tenant a'zolariga membership_role_binding orqali tayinlanadi.
 *
 * code — barqaror (stable) identifikator, kodda ishlatiladi (masalan: "ADMIN").
 * name — ko'rsatish uchun nom (masalan: "Administrator").
 * system_role = true bo'lsa — tizim tomonidan yaratilgan, o'chirib bo'lmaydi.
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "description")
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    protected Role() {}

    public Role(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Role(String code, String name, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.systemRole = systemRole;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
}
