package com.engops.platform.sharedkernel;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

/**
 * Kim tomonidan yaratilgan va o'zgartirilganligini kuzatuvchi entity bazasi.
 *
 * {@link BaseEntity}'dan farqi — bu klass qo'shimcha ravishda
 * {@code createdBy} va {@code updatedBy} maydonlarini saqlaydi.
 *
 * Qachon ishlatiladi:
 * - Biznes uchun muhim entity'larda (WorkItem, AuditEvent va h.k.)
 * - Kim qachon nima qilganini bilish kerak bo'lgan hollarda
 *
 * Qachon ishlatilMAYDI:
 * - Texnik yoki vaqtinchalik entity'larda (session, cache)
 */
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * JPA uchun kerak bo'lgan himoyalangan konstruktor.
     */
    protected AuditableEntity() {
        super();
    }

    /**
     * Berilgan ID bilan auditable entity yaratadi.
     *
     * @param id entity identifikatori
     */
    protected AuditableEntity(UUID id) {
        super(id);
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}