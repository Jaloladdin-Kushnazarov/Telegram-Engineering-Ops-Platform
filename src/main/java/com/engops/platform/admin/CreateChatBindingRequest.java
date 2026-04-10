package com.engops.platform.admin;

/**
 * Chat binding yaratish uchun HTTP request DTO.
 *
 * @param chatId Telegram chat identifikatori
 * @param chatTitle chat sarlavhasi (nullable)
 * @param bindingType binding turi (MAIN_GROUP, NOTIFICATION_GROUP)
 */
public record CreateChatBindingRequest(
        Long chatId,
        String chatTitle,
        String bindingType) {}
