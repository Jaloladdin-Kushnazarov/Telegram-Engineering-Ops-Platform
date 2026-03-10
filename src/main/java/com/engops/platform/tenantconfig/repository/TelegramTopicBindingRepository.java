package com.engops.platform.tenantconfig.repository;

import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram topic bog'lanishi uchun repository.
 */
@Repository
public interface TelegramTopicBindingRepository extends JpaRepository<TelegramTopicBinding, UUID> {

    List<TelegramTopicBinding> findByChatBindingId(UUID chatBindingId);

    Optional<TelegramTopicBinding> findByChatBindingIdAndTopicId(UUID chatBindingId, long topicId);

    List<TelegramTopicBinding> findByChatBindingIdAndActiveTrue(UUID chatBindingId);
}
