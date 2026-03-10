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
}
