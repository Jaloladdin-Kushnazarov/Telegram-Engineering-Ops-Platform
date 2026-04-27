package com.engops.platform.tenantconfig;

import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.RoutingRuleRepository;
import com.engops.platform.tenantconfig.repository.TelegramChatBindingRepository;
import com.engops.platform.tenantconfig.repository.TelegramTopicBindingRepository;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun so'rov (query) servisi.
 * Boshqa modullar tenant sozlamalari, workflow, chat/topic va routing
 * ma'lumotlarini shu servis orqali oladi.
 */
@Service
@Transactional(readOnly = true)
public class TenantConfigQueryService {

    private final TenantRepository tenantRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final TelegramChatBindingRepository telegramChatBindingRepository;
    private final TelegramTopicBindingRepository telegramTopicBindingRepository;
    private final RoutingRuleRepository routingRuleRepository;

    public TenantConfigQueryService(TenantRepository tenantRepository,
                                     WorkflowDefinitionRepository workflowDefinitionRepository,
                                     TelegramChatBindingRepository telegramChatBindingRepository,
                                     TelegramTopicBindingRepository telegramTopicBindingRepository,
                                     RoutingRuleRepository routingRuleRepository) {
        this.tenantRepository = tenantRepository;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.telegramChatBindingRepository = telegramChatBindingRepository;
        this.telegramTopicBindingRepository = telegramTopicBindingRepository;
        this.routingRuleRepository = routingRuleRepository;
    }

    /**
     * ID bo'yicha tenantni topadi.
     */
    public Optional<Tenant> findTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId);
    }

    /**
     * Slug bo'yicha tenantni topadi.
     */
    public Optional<Tenant> findTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug);
    }

    /**
     * Tenant uchun barcha workflow ta'riflarini qaytaradi (barcha statuslar).
     */
    public List<WorkflowDefinition> listAllWorkflowDefinitions(UUID tenantId) {
        return workflowDefinitionRepository.findByTenantId(tenantId);
    }

    /**
     * Tenant uchun barcha yo'naltirish qoidalarini qaytaradi (barcha statuslar).
     */
    public List<RoutingRule> listAllRoutingRules(UUID tenantId) {
        return routingRuleRepository.findByTenantId(tenantId);
    }

    /**
     * Tenant-safe lookup: ID va tenant bo'yicha routing rule'ni topadi.
     * Admin moduli routing rule detail read surface uchun ishlatadi —
     * shu metod orqali ham mavjudlik, ham tenant ownership bir lookup'da
     * tekshiriladi (cross-tenant himoya).
     */
    public Optional<RoutingRule> findRoutingRuleById(UUID tenantId, UUID ruleId) {
        return routingRuleRepository.findByIdAndTenantId(ruleId, tenantId);
    }

    /**
     * Tenant va ID bo'yicha workflow ta'rifini topadi (tenant-safe).
     */
    public Optional<WorkflowDefinition> findWorkflowDefinitionById(UUID tenantId, UUID definitionId) {
        return workflowDefinitionRepository.findByTenantIdAndId(tenantId, definitionId);
    }

    /**
     * Tenant uchun aktiv workflow ta'riflarini qaytaradi.
     */
    public List<WorkflowDefinition> listActiveWorkflowDefinitions(UUID tenantId) {
        return workflowDefinitionRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    /**
     * Tenant va work item turi uchun workflow ta'rifini topadi.
     */
    public Optional<WorkflowDefinition> findWorkflowDefinition(UUID tenantId, String workItemType) {
        return workflowDefinitionRepository.findByTenantIdAndWorkItemType(tenantId, workItemType);
    }

    /**
     * Tenant va work item turi uchun aktiv workflow ta'riflarini qaytaradi.
     * Ambiguity tekshiruvi uchun list qaytaradi — caller 0, 1 yoki ko'p natijani o'zi handle qiladi.
     */
    public List<WorkflowDefinition> findActiveWorkflowDefinitionsByType(UUID tenantId, String workItemType) {
        return workflowDefinitionRepository.findByTenantIdAndWorkItemTypeAndActiveTrue(tenantId, workItemType);
    }

    /**
     * Tenant uchun barcha Telegram chat bog'lanishlarini qaytaradi (barcha statuslar).
     */
    public List<TelegramChatBinding> listAllChatBindings(UUID tenantId) {
        return telegramChatBindingRepository.findByTenantId(tenantId);
    }

    /**
     * Tenant uchun aktiv Telegram chat bog'lanishlarini qaytaradi.
     */
    public List<TelegramChatBinding> listActiveChatBindings(UUID tenantId) {
        return telegramChatBindingRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    /**
     * Tenant va chat ID uchun chat bog'lanishini topadi.
     */
    public Optional<TelegramChatBinding> findChatBinding(UUID tenantId, long chatId) {
        return telegramChatBindingRepository.findByTenantIdAndChatId(tenantId, chatId);
    }

    /**
     * Tenant-safe lookup: ID va tenant bo'yicha chat bog'lanishini topadi.
     * Admin moduli chat binding detail read surface uchun ishlatadi —
     * shu metod orqali ham mavjudlik, ham tenant ownership bir lookup'da
     * tekshiriladi (cross-tenant himoya).
     */
    public Optional<TelegramChatBinding> findChatBindingById(UUID tenantId, UUID chatBindingId) {
        return telegramChatBindingRepository.findByIdAndTenantId(chatBindingId, tenantId);
    }

    /**
     * Chat bog'lanishi uchun barcha topic bog'lanishlarini qaytaradi (barcha statuslar).
     */
    public List<TelegramTopicBinding> listAllTopicBindings(UUID chatBindingId) {
        return telegramTopicBindingRepository.findByChatBindingId(chatBindingId);
    }

    /**
     * Chat bog'lanishi uchun aktiv topic bog'lanishlarini qaytaradi.
     */
    public List<TelegramTopicBinding> listActiveTopicBindings(UUID chatBindingId) {
        return telegramTopicBindingRepository.findByChatBindingIdAndActiveTrue(chatBindingId);
    }

    /**
     * Tenant uchun aktiv yo'naltirish qoidalarini qaytaradi (prioritet bo'yicha).
     */
    public List<RoutingRule> listActiveRoutingRules(UUID tenantId) {
        return routingRuleRepository.findByTenantIdAndActiveTrueOrderByPriorityDesc(tenantId);
    }

    /**
     * Tenant va work item turi uchun yo'naltirish qoidalarini qaytaradi.
     */
    public List<RoutingRule> findRoutingRules(UUID tenantId, String workItemType) {
        return routingRuleRepository.findByTenantIdAndWorkItemType(tenantId, workItemType);
    }

    /**
     * Tenant-safe lookup: ID va tenant bo'yicha topic bindingni topadi.
     * Routing target validatsiyasi uchun ishlatiladi.
     */
    public Optional<TelegramTopicBinding> findTopicBindingById(UUID tenantId, UUID topicBindingId) {
        return telegramTopicBindingRepository.findByIdAndChatBinding_TenantId(topicBindingId, tenantId);
    }

    /**
     * Tenant va work item turi uchun aktiv yo'naltirish qoidalarini qaytaradi (prioritet bo'yicha DESC).
     * Routing preparation uchun — faqat aktiv va type-mos rule'lar qaytadi.
     */
    public List<RoutingRule> findActiveRoutingRulesByType(UUID tenantId, String workItemType) {
        return routingRuleRepository.findByTenantIdAndWorkItemTypeAndActiveTrueOrderByPriorityDesc(
                tenantId, workItemType);
    }
}
