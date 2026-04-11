package com.engops.platform.admin;

import com.engops.platform.identity.IdentityCommandService;
import com.engops.platform.identity.model.Membership;
import com.engops.platform.tenantconfig.TenantConfigCommandService;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
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
    private static final Set<String> ALLOWED_BINDING_TYPES = Set.of("MAIN_GROUP", "NOTIFICATION_GROUP");

    private final TenantConfigCommandService commandService;
    private final IdentityCommandService identityCommandService;

    public TenantConfigWriteFacade(TenantConfigCommandService commandService,
                                    IdentityCommandService identityCommandService) {
        this.commandService = commandService;
        this.identityCommandService = identityCommandService;
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

    /**
     * Workflow definition'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @throws IllegalArgumentException tenantId yoki definitionId null bo'lsa
     */
    public void deleteWorkflowDefinition(UUID tenantId, UUID definitionId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (definitionId == null) {
            throw new IllegalArgumentException("definitionId null bo'lishi mumkin emas");
        }

        commandService.deleteWorkflowDefinition(tenantId, definitionId);
    }

    // ========== TelegramChatBinding operations ==========

    /**
     * Yangi chat binding yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan chat binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public ChatBindingCreatedView createChatBinding(UUID tenantId,
                                                      CreateChatBindingRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.chatId() == null) {
            throw new IllegalArgumentException("chatId null bo'lishi mumkin emas");
        }
        if (request.bindingType() == null || request.bindingType().isBlank()) {
            throw new IllegalArgumentException("bindingType null yoki bo'sh bo'lishi mumkin emas");
        }
        if (!ALLOWED_BINDING_TYPES.contains(request.bindingType())) {
            throw new IllegalArgumentException(
                    "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: " + request.bindingType());
        }

        TelegramChatBinding binding = commandService.createChatBinding(
                tenantId, request.chatId(), request.chatTitle(),
                ChatBindingType.valueOf(request.bindingType()));

        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Chat binding metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public ChatBindingCreatedView updateChatBinding(UUID tenantId, UUID chatBindingId,
                                                      UpdateChatBindingRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isChatTitleProvided() && !request.isBindingTypeProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }
        if (request.isBindingTypeProvided()) {
            if (request.getBindingType() == null || request.getBindingType().isBlank()) {
                throw new IllegalArgumentException("bindingType null yoki bo'sh bo'lishi mumkin emas");
            }
            if (!ALLOWED_BINDING_TYPES.contains(request.getBindingType())) {
                throw new IllegalArgumentException(
                        "bindingType faqat MAIN_GROUP, NOTIFICATION_GROUP bo'lishi mumkin: "
                                + request.getBindingType());
            }
        }

        ChatBindingType bindingType = request.isBindingTypeProvided()
                ? ChatBindingType.valueOf(request.getBindingType()) : null;

        TelegramChatBinding binding = commandService.updateChatBinding(
                tenantId, chatBindingId,
                request.getChatTitle(), request.isChatTitleProvided(),
                bindingType, request.isBindingTypeProvided());

        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Chat binding'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public ChatBindingCreatedView activateChatBinding(UUID tenantId, UUID chatBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }

        TelegramChatBinding binding = commandService.activateChatBinding(tenantId, chatBindingId);
        return toChatBindingView(binding);
    }

    /**
     * Chat binding'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @return yangilangan chat binding view
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public ChatBindingCreatedView deactivateChatBinding(UUID tenantId, UUID chatBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }

        TelegramChatBinding binding = commandService.deactivateChatBinding(tenantId, chatBindingId);
        return toChatBindingView(binding);
    }

    /**
     * Chat binding'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @throws IllegalArgumentException tenantId yoki chatBindingId null bo'lsa
     */
    public void deleteChatBinding(UUID tenantId, UUID chatBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (chatBindingId == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }

        commandService.deleteChatBinding(tenantId, chatBindingId);
    }

    private ChatBindingCreatedView toChatBindingView(TelegramChatBinding binding) {
        return new ChatBindingCreatedView(
                binding.getTenantId(),
                binding.getId(),
                binding.getChatId(),
                binding.getChatTitle(),
                binding.getBindingType().name(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — yaratilgan chat binding.
     */
    public record ChatBindingCreatedView(
            UUID tenantId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            java.time.Instant createdAt) {}

    // ========== TelegramTopicBinding operations ==========

    /**
     * Yangi topic binding yaratish uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param request yaratish so'rovi
     * @return yaratilgan topic binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public TopicBindingView createTopicBinding(UUID tenantId,
                                                 CreateTopicBindingRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (request.chatBindingId() == null) {
            throw new IllegalArgumentException("chatBindingId null bo'lishi mumkin emas");
        }
        if (request.topicId() == null) {
            throw new IllegalArgumentException("topicId null bo'lishi mumkin emas");
        }
        if (request.purpose() == null || request.purpose().isBlank()) {
            throw new IllegalArgumentException("purpose null yoki bo'sh bo'lishi mumkin emas");
        }

        TelegramTopicBinding binding = commandService.createTopicBinding(
                tenantId, request.chatBindingId(), request.topicId(),
                request.topicName(), request.purpose());

        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding metadata'sini PATCH yangilash uchun request boundary validatsiyasi va delegation.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @param request yangilash so'rovi
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException request boundary buzilsa
     */
    public TopicBindingView updateTopicBinding(UUID tenantId, UUID topicBindingId,
                                                 UpdateTopicBindingRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request null bo'lishi mumkin emas");
        }
        if (!request.isTopicNameProvided()) {
            throw new IllegalArgumentException("Kamida bitta yangilanuvchi field berilishi kerak");
        }

        TelegramTopicBinding binding = commandService.updateTopicBinding(
                tenantId, topicBindingId,
                request.getTopicName(), request.isTopicNameProvided());

        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public TopicBindingView activateTopicBinding(UUID tenantId, UUID topicBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }

        TelegramTopicBinding binding = commandService.activateTopicBinding(tenantId, topicBindingId);
        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @return yangilangan topic binding view
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public TopicBindingView deactivateTopicBinding(UUID tenantId, UUID topicBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }

        TelegramTopicBinding binding = commandService.deactivateTopicBinding(tenantId, topicBindingId);
        return toTopicBindingView(tenantId, binding);
    }

    /**
     * Topic binding'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param topicBindingId topic binding identifikatori
     * @throws IllegalArgumentException tenantId yoki topicBindingId null bo'lsa
     */
    public void deleteTopicBinding(UUID tenantId, UUID topicBindingId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (topicBindingId == null) {
            throw new IllegalArgumentException("topicBindingId null bo'lishi mumkin emas");
        }

        commandService.deleteTopicBinding(tenantId, topicBindingId);
    }

    private TopicBindingView toTopicBindingView(UUID tenantId, TelegramTopicBinding binding) {
        UUID chatBindingId = binding.getChatBinding() != null ? binding.getChatBinding().getId() : null;
        return new TopicBindingView(
                tenantId,
                binding.getId(),
                chatBindingId,
                binding.getTopicId(),
                binding.getTopicName(),
                binding.getPurpose(),
                binding.isActive(),
                binding.getCreatedAt());
    }

    /**
     * Facade natija modeli — topic binding write natijasi.
     */
    public record TopicBindingView(
            UUID tenantId,
            UUID topicBindingId,
            UUID chatBindingId,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            java.time.Instant createdAt) {}

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
     * Routing rule'ni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public RoutingRuleUpdatedView activateRoutingRule(UUID tenantId, UUID ruleId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }

        RoutingRule rule = commandService.activateRoutingRule(tenantId, ruleId);
        return toRoutingRuleUpdatedView(rule);
    }

    /**
     * Routing rule'ni noaktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan routing rule view
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public RoutingRuleUpdatedView deactivateRoutingRule(UUID tenantId, UUID ruleId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }

        RoutingRule rule = commandService.deactivateRoutingRule(tenantId, ruleId);
        return toRoutingRuleUpdatedView(rule);
    }

    /**
     * Routing rule'ni o'chiradi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @throws IllegalArgumentException tenantId yoki ruleId null bo'lsa
     */
    public void deleteRoutingRule(UUID tenantId, UUID ruleId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId null bo'lishi mumkin emas");
        }

        commandService.deleteRoutingRule(tenantId, ruleId);
    }

    private RoutingRuleUpdatedView toRoutingRuleUpdatedView(RoutingRule rule) {
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

    // ========== Membership status lifecycle ==========

    /**
     * A'zolikni aktiv holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan membership view
     * @throws IllegalArgumentException tenantId yoki membershipId null bo'lsa
     */
    public MembershipStatusView activateMembership(UUID tenantId, UUID membershipId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }

        Membership membership = identityCommandService.activateMembership(tenantId, membershipId);
        return toMembershipStatusView(membership);
    }

    /**
     * A'zolikni SUSPENDED holatga o'tkazadi.
     *
     * @param tenantId tenant identifikatori
     * @param membershipId a'zolik identifikatori
     * @return yangilangan membership view
     * @throws IllegalArgumentException tenantId yoki membershipId null bo'lsa
     */
    public MembershipStatusView suspendMembership(UUID tenantId, UUID membershipId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId null bo'lishi mumkin emas");
        }

        Membership membership = identityCommandService.suspendMembership(tenantId, membershipId);
        return toMembershipStatusView(membership);
    }

    private MembershipStatusView toMembershipStatusView(Membership membership) {
        return new MembershipStatusView(
                membership.getTenantId(),
                membership.getId(),
                membership.getUserId(),
                membership.getStatus().name(),
                membership.getCreatedAt());
    }

    /**
     * Facade natija modeli — membership status o'zgarishi.
     */
    public record MembershipStatusView(
            UUID tenantId,
            UUID membershipId,
            UUID userId,
            String status,
            java.time.Instant createdAt) {}
}
