package com.engops.platform.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing (avtomatik vaqt belgilash) konfiguratsiyasi.
 *
 * Bu konfiguratsiya {@code @CreatedDate} va {@code @LastModifiedDate}
 * annotatsiyalarini ishga tushiradi.
 *
 * Natijada {@link com.engops.platform.sharedkernel.BaseEntity}'dagi
 * {@code createdAt} va {@code updatedAt} maydonlari avtomatik to'ldiriladi:
 * - Entity birinchi marta saqlanganda {@code createdAt} belgilanadi
 * - Har safar yangilanganda {@code updatedAt} yangilanadi
 *
 * Bu qo'lda vaqt o'rnatish zaruratini yo'q qiladi va
 * barcha entity'larda izchil auditing ta'minlaydi.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Hozircha qo'shimcha konfiguratsiya kerak emas.
    // AuditorAware (kim o'zgartirganini aniqlash) keyingi fazada
    // identity moduli tayyor bo'lganda qo'shiladi.
}