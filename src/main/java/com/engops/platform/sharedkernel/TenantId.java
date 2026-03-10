package com.engops.platform.sharedkernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant (tashkilot) identifikatorini ifodalovchi value object.
 *
 * Oddiy String yoki UUID o'rniga TenantId ishlatiladi, chunki:
 * - Kompilyatsiya vaqtida xatoliklarni oldini oladi (UserId bilan aralashib ketmaydi)
 * - Kod o'qilishi osonlashadi
 * - Validatsiya bir joyda saqlanadi
 *
 * Bu immutable (o'zgarmas) ob'ekt — yaratilgandan keyin qiymati o'zgarmaydi.
 */
public record TenantId(UUID value) {

    /**
     * Yangi TenantId yaratadi va validatsiyadan o'tkazadi.
     *
     * @param value UUID qiymat, null bo'lishi mumkin emas
     * @throws IllegalArgumentException agar value null bo'lsa
     */
    public TenantId {
        Objects.requireNonNull(value, "TenantId qiymati null bo'lishi mumkin emas");
    }

    /**
     * String ko'rinishidagi UUID'dan TenantId yaratadi.
     *
     * @param value UUID string formatida (masalan: "550e8400-e29b-41d4-a716-446655440000")
     * @return yangi TenantId
     * @throws IllegalArgumentException agar format noto'g'ri bo'lsa
     */
    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    /**
     * UUID ob'ektidan TenantId yaratadi.
     *
     * @param value UUID ob'ekt
     * @return yangi TenantId
     */
    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    /**
     * Yangi tasodifiy TenantId generatsiya qiladi.
     *
     * @return yangi noyob TenantId
     */
    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}