package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tenant konfiguratsiyasi uchun compact details read facade.
 *
 * Admin/support caller'lar uchun tenant'ning operatsion konfiguratsiya holatini
 * bitta chaqiruvda qaytaradi:
 * - tenant metadata
 * - membership summary (total + active count)
 * - workflow summary (total + active count)
 * - routing summary (total + active count)
 * - telegram summary (active chat binding + topic binding count)
 *
 * Delegation:
 * (tenantId)
 *   -> TenantConfigQueryService (tenant, workflow, routing, telegram)
 *   -> IdentityQueryService (membership)
 *   -> TenantConfigDetailsView
 *
 * Muhim:
 * - Faqat mavjud public query service'lar orqali ishlaydi
 * - Repository bypass yo'q
 * - Biznes logika yo'q — faqat thin orchestration va count aggregation
 * - Tenant topilmasa ResourceNotFoundException (404)
 * - Tenant-scoped
 * - Read-only tranzaksiya
 * - Stateless
 */
@Service
@Transactional(readOnly = true)
public class TenantConfigDetailsFacade {

    private final TenantConfigQueryService tenantConfigQueryService;
    private final IdentityQueryService identityQueryService;

    public TenantConfigDetailsFacade(TenantConfigQueryService tenantConfigQueryService,
                                      IdentityQueryService identityQueryService) {
        this.tenantConfigQueryService = tenantConfigQueryService;
        this.identityQueryService = identityQueryService;
    }

    /**
     * Tenant konfiguratsiyasining compact details view'ini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return compact tenant config details
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public TenantConfigDetailsView getDetails(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }

        Tenant tenant = tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        int totalMembershipCount = identityQueryService.listAllMembers(tenantId).size();
        int activeMembershipCount = identityQueryService.listActiveMembers(tenantId).size();

        int totalWorkflowDefinitionCount = tenantConfigQueryService
                .listAllWorkflowDefinitions(tenantId).size();
        int activeWorkflowDefinitionCount = tenantConfigQueryService
                .listActiveWorkflowDefinitions(tenantId).size();

        int totalRoutingRuleCount = tenantConfigQueryService
                .listAllRoutingRules(tenantId).size();
        int activeRoutingRuleCount = tenantConfigQueryService
                .listActiveRoutingRules(tenantId).size();

        List<TelegramChatBinding> activeChatBindings = tenantConfigQueryService
                .listActiveChatBindings(tenantId);
        int activeChatBindingCount = activeChatBindings.size();

        int activeTopicBindingCount = activeChatBindings.stream()
                .mapToInt(cb -> tenantConfigQueryService
                        .listActiveTopicBindings(cb.getId()).size())
                .sum();

        return new TenantConfigDetailsView(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                totalMembershipCount,
                activeMembershipCount,
                totalWorkflowDefinitionCount,
                activeWorkflowDefinitionCount,
                totalRoutingRuleCount,
                activeRoutingRuleCount,
                activeChatBindingCount,
                activeTopicBindingCount);
    }

    /**
     * Tenant uchun barcha workflow ta'riflarining compact ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return workflow ta'riflari ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public WorkflowDefinitionListView getWorkflowDefinitions(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<WorkflowDefinition> definitions =
                tenantConfigQueryService.listAllWorkflowDefinitions(tenantId);

        List<WorkflowDefinitionItemView> items = definitions.stream()
                .sorted(Comparator.comparing(WorkflowDefinition::getName)
                        .thenComparing(WorkflowDefinition::getId))
                .map(d -> new WorkflowDefinitionItemView(
                        d.getId(),
                        d.getName(),
                        d.getWorkItemType(),
                        d.getDescription(),
                        d.isActive(),
                        d.getCreatedAt()))
                .toList();

        return new WorkflowDefinitionListView(tenantId, items);
    }

    /**
     * Tenant uchun barcha routing qoidalarining compact ro'yxatini qaytaradi.
     *
     * @param tenantId tenant identifikatori
     * @return routing qoidalari ro'yxati (priority DESC, name ASC, id ASC)
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public RoutingRuleListView getRoutingRules(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<RoutingRule> rules =
                tenantConfigQueryService.listAllRoutingRules(tenantId);

        List<RoutingRuleItemView> items = rules.stream()
                .sorted(Comparator.comparingInt(RoutingRule::getPriority).reversed()
                        .thenComparing(RoutingRule::getName)
                        .thenComparing(RoutingRule::getId))
                .map(r -> new RoutingRuleItemView(
                        r.getId(),
                        r.getName(),
                        r.getWorkItemType(),
                        r.getPriority(),
                        r.getTargetTopicBindingId(),
                        r.isActive(),
                        r.getCreatedAt()))
                .toList();

        return new RoutingRuleListView(tenantId, items);
    }

    /**
     * Tenant uchun barcha Telegram chat bog'lanishlarining compact ro'yxatini qaytaradi.
     *
     * Har bir chat binding uchun activeTopicBindingCount ham hisoblanadi —
     * mavjud listActiveTopicBindings() orqali.
     *
     * Ordering: bindingType name ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return chat bog'lanishlari ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public ChatBindingListView getChatBindings(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<TelegramChatBinding> bindings =
                tenantConfigQueryService.listAllChatBindings(tenantId);

        List<ChatBindingItemView> items = bindings.stream()
                .sorted(Comparator.comparing(
                                (TelegramChatBinding cb) -> cb.getBindingType().name())
                        .thenComparing(TelegramChatBinding::getId))
                .map(cb -> {
                    int activeTopicCount = tenantConfigQueryService
                            .listActiveTopicBindings(cb.getId()).size();
                    return new ChatBindingItemView(
                            cb.getId(),
                            cb.getChatId(),
                            cb.getChatTitle(),
                            cb.getBindingType().name(),
                            cb.isActive(),
                            activeTopicCount,
                            cb.getCreatedAt());
                })
                .toList();

        return new ChatBindingListView(tenantId, items);
    }

    /**
     * Tenant uchun barcha Telegram topic bog'lanishlarining compact flat ro'yxatini qaytaradi.
     *
     * Barcha chat binding'lar bo'ylab iteratsiya qilib, har birining topic binding'larini
     * yig'adi. Chat kontekst field'lari (chatId, chatTitle) flat sifatida kiritiladi.
     *
     * Ordering: purpose ASC -> id ASC
     *
     * @param tenantId tenant identifikatori
     * @return topic bog'lanishlari flat ro'yxati
     * @throws IllegalArgumentException agar tenantId null bo'lsa
     * @throws ResourceNotFoundException agar tenant topilmasa
     */
    public TopicBindingListView getTopicBindings(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId null bo'lishi mumkin emas");
        }

