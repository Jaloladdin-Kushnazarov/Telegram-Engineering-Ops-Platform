package com.engops.platform.admin;

import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun write orchestration facade.
 *
 * Admin controller'dan write request'larni qabul qilib,
 * request boundary validatsiyasini bajarib, TenantConfigCommandService'ga uzatadi.
 *
 * Muhim:
 * - Tranzaksiya bu facade'da emas — TenantConfigCommandService ichida
 * - Faqat request boundary validation bu yerda
 * - Business validation TenantConfigCommandService ichida
 * - Read operatsiyalar uchun TenantConfigDetailsFacade ishlatiladi
 */
@Service
public class TenantConfigWriteFacade {

    private static final Set<String> ALLOWED_WORK_ITEM_TYPES = Set.of("BUG", "INCIDENT", "TASK");

    private final TenantConfigCommandService commandService;

    public TenantConfigWriteFacade(TenantConfigCommandService commandService) {
        this.commandService = commandService;
    }

    /**
     * Yangi workflow definition yaratish uchun request boundary validatsiyasi va delegation.
     *
     * Request boundary validatsiya:
     * - tenantId null bo'lmasligi kerak
     * - request null bo'lmasligi kerak
     * - name null/blank bo'lmasligi kerak
     * - workItemType null/blank bo'lmasligi kerak
     * - workItemType faqat: BUG, INCIDENT, TASK
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan workflow definition view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowDefinitionCreatedView createWorkflowDefinition(UUID tenantId,
                                                                    CreateWorkflowDefinitionRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.workItemType() == null || request.workItemType().isBlank()) {
            throw new IllegalArgumentException("workItemType null yoki bo'sh bo'lishi mumkin emas");
        }
        if (!ALLOWED_WORK_ITEM_TYPES.contains(request.workItemType())) {
            throw new IllegalArgumentException(
                    "workItemType faqat BUG, INCIDENT, TASK bo'lishi mumkin: " + request.workItemType());
        }

        WorkflowDefinition definition = commandService.createWorkflowDefinition(
                tenantId, request.name(), request.workItemType(), request.description());

        return new WorkflowDefinitionCreatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan workflow definition.
     */
    public record WorkflowDefinitionCreatedView(
            UUID tenantId,
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            java.time.Instant createdAt) {}
}
