package com.engops.platform.admin;

import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.RoutingRule;
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
     * Workflow definition metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * PATCH semantikasi:
     * - faqat JSON'da mavjud field'lar yangilanadi
     * - kamida bitta field berilishi kerak
     * - name berilsa, blank bo'lmasligi kerak
     * - description berilmasa o'zgarmaydi, null/blank berilsa tozalanadi
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public WorkflowDefinitionUpdatedView updateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                                    UpdateWorkflowDefinitionRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isNameProvided() && !request.isDescriptionProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isNameProvided() && (request.getName() == null || request.getName().isBlank())) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }

        WorkflowDefinition definition = commandService.updateWorkflowDefinition(
                tenantId, definitionId,
                request.getName(), request.isNameProvided(),
                request.getDescription(), request.isDescriptionProvided());

        return new WorkflowDefinitionUpdatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    /**
     * Workflow definition'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public WorkflowDefinitionUpdatedView activateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }

        WorkflowDefinition definition = commandService.activateWorkflowDefinition(tenantId, definitionId);
        return toUpdatedView(definition);
    }

    /**
     * Workflow definition'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan workflow definition view
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public WorkflowDefinitionUpdatedView deactivateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }

        WorkflowDefinition definition = commandService.deactivateWorkflowDefinition(tenantId, definitionId);
        return toUpdatedView(definition);
    }

    private WorkflowDefinitionUpdatedView toUpdatedView(WorkflowDefinition definition) {
        return new WorkflowDefinitionUpdatedView(
                definition.getTenantId(),
                definition.getId(),
                definition.getName(),
                definition.getWorkItemType(),
                definition.getDescription(),
                definition.isActive(),
                definition.getCreatedAt());
    }

    // ========== RoutingRule operations ==========

    /**
     * Yangi routing rule yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan routing rule view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoutingRuleCreatedView createRoutingRule(UUID tenantId,
                                                     CreateRoutingRuleRequest request) {
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

        RoutingRule rule = commandService.createRoutingRule(
                tenantId, request.name(), request.workItemType(),
                request.priority(), request.targetTopicBindingId(),
                request.conditionExpression());

        return new RoutingRuleCreatedView(
                rule.getTenantId(),
                rule.getId(),
                rule.getName(),
                rule.getWorkItemType(),
                rule.getPriority(),
                rule.getTargetTopicBindingId(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    /**
     * Mavjud routing rule metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * PATCH semantikasi:
     * - faqat JSON'da mavjud field'lar yangilanadi
     * - kamida bitta field berilishi kerak
     * - name berilsa, blank bo'lmasligi kerak
     * - targetTopicBindingId berilmasa o'zgarmaydi, explicit null berilsa tozalanadi
     * - conditionExpression berilmasa o'zgarmaydi, null/blank berilsa tozalanadi
     * - priority berilmasa o'zgarmaydi
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public RoutingRuleUpdatedView updateRoutingRule(UUID tenantId, UUID ruleId,
                                                      UpdateRoutingRuleRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isNameProvided() && !request.isPriorityProvided()
                && !request.isTargetTopicBindingIdProvided() && !request.isConditionExpressionProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isNameProvided() && (request.getName() == null || request.getName().isBlank())) {
            throw new IllegalArgumentException("name null yoki bo'sh bo'lishi mumkin emas");
        }
        if (request.isPriorityProvided() && request.getPriority() == null) {
            throw new IllegalArgumentException("priority null bo'lishi mumkin emas");
        }

        RoutingRule rule = commandService.updateRoutingRule(
                tenantId, ruleId,
                request.getName(), request.isNameProvided(),
                request.getPriority(), request.isPriorityProvided(),
                request.getTargetTopicBindingId(), request.isTargetTopicBindingIdProvided(),
                request.getConditionExpression(), request.isConditionExpressionProvided());

        return new RoutingRuleUpdatedView(
                rule.getTenantId(),
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                rule.getTargetTopicBindingId(),
                rule.getConditionExpression(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    /**
     * Facade natija modeli — yangilangan routing rule.
     */
    public record RoutingRuleUpdatedView(
            UUID tenantId,
            UUID ruleId,
            String name,
            int priority,
            UUID targetTopicBindingId,
            String conditionExpression,
            boolean active,
            java.time.Instant createdAt) {}

    /**
     * Facade natija modeli — yaratilgan routing rule.
     */
    public record RoutingRuleCreatedView(
            UUID tenantId,
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            UUID targetTopicBindingId,
            boolean active,
            java.time.Instant createdAt) {}

    /**
     * Facade natija modeli — yangilangan workflow definition.
     */
    public record WorkflowDefinitionUpdatedView(
            UUID tenantId,
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            java.time.Instant createdAt) {}

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
