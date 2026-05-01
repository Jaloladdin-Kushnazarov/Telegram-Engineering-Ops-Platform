package com.engops.platform.workflow;

import java.util.UUID;

/**
 * Workflow transition uchun HTTP request DTO.
 *
 * Field'lar mavjud {@code WorkflowTransitionService.transition(tenantId, workItemId,
 * targetStatusCode, actorUserId, actionSource, reason)} signature'iga to'g'ridan-to'g'ri
 * map qilinadi. Controller'da biznes validatsiya qo'shilmaydi — state machine
 * qoidalari (SAME_STATUS, INVALID_TRANSITION, terminal/reopen) servisda saqlanadi.
 *
 * actorUserId — bu Phase 122'da audit/attribution input, ishonchli authentication EMAS.
 * Ishonchli auth keyingi alohida phase'da ishlab chiqiladi.
 *
 * @param tenantId tenant identifikatori (required)
 * @param targetStatusCode maqsad status kodi (required, masalan "PROCESSING")
 * @param actorUserId amal bajaruvchi foydalanuvchi (audit/attribution uchun)
 * @param actionSource amal manbai: MANUAL, TELEGRAM, SYSTEM va h.k. (required)
 * @param reason o'tish sababi (nullable, audit'ga yoziladi)
 */
public record WorkflowTransitionRequest(
        UUID tenantId,
        String targetStatusCode,
        UUID actorUserId,
        String actionSource,
        String reason) {}
