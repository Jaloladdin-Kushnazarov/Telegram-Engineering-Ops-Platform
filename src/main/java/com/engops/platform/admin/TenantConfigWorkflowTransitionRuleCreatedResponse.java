package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow transition rule yaratish natijasi uchun HTTP response DTO.
 *
 * @param tenantId tenant identifikatori
 * @param workflowDefinitionId ota workflow definition identifikatori
 * @param transitionRuleId yaratilgan transition rule identifikatori
 * @param fromStatusId boshlang'ich status identifikatori
 * @param toStatusId maqsad status identifikatori
 * @param createdAt yaratilgan vaqt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigWorkflowTransitionRuleCreatedResponse(
        UUID tenantId,
        UUID workflowDefinitionId,
        UUID transitionRuleId,
        UUID fromStatusId,
        UUID toStatusId,
        Instant createdAt) {}
