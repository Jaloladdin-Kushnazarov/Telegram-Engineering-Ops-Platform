package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram chat bog'lanishi uchun repository. Tenant-scoped.
 */
@Repository
public interface TelegramChatBindingRepository extends JpaRepository<TelegramChatBinding, UUID> {

    List<TelegramChatBinding> findByTenantId(UUID tenantId);

    Optional<TelegramChatBinding> findByTenantIdAndChatId(UUID tenantId, long chatId);

    List<TelegramChatBinding> findByTenantIdAndActiveTrue(UUID tenantId);
}
