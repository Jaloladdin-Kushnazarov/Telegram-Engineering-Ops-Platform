package com.engops.platform.identity.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ruxsat (permission) — tizim darajasida aniqlanadi.
 * Masalan: WORK_ITEM_CREATE, TENANT_MANAGE.
 */
@Entity
@Table(name = "permission")
public class Permission extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Size(max = 500)
    @Column(name = "description")
    private String description;

    protected Permission() {}

    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