        tenantConfigQueryService.findTenantById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        List<TelegramChatBinding> chatBindings =
                tenantConfigQueryService.listAllChatBindings(tenantId);

        List<TopicBindingItemView> items = chatBindings.stream()
                .flatMap(cb -> tenantConfigQueryService.listAllTopicBindings(cb.getId())
                        .stream()
                        .map(tb -> new TopicBindingItemView(
                                tb.getId(),
                                cb.getId(),
                                cb.getChatId(),
                                cb.getChatTitle(),
                                tb.getTopicId(),
                                tb.getTopicName(),
                                tb.getPurpose(),
                                tb.isActive(),
                                tb.getCreatedAt())))
                .sorted(Comparator.comparing(TopicBindingItemView::purpose)
                        .thenComparing(TopicBindingItemView::topicBindingId))
                .toList();

        return new TopicBindingListView(tenantId, items);
    }

    /**
     * Facade natija modeli — topic bog'lanishlari ro'yxati.
     */
    public record TopicBindingListView(
            UUID tenantId,
            List<TopicBindingItemView> items) {}

    public record TopicBindingItemView(
            UUID topicBindingId,
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            long topicId,
            String topicName,
            String purpose,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — chat bog'lanishlari ro'yxati.
     */
    public record ChatBindingListView(
            UUID tenantId,
            List<ChatBindingItemView> items) {}

    public record ChatBindingItemView(
            UUID chatBindingId,
            long chatId,
            String chatTitle,
            String bindingType,
            boolean active,
            int activeTopicBindingCount,
            Instant createdAt) {}

    /**
     * Facade natija modeli — routing qoidalari ro'yxati.
     */
    public record RoutingRuleListView(
            UUID tenantId,
            List<RoutingRuleItemView> items) {}

    public record RoutingRuleItemView(
            UUID ruleId,
            String name,
            String workItemType,
            int priority,
            UUID targetTopicBindingId,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — workflow ta'riflari ro'yxati.
     */
    public record WorkflowDefinitionListView(
            UUID tenantId,
            List<WorkflowDefinitionItemView> items) {}

    public record WorkflowDefinitionItemView(
            UUID definitionId,
            String name,
            String workItemType,
            String description,
            boolean active,
            Instant createdAt) {}

    /**
     * Facade natija modeli — compact tenant config details.
     */
    public record TenantConfigDetailsView(
            UUID tenantId,
            String name,
            String slug,
            String timezone,
            String status,
            Instant createdAt,
            int totalMembershipCount,
            int activeMembershipCount,
            int totalWorkflowDefinitionCount,
            int activeWorkflowDefinitionCount,
            int totalRoutingRuleCount,
            int activeRoutingRuleCount,
            int activeChatBindingCount,
            int activeTopicBindingCount) {}
}
