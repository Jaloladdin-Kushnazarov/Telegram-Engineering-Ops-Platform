package com.engops.platform.tenantconfig.repository;

import com.engops.platform.infrastructure.config.JpaAuditingConfig;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import com.engops.platform.tenantconfig.model.Tenant;
import com.engops.platform.tenantconfig.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant-config repository testlari.
 * Workflow, chat/topic va routing konfiguratsiya so'rovlarini tekshiradi.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class TenantConfigRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired private TelegramChatBindingRepository telegramChatBindingRepository;
    @Autowired private TelegramTopicBindingRepository telegramTopicBindingRepository;
    @Autowired private RoutingRuleRepository routingRuleRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("Acme Corp", "acme-corp"));
    }

    @Test
    void tenantSlugBoYichaTopish() {
        Optional<Tenant> found = tenantRepository.findBySlug("acme-corp");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Corp");
    }

    @Test
    void workflowDefinitionYaratishVaSorash() {
        WorkflowDefinition wf = new WorkflowDefinition(tenant.getId(), "Bug Workflow", "BUG");
        wf.setDescription("Standart bug workflow");
        workflowDefinitionRepository.save(wf);

        List<WorkflowDefinition> active = workflowDefinitionRepository
                .findByTenantIdAndActiveTrue(tenant.getId());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getWorkItemType()).isEqualTo("BUG");

        Optional<WorkflowDefinition> byType = workflowDefinitionRepository
                .findByTenantIdAndWorkItemType(tenant.getId(), "BUG");
        assertThat(byType).isPresent();
    }

    @Test
    void chatVaTopicBindingYaratish() {
        TelegramChatBinding chat = new TelegramChatBinding(tenant.getId(), -100123456L, "Acme Ops");
        telegramChatBindingRepository.save(chat);

        TelegramTopicBinding topic = new TelegramTopicBinding(chat, 42L, "Bugs", "BUG_TOPIC");
        telegramTopicBindingRepository.save(topic);

        List<TelegramChatBinding> chats = telegramChatBindingRepository
                .findByTenantIdAndActiveTrue(tenant.getId());
        assertThat(chats).hasSize(1);

        List<TelegramTopicBinding> topics = telegramTopicBindingRepository
                .findByChatBindingIdAndActiveTrue(chat.getId());
        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).getPurpose()).isEqualTo("BUG_TOPIC");
    }

    @Test
    void routingRulePrioritetBoYichaSorash() {
        RoutingRule r1 = new RoutingRule(tenant.getId(), "Bug to Bugs Topic", "BUG");
        r1.setPriority(10);
        routingRuleRepository.save(r1);

        RoutingRule r2 = new RoutingRule(tenant.getId(), "Incident to Incidents Topic", "INCIDENT");
        r2.setPriority(20);
        routingRuleRepository.save(r2);

        List<RoutingRule> rules = routingRuleRepository
                .findByTenantIdAndActiveTrueOrderByPriorityDesc(tenant.getId());
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).getPriority()).isGreaterThan(rules.get(1).getPriority());
    }
}
