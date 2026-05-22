package com.engops.platform.admin;

import java.util.UUID;

/**
 * Phase 190 — work item owner'ini tayinlash uchun HTTP request DTO.
 *
 * <p>tenantId va ownerUserId request body'da keladi. workItemId endpoint URL
 * path variable'da keladi. Yaratuvchi (actor) {@code @CurrentActor} resolver
 * tomonidan SecurityContext'dan olinadi va body'dagi har qanday {@code actor}
 * maydoniga e'tibor berilmaydi (Phase 134 spoofing-resistant pattern).</p>
 *
 * <p>Validatsiya service qatlamida (WorkItemCommandService.assignOwner)
 * bajariladi — mavjud exception style saqlanadi (BusinessRuleException →
 * 422 GlobalExceptionHandler orqali).</p>
 *
 * @param tenantId tenant identifikatori (required)
 * @param ownerUserId yangi owner foydalanuvchi identifikatori (required)
 */
public record AssignWorkItemOwnerRequest(UUID tenantId, UUID ownerUserId) {}
