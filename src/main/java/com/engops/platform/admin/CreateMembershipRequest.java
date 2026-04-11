package com.engops.platform.admin;

import java.util.UUID;

/**
 * Tenantda yangi a'zolik yaratish uchun HTTP request DTO.
 *
 * tenantId bu DTO ichida emas — endpoint query param sifatida keladi.
 *
 * @param userId mavjud foydalanuvchi identifikatori (required)
 */
public record CreateMembershipRequest(UUID userId) {}
