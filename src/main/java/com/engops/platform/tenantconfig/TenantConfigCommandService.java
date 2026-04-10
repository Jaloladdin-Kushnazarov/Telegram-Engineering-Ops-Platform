package com.engops.platform.tenantconfig;

import com.engops.platform.audit.AuditService;
import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.model.ChatBindingType;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.RoutingRuleRepository;
import com.engops.platform.tenantconfig.repository.TelegramChatBindingRepository;
import com.engops.platform.tenantconfig.repository.TelegramTopicBindingRepository;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun buyruq (command) servisi — yaratish, o'zgartirish operatsiyalari.
 *
 * Cross-module bog'lanishlar:
 * - AuditService — audit yozish uchun (public API)
 *
 * Muhim:
 * - Faqat tenant-config module'ning o'z repository'laridan foydalanadi
 * - Read-only operatsiyalar TenantConfigQueryService orqali
 * - Bu servis write tranzaksiyasini own qiladi
 */
@Service
@Transactional
public class TenantConfigCommandService {

    private final TenantRepository tenantRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final TelegramChatBindingRepository telegramChatBindingRepository;
    private final TelegramTopicBindingRepository telegramTopicBindingRepository;
    private final AuditService auditService;

    public TenantConfigCommandService(TenantRepository tenantRepository,
                                       WorkflowDefinitionRepository workflowDefinitionRepository,
                                       RoutingRuleRepository routingRuleRepository,
                                       TelegramChatBindingRepository telegramChatBindingRepository,
                                       TelegramTopicBindingRepository telegramTopicBindingRepository,
                                       AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.telegramChatBindingRepository = telegramChatBindingRepository;
        this.telegramTopicBindingRepository = telegramTopicBindingRepository;
        this.auditService = auditService;
    }

