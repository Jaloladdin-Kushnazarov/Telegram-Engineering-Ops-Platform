package com.engops.platform.admin;

import com.engops.platform.identity.IdentityQueryService;
import com.engops.platform.sharedkernel.exception.ResourceNotFoundException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
