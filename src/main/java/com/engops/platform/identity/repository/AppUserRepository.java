package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Foydalanuvchi (AppUser) uchun repository.
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByTelegramUserId(Long telegramUserId);

    boolean existsByTelegramUserId(Long telegramUserId);
}
