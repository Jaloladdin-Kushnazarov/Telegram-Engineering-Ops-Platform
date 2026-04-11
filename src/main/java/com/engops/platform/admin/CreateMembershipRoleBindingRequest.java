package com.engops.platform.admin;

import java.util.UUID;

/**
 * Membership-role binding yaratish uchun HTTP request DTO.
 *
 * tenantId va membershipId request body'da emas — endpoint URL va query param'da keladi.
 *
 * @param roleId global rol identifikatori (required)
 */
public record CreateMembershipRoleBindingRequest(UUID roleId) {}
