package com.engops.platform.admin;

import java.util.UUID;

/**
 * Workflow transition rule yaratish uchun HTTP request DTO.
 *
 * tenantId va workflowDefinitionId bu DTO ichida emas — endpoint query
 * param/path orqali keladi.
 *
 * Phase 116 surface'i uchun requiredPermissionId qo'llab-quvvatlanmaydi —
 * runtime hozircha shu fieldni e'tiborga olmaydi (kelajak phase'ida
 * runtime gate qo'shilganda surface ham yangilanadi).
 *
 * @param fromStatusId boshlang'ich status identifikatori (required)
 * @param toStatusId maqsad status identifikatori (required, fromStatusId'dan farq qilishi kerak)
 */
public record CreateWorkflowTransitionRuleRequest(
        UUID fromStatusId,
        UUID toStatusId) {}
