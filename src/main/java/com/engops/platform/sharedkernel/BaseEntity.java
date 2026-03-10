package com.engops.platform.sharedkernel;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Barcha JPA entity'lar uchun bazaviy (ota) klass.
 *
 * Bu klass quyidagilarni ta'minlaydi:
 * - {@code id}: har bir entity'ning UUID asosidagi yagona identifikatori
 * - {@code createdAt}: entity birinchi marta saqlangan vaqt (avtomatik to'ldiriladi)
 * - {@code updatedAt}: entity oxirgi marta o'zgartirilgan vaqt (avtomatik to'ldiriladi)
 * - {@code version}: optimistic locking uchun versiya raqami
 *
 * Optimistic locking nima:
 * Ikki foydalanuvchi bir vaqtda bitta yozuvni o'zgartirishga harakat qilganda,
 * version orqali konflikt aniqlanadi va ikkinchi o'zgartirish rad etiladi.
 * Bu multi-tenant tizimda ma'lumot yaxlitligini saqlash uchun juda muhim.
 *
 * Barcha vaqt qiymatlari UTC formatida {@link Instant} sifatida saqlanadi.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * JPA uchun kerak bo'lgan himoyalangan konstruktor.
     * Yangi ID avtomatik generatsiya qilinadi.
     */
    protected BaseEntity() {
        this.id = UUID.randomUUID();
    }

    /**
     * Berilgan ID bilan entity yaratadi.
     * Mavjud entity'ni qayta yuklash yoki test uchun ishlatiladi.
     *
     * @param id entity identifikatori
     */
    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "Entity ID null bo'lishi mumkin emas");
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Entity'lar faqat ID bo'yicha solishtiriladi.
     * Ikki entity bir xil ID ga ega bo'lsa — ular teng.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}