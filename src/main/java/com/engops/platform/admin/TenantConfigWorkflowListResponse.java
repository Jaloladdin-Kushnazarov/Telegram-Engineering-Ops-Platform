package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant workflow ta'riflari ro'yxati uchun HTTP response DTO.
 *
 * Har bir workflow definition uchun compact item qaytaradi:
 * definitionId, name, workItemType, description, active, createdAt.
 *
 * Deep nested data (statuses, transition rules) kiritilmagan —
 * bu ro'yxat darajasidagi surface, detail emas.
 *
 * @param tenantId tenant identifikatori
 * @param items workflow ta'riflari ro'yxati
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantConfigWorkflowListResponse(
        UUID tenantId,
        List<WorkflowDefinitionItem> items) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkflowDefinitionItem(
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            Instant createdAt) {}
}
