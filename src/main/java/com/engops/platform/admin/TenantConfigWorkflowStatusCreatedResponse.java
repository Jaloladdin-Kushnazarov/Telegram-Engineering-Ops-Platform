package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow status yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId tenant identifikatori
 * @param workflowDefinitionId ota workflow definition identifikatori
 * @param statusId yaratilgan status identifikatori
 * @param name status nomi
 * @param statusOrder status tartibi
 * @param initial boshlang'ich status flag'i
 * @param terminal yakuniy status flag'i
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigWorkflowStatusCreatedResponse(
        UUID tenantId,
        UUID workflowDefinitionId,
        UUID statusId,
        String name,
        int statusOrder,
        boolean initial,
        boolean terminal,
        Instant createdAt) {}
