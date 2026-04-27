package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


/**
 * Workflow definition to'liq detail uchun HTTP response DTO.
 *
 * Header field'lari + statuses[] + transitionRules[].
 *
 * @param tenantId tenant identifikatori
 * @param definitionId workflow definition identifikatori
 * @param name nom
 * @param workItemType work item turi (BUG, INCIDENT, TASK)
 * @param description ixtiyoriy tavsif
 * @param active aktiv holat
 * @param createdAt yaratilgan vaqt
 * @param statuses workflow ichidagi status node'lar
 * @param transitionRules holat o'tish qoidalari
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigWorkflowDefinitionDetailResponse(
        UUID tenantId,
        UUID definitionId,
        String name,
        String workItemType,
        String description,
        boolean active,
        Instant createdAt,
        List<StatusItem> statuses,
        List<TransitionRuleItem> transitionRules) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusItem(
            UUID statusId,
            String name,
            int statusOrder,
            boolean initial,
            boolean terminal) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitionRuleItem(
            UUID ruleId,
            UUID fromStatusId,
            String fromStatusName,
            UUID toStatusId,
            String toStatusName,
            UUID requiredPermissionId) {}
}
