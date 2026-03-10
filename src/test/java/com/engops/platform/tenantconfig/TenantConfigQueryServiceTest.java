package com.engops.platform.tenantconfig;

import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import com.engops.platform.tenantconfig.repository.RoutingRuleRepository;
import com.engops.platform.tenantconfig.repository.TelegramChatBindingRepository;
import com.engops.platform.tenantconfig.repository.TelegramTopicBindingRepository;
import com.engops.platform.tenantconfig.repository.TenantRepository;
import com.engops.platform.tenantconfig.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TenantConfigQueryService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class TenantConfigQueryServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Mock private TelegramChatBindingRepository telegramChatBindingRepository;
    @Mock private TelegramTopicBindingRepository telegramTopicBindingRepository;
    @Mock private RoutingRuleRepository routingRuleRepository;

    @InjectMocks
    private TenantConfigQueryService tenantConfigQueryService;

    @Test
    void slugBoYichaTenantTopish() {
        Tenant tenant = new Tenant("Acme", "acme");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        Optional<Tenant> result = tenantConfigQueryService.findTenantBySlug("acme");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Acme");
    }

    @Test
    void aktivWorkflowlarniRoyxatlash() {
        UUID tenantId = UUID.randomUUID();
        WorkflowDefinition wf = new WorkflowDefinition(tenantId, "Bug Flow", "BUG");

        when(workflowDefinitionRepository.findByTenantIdAndActiveTrue(tenantId))
                .thenReturn(List.of(wf));

        List<WorkflowDefinition> result = tenantConfigQueryService
                .listActiveWorkflowDefinitions(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWorkItemType()).isEqualTo("BUG");
    }

    @Test
    void aktivChatBindinglarniRoyxatlash() {
        UUID tenantId = UUID.randomUUID();
        TelegramChatBinding chat = new TelegramChatBinding(tenantId, -100123L, "Chat");

        when(telegramChatBindingRepository.findByTenantIdAndActiveTrue(tenantId))
                .thenReturn(List.of(chat));

        List<TelegramChatBinding> result = tenantConfigQueryService.listActiveChatBindings(tenantId);

        assertThat(result).hasSize(1);
    }

    @Test
    void aktivRoutingQoidalariniRoyxatlash() {
        UUID tenantId = UUID.randomUUID();
        RoutingRule rule = new RoutingRule(tenantId, "Bug Route", "BUG");

        when(routingRuleRepository.findByTenantIdAndActiveTrueOrderByPriorityDesc(tenantId))
                .thenReturn(List.of(rule));

        List<RoutingRule> result = tenantConfigQueryService.listActiveRoutingRules(tenantId);

        assertThat(result).hasSize(1);
    }
}
