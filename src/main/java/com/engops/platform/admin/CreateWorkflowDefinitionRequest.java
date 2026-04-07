package com.engops.platform.admin;

/**
 * Workflow definition yaratish uchun HTTP request DTO.
 *
 * tenantId bu DTO ichida emas — endpoint query param sifatida keladi.
 *
 * @param name workflow nomi (required)
 * @param workItemType work item turi: BUG, INCIDENT, TASK (required)
 * @param description ixtiyoriy tavsif (nullable)
 */
public record CreateWorkflowDefinitionRequest(
        String name,
        String workItemType,
        String description) {}