    /**
     * Yangi workflow definition yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Tenant ichida workflow nomi unikal bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param name workflow nomi
     * @param workItemType work item turi (BUG, INCIDENT, TASK)
     * @param description ixtiyoriy tavsif (nullable)
     * @return yaratilgan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar shu nomli workflow allaqachon mavjud bo'lsa
     */
    public WorkflowDefinition createWorkflowDefinition(UUID tenantId, String name,
                                                         String workItemType, String description) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        workflowDefinitionRepository.findByTenantIdAndName(tenantId, name)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                            "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
                });

        WorkflowDefinition definition = new WorkflowDefinition(tenantId, name, workItemType);
        if (description != null && !description.isBlank()) {
            definition.setDescription(description);
        }

        try {
            definition = workflowDefinitionRepository.save(definition);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
            }
            throw ex;
        }

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return definition;
    }

    /**
     * Workflow definition metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - nameProvided=false → nom o'zgarmaydi
     * - nameProvided=true → yangi nom o'rnatiladi, duplicate check bajariladi
     * - descriptionProvided=false → tavsif o'zgarmaydi
     * - descriptionProvided=true, description null/blank → tavsif tozalanadi
     * - descriptionProvided=true, description non-blank → tavsif yangilanadi
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @param name yangi nom (faqat nameProvided=true bo'lganda ishlatiladi)
     * @param nameProvided name field JSON'da berilganmi
     * @param description yangi tavsif (faqat descriptionProvided=true bo'lganda ishlatiladi)
     * @param descriptionProvided description field JSON'da berilganmi
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     * @throws BusinessRuleException agar shu nomli workflow allaqachon mavjud bo'lsa
     */
    public WorkflowDefinition updateWorkflowDefinition(UUID tenantId, UUID definitionId,
                                                         String name, boolean nameProvided,
                                                         String description, boolean descriptionProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        String oldName = definition.getName();
        String oldDescription = definition.getDescription();

        if (nameProvided) {
            if (!name.equals(definition.getName())) {
                workflowDefinitionRepository.findByTenantIdAndName(tenantId, name)
                        .ifPresent(existing -> {
                            throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                                    "Tenant ichida '" + name + "' nomli workflow allaqachon mavjud");
                        });
            }
            definition.setName(name);
        }

        if (descriptionProvided) {
            definition.setDescription(description != null && !description.isBlank() ? description : null);
        }

        try {
            definition = workflowDefinitionRepository.save(definition);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateNameConstraint(ex)) {
                String failedName = nameProvided ? name : definition.getName();
                throw new BusinessRuleException("DUPLICATE_WORKFLOW_NAME",
                        "Tenant ichida '" + failedName + "' nomli workflow allaqachon mavjud");
            }
            throw ex;
        }

        String newName = definition.getName();
        String newDescription = definition.getDescription();
        String oldValue = oldName + (oldDescription != null ? " | " + oldDescription : "");
        String newValue = newName + (newDescription != null ? " | " + newDescription : "");

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return definition;
    }

    /**
     * Workflow definition'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public WorkflowDefinition activateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        if (definition.isActive()) {
            return definition;
        }

        definition.setActive(true);
        definition = workflowDefinitionRepository.save(definition);

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return definition;
    }

    /**
     * Workflow definition'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @return yangilangan WorkflowDefinition
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public WorkflowDefinition deactivateWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        if (!definition.isActive()) {
            return definition;
        }

        definition.setActive(false);
        definition = workflowDefinitionRepository.save(definition);

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definition.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return definition;
    }

    /**
     * Workflow definition'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param definitionId workflow definition identifikatori
     * @throws ResourceNotFoundException agar tenant yoki workflow definition topilmasa
     */
    public void deleteWorkflowDefinition(UUID tenantId, UUID definitionId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        WorkflowDefinition definition = workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", definitionId));

        String oldValue = definition.getName() + " | type=" + definition.getWorkItemType();

        workflowDefinitionRepository.delete(definition);

        auditService.recordEvent(tenantId, "WORKFLOW_DEFINITION", definitionId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    // ========== TelegramChatBinding operations ==========

    /**
     * Yangi Telegram chat binding yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. Shu tenant ichida shu chatId uchun binding allaqachon mavjud bo'lmasligi kerak
     *
     * @param tenantId tenant identifikatori
     * @param chatId Telegram chat identifikatori
     * @param chatTitle chat sarlavhasi (nullable)
     * @param bindingType binding turi (MAIN_GROUP, NOTIFICATION_GROUP)
     * @return yaratilgan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant topilmasa
     * @throws BusinessRuleException agar duplicate chat binding mavjud bo'lsa
     */
    public TelegramChatBinding createChatBinding(UUID tenantId, long chatId,
                                                   String chatTitle, ChatBindingType bindingType) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        telegramChatBindingRepository.findByTenantIdAndChatId(tenantId, chatId)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                            "Tenant ichida chatId=" + chatId + " uchun binding allaqachon mavjud");
                });

        TelegramChatBinding binding = new TelegramChatBinding(tenantId, chatId,
                chatTitle != null && !chatTitle.isBlank() ? chatTitle : null);
        binding.setBindingType(bindingType);

        try {
            binding = telegramChatBindingRepository.save(binding);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateChatBindingConstraint(ex)) {
                throw new BusinessRuleException("DUPLICATE_CHAT_BINDING",
                        "Tenant ichida chatId=" + chatId + " uchun binding allaqachon mavjud");
            }
            throw ex;
        }

        String newValue = chatId + " | " + bindingType.name()
                + (binding.getChatTitle() != null ? " | " + binding.getChatTitle() : "");

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "CREATED", null, "ADMIN_API", null, newValue);

        return binding;
    }

    /**
     * Chat binding metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - chatTitleProvided=false → sarlavha o'zgarmaydi
     * - chatTitleProvided=true, null/blank → sarlavha tozalanadi
     * - chatTitleProvided=true, non-blank → sarlavha yangilanadi
     * - bindingTypeProvided=false → tur o'zgarmaydi
     * - bindingTypeProvided=true → yangi tur o'rnatiladi
     *
     * @param tenantId tenant identifikatori
     * @param chatBindingId chat binding identifikatori
     * @param chatTitle yangi sarlavha (faqat chatTitleProvided=true bo'lganda)
     * @param chatTitleProvided chatTitle field JSON'da berilganmi
     * @param bindingType yangi binding turi (faqat bindingTypeProvided=true bo'lganda)
     * @param bindingTypeProvided bindingType field JSON'da berilganmi
     * @return yangilangan TelegramChatBinding
     * @throws ResourceNotFoundException agar tenant yoki chat binding topilmasa
     */
    public TelegramChatBinding updateChatBinding(UUID tenantId, UUID chatBindingId,
                                                   String chatTitle, boolean chatTitleProvided,
                                                   ChatBindingType bindingType, boolean bindingTypeProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        TelegramChatBinding binding = telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatBinding", chatBindingId));

        String oldChatTitle = binding.getChatTitle();
        String oldBindingType = binding.getBindingType().name();

        if (chatTitleProvided) {
            binding.setChatTitle(chatTitle != null && !chatTitle.isBlank() ? chatTitle : null);
        }

        if (bindingTypeProvided) {
            binding.setBindingType(bindingType);
        }

        binding = telegramChatBindingRepository.save(binding);

        String oldValue = oldBindingType + (oldChatTitle != null ? " | " + oldChatTitle : "");
        String newValue = binding.getBindingType().name()
                + (binding.getChatTitle() != null ? " | " + binding.getChatTitle() : "");

        auditService.recordEvent(tenantId, "CHAT_BINDING", binding.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return binding;
    }

    // ========== RoutingRule operations ==========

    /**
     * Yangi routing rule yaratadi.
     *
     * Validatsiyalar:
     * 1. Tenant mavjud bo'lishi kerak
     * 2. targetTopicBindingId berilsa, topic binding mavjud va shu tenantga tegishli bo'lishi kerak
     *
     * @param tenantId tenant identifikatori
     * @param name rule nomi
     * @param workItemType work item turi (BUG, INCIDENT, TASK)
     * @param priority rule prioriteti
     * @param targetTopicBindingId target topic binding (nullable)
     * @param conditionExpression shart ifodasi (nullable)
     * @return yaratilgan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki topic binding topilmasa
     */
    public RoutingRule createRoutingRule(UUID tenantId, String name, String workItemType,
                                         int priority, UUID targetTopicBindingId,
                                         String conditionExpression) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        if (targetTopicBindingId != null) {
            telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(targetTopicBindingId, tenantId)
                    .orElseThrow(() -> new BusinessRuleException("INVALID_TOPIC_BINDING",
                            "Topic binding (id=" + targetTopicBindingId
                                    + ") topilmadi yoki shu tenantga tegishli emas"));
        }

        RoutingRule rule = new RoutingRule(tenantId, name, workItemType);
        rule.setPriority(priority);
        rule.setTargetTopicBindingId(targetTopicBindingId);
        if (conditionExpression != null && !conditionExpression.isBlank()) {
            rule.setConditionExpression(conditionExpression);
        }

        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "CREATED", null, "ADMIN_API", null, name);

        return rule;
    }

    /**
     * Mavjud routing rule metadata'sini partial yangilaydi (PATCH semantikasi).
     *
     * Faqat provided=true field'lar yangilanadi:
     * - nameProvided=true → yangi nom o'rnatiladi
     * - priorityProvided=true → yangi prioritet o'rnatiladi
     * - targetTopicBindingIdProvided=true, null → tozalanadi
     * - targetTopicBindingIdProvided=true, non-null → tenant-safe validate va set
     * - conditionExpressionProvided=true, null/blank → tozalanadi
     * - conditionExpressionProvided=true, non-blank → yangilanadi
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @param name yangi nom (faqat nameProvided=true bo'lganda)
     * @param nameProvided name field JSON'da berilganmi
     * @param priority yangi prioritet (faqat priorityProvided=true bo'lganda)
     * @param priorityProvided priority field JSON'da berilganmi
     * @param targetTopicBindingId yangi topic binding (faqat provided=true bo'lganda)
     * @param targetTopicBindingIdProvided field JSON'da berilganmi
     * @param conditionExpression yangi shart ifodasi (faqat provided=true bo'lganda)
     * @param conditionExpressionProvided field JSON'da berilganmi
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     * @throws BusinessRuleException agar topic binding yaroqsiz bo'lsa
     */
    public RoutingRule updateRoutingRule(UUID tenantId, UUID ruleId,
                                          String name, boolean nameProvided,
                                          Integer priority, boolean priorityProvided,
                                          UUID targetTopicBindingId, boolean targetTopicBindingIdProvided,
                                          String conditionExpression, boolean conditionExpressionProvided) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        String oldName = rule.getName();
        int oldPriority = rule.getPriority();
        UUID oldTopicBindingId = rule.getTargetTopicBindingId();
        String oldConditionExpression = rule.getConditionExpression();

        if (nameProvided) {
            rule.setName(name);
        }

        if (priorityProvided) {
            rule.setPriority(priority);
        }

        if (targetTopicBindingIdProvided) {
            if (targetTopicBindingId == null) {
                rule.setTargetTopicBindingId(null);
            } else {
                telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(targetTopicBindingId, tenantId)
                        .orElseThrow(() -> new BusinessRuleException("INVALID_TOPIC_BINDING",
                                "Topic binding (id=" + targetTopicBindingId
                                        + ") topilmadi yoki shu tenantga tegishli emas"));
                rule.setTargetTopicBindingId(targetTopicBindingId);
            }
        }

        if (conditionExpressionProvided) {
            rule.setConditionExpression(
                    conditionExpression != null && !conditionExpression.isBlank()
                            ? conditionExpression : null);
        }

        rule = routingRuleRepository.save(rule);

        String oldValue = oldName + " | p=" + oldPriority
                + (oldTopicBindingId != null ? " | tb=" + oldTopicBindingId : "")
                + (oldConditionExpression != null ? " | ce=" + oldConditionExpression : "");
        String newValue = rule.getName() + " | p=" + rule.getPriority()
                + (rule.getTargetTopicBindingId() != null ? " | tb=" + rule.getTargetTopicBindingId() : "")
                + (rule.getConditionExpression() != null ? " | ce=" + rule.getConditionExpression() : "");

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "UPDATED", null, "ADMIN_API", oldValue, newValue);

        return rule;
    }

    /**
     * Routing rule'ni aktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon aktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public RoutingRule activateRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        if (rule.isActive()) {
            return rule;
        }

        rule.setActive(true);
        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "ACTIVATED", null, "ADMIN_API", "false", "true");

        return rule;
    }

    /**
     * Routing rule'ni noaktiv holatga o'tkazadi.
     *
     * Idempotent: allaqachon noaktiv bo'lsa, hech narsa o'zgarmaydi.
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @return yangilangan RoutingRule
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public RoutingRule deactivateRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        if (!rule.isActive()) {
            return rule;
        }

        rule.setActive(false);
        rule = routingRuleRepository.save(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", rule.getId(),
                "DEACTIVATED", null, "ADMIN_API", "true", "false");

        return rule;
    }

    /**
     * Routing rule'ni o'chiradi (hard delete).
     *
     * @param tenantId tenant identifikatori
     * @param ruleId routing rule identifikatori
     * @throws ResourceNotFoundException agar tenant yoki routing rule topilmasa
     */
    public void deleteRoutingRule(UUID tenantId, UUID ruleId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        RoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", ruleId));

        String oldValue = rule.getName() + " | p=" + rule.getPriority();

        routingRuleRepository.delete(rule);

        auditService.recordEvent(tenantId, "ROUTING_RULE", ruleId,
                "DELETED", null, "ADMIN_API", oldValue, null);
    }

    /**
     * DataIntegrityViolationException workflow_definition (tenant_id, name) unique
     * constraint violation ekanligini tekshiradi.
     *
     * PostgreSQL auto-generated constraint nomi: workflow_definition_tenant_id_name_key
     */
    private static boolean isDuplicateNameConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("workflow_definition")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("name");
        }
        return false;
    }

    /**
     * DataIntegrityViolationException telegram_chat_binding (tenant_id, chat_id) unique
     * constraint violation ekanligini tekshiradi.
     */
    private static boolean isDuplicateChatBindingConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null
                    && constraintName.contains("telegram_chat_binding")
                    && constraintName.contains("tenant_id")
                    && constraintName.contains("chat_id");
        }
        return false;
    }
}
