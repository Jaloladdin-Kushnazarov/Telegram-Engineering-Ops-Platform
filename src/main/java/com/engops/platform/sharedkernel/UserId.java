package com.engops.platform.sharedkernel;

import java.util.Objects;
import java.util.UUID;

/**
 * Foydalanuvchi (user) identifikatorini ifodalovchi value object.
 *
 * TenantId kabi, UserId ham type-safe identifikator vazifasini bajaradi.
 * Bu orqali UserId va TenantId aralashib ketishi oldini olinadi.
 *
 * Bu immutable (o'zgarmas) ob'ekt.
 */
public record UserId(UUID value) {

    /**
     * Yangi UserId yaratadi va validatsiyadan o'tkazadi.
     *
     * @param value UUID qiymat, null bo'lishi mumkin emas
     * @throws IllegalArgumentException agar value null bo'lsa
     */
    public UserId {
        Objects.requireNonNull(value, "UserId qiymati null bo'lishi mumkin emas");
    }

    /**
     * String ko'rinishidagi UUID'dan UserId yaratadi.
     *
     * @param value UUID string formatida
     * @return yangi UserId
     * @throws IllegalArgumentException agar format noto'g'ri bo'lsa
     */
    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    /**
     * UUID ob'ektidan UserId yaratadi.
     *
     * @param value UUID ob'ekt
     * @return yangi UserId
     */
    public static UserId of(UUID value) {
        return new UserId(value);
    }

    /**
     * Yangi tasodifiy UserId generatsiya qiladi.
     *
     * @return yangi noyob UserId
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}