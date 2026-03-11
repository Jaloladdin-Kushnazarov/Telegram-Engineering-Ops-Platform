package com.engops.platform.workitem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Work item kod generatsiyasi uchun counter.
 * Har bir tenant va type uchun alohida counter saqlanadi.
 *
 * Nima uchun COUNT+1 emas:
 * COUNT+1 — concurrent yaratishda xavfli: ikkita so'rov bir vaqtda COUNT=5
 * ni o'qib, ikkalasi ham BUG-6 generatsiya qiladi. Unique constraint
 * bittasini rad etadi, lekin foydalanuvchiga xato qaytaradi.
 *
 * Counter table — row-level lock bilan sequential raqam beradi.
 * Throughput cheklovi bor (bitta tenant+type uchun bir vaqtda faqat bitta yaratish),
 * lekin MVP uchun bu to'g'ri va xavfsiz.
 */
@Entity
@Table(name = "work_item_counter",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "type_code"}))
public class WorkItemCounter {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_code", nullable = false)
    private WorkItemType typeCode;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected WorkItemCounter() {}

    public WorkItemCounter(UUID tenantId, WorkItemType typeCode) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.typeCode = typeCode;
        this.nextValue = 1;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public WorkItemType getTypeCode() { return typeCode; }
    public long getNextValue() { return nextValue; }

    /**
     * Keyingi qiymatni qaytaradi va counterni oshiradi.
     */
    public long incrementAndGet() {
        long current = this.nextValue;
        this.nextValue = current + 1;
        return current;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkItemCounter that = (WorkItemCounter) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
