package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.AppUserRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 215 — Platform-level role binding uchun repository.
 *
 * <p>{@link AppUserRoleBinding} entity'larni tenant'siz boshqaradi.
 * {@code PLATFORM_OWNER} kabi global rollar shu repository orqali
 * biriktiriladi.</p>
 *
 * <p>Phase 215 PURELY ADDITIVE: hozir hech qaysi service bu repository'ni
 * inject qilmaydi. Phase 216 authorization rewrite paytida ishlatiladi.</p>
 */
@Repository
public interface AppUserRoleBindingRepository extends JpaRepository<AppUserRoleBinding, UUID> {

    /**
     * Foydalanuvchining barcha platform-level rol biriktirishlarini qaytaradi.
     * Phase 216 authorizeGlobal yo'lida ishlatiladi.
     */
    List<AppUserRoleBinding> findByUserId(UUID userId);

    /**
     * Foydalanuvchi'da ko'rsatilgan rol biriktirilganmi tekshiradi.
     * UNIQUE(user_id, role_id) constraint'ini bilvosita ishlatadi.
     */
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    /**
     * Aniq binding'ni qaytaradi (mavjud bo'lsa). Idempotent seed yo'lida
     * ishlatish uchun qulay.
     */
    Optional<AppUserRoleBinding> findByUserIdAndRoleId(UUID userId, UUID roleId);
}
