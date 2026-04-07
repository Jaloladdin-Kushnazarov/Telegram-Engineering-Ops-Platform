package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow definition yaratish natijasi uchun HTTP response DTO.
 *
 * Yaratilgan workflow definition'ning compact ko'rinishini qaytaradi.
 *
 * @param tenantId tenant identifikatori
 * @param definitionId yaratilgan workflow definition identifikatori
 * @param name workflow nomi
 * @param workItemType work item turi
 * @param description tavsif (nullable)
 * @param active aktiv holati
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigWorkflowDefinitionCreatedResponse(
        UUID tenantId,
        UUID definitionId,
        String name,
        String workItemType,
        String description,
        boolean active,
        Instant createdAt) {}
