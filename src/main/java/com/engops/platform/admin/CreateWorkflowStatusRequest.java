package com.engops.platform.admin;

/**
 * Workflow status yaratish uchun HTTP request DTO.
 *
 * tenantId va workflowDefinitionId bu DTO ichida emas — endpoint query
 * param/path orqali keladi.
 *
 * @param name status nomi (required, non-blank, max 100)
 * @param statusOrder status tartibi (>= 0; default 0)
 * @param initial boshlang'ich status flag'i
 * @param terminal yakuniy status flag'i
 */
public record CreateWorkflowStatusRequest(
        String name,
        int statusOrder,
        boolean initial,
        boolean terminal) {}
