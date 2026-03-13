package com.engops.platform.routing;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RoutingDecisionService unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class RoutingDecisionServiceTest {

    @Mock private TenantConfigQueryService tenantConfigQueryService;

    @InjectMocks
    private RoutingDecisionService routingDecisionService;

    private final UUID tenantId = UUID.randomUUID();

    // ==================== SELECTION POLICY TESTLARI ====================

    @Test
    void candidateYoqBolsaNoneQaytaradi() {
        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of());

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isFalse();
        assertThat(decision.getMatchedRoutingRuleId()).isNull();
        assertThat(decision.getTargetTopicBindingId()).isNull();
        assertThat(decision.getTargetChatBindingId()).isNull();
        assertThat(decision.getTargetTopicId()).isNull();
    }

    @Test
    void bittaUnconditionalRuleResolvedTargetMatchedQaytaradi() {
        UUID ruleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 42L;

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.isActive()).thenReturn(true);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(topicBindingId);
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getTopicId()).thenReturn(topicId);
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTopicBindingById(tenantId, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(ruleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(topicBindingId);
        assertThat(decision.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(decision.getTargetTopicId()).isEqualTo(topicId);
    }

    @Test
    void birNechtaUnconditionalTurliPriorityEngYuqoriTanlanadi() {
        UUID highRuleId = UUID.randomUUID();
        UUID highTopicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 99L;

        RoutingRule highRule = mock(RoutingRule.class);
        when(highRule.getId()).thenReturn(highRuleId);
        when(highRule.getPriority()).thenReturn(200);
        when(highRule.getTargetTopicBindingId()).thenReturn(highTopicBindingId);

        RoutingRule lowRule = mock(RoutingRule.class);
        when(lowRule.getPriority()).thenReturn(100);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(highRule, lowRule));

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.isActive()).thenReturn(true);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(highTopicBindingId);
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getTopicId()).thenReturn(topicId);
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTopicBindingById(tenantId, highTopicBindingId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(highRuleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(highTopicBindingId);
        assertThat(decision.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(decision.getTargetTopicId()).isEqualTo(topicId);
    }

    @Test
    void birXilTopPriorityFailFastRadEtilishi() {
        RoutingRule rule1 = mock(RoutingRule.class);
        when(rule1.getPriority()).thenReturn(100);
        RoutingRule rule2 = mock(RoutingRule.class);
        when(rule2.getPriority()).thenReturn(100);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule1, rule2));

        assertThatThrownBy(() -> routingDecisionService.resolve(tenantId, "BUG"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unconditional routing rule bir xil prioritetga");
    }

    @Test
    void faqatConditionalRuleBolsaNoneQaytaradi() {
        RoutingRule conditionalRule = mock(RoutingRule.class);
        when(conditionalRule.getConditionExpression()).thenReturn("severity == 'CRITICAL'");

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(conditionalRule));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isFalse();
        assertThat(decision.getMatchedRoutingRuleId()).isNull();
    }

    @Test
    void mixedRulesConditionalChetlatilibUnconditionalTanlanadi() {
        RoutingRule conditionalHighRule = mock(RoutingRule.class);
        when(conditionalHighRule.getConditionExpression()).thenReturn("severity == 'CRITICAL'");

        UUID unconditionalRuleId = UUID.randomUUID();
        UUID unconditionalTopicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();
        long topicId = 7L;

        RoutingRule unconditionalLowRule = mock(RoutingRule.class);
        when(unconditionalLowRule.getId()).thenReturn(unconditionalRuleId);
        when(unconditionalLowRule.getTargetTopicBindingId()).thenReturn(unconditionalTopicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(conditionalHighRule, unconditionalLowRule));

        TelegramChatBinding chatBinding = mock(TelegramChatBinding.class);
        when(chatBinding.getId()).thenReturn(chatBindingId);
        when(chatBinding.isActive()).thenReturn(true);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.getId()).thenReturn(unconditionalTopicBindingId);
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getTopicId()).thenReturn(topicId);
        when(topicBinding.getChatBinding()).thenReturn(chatBinding);

        when(tenantConfigQueryService.findTopicBindingById(tenantId, unconditionalTopicBindingId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(unconditionalRuleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(unconditionalTopicBindingId);
        assertThat(decision.getTargetChatBindingId()).isEqualTo(chatBindingId);
        assertThat(decision.getTargetTopicId()).isEqualTo(topicId);
    }

    // ==================== TARGET VALIDATION TESTLARI ====================

    @Test
    void matchedRuleTargetNullBolsaFailFast() {
        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getName()).thenReturn("Bug Default Route");

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> routingDecisionService.resolve(tenantId, "BUG"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("targetTopicBindingId ko'rsatilmagan");
    }

    @Test
    void matchedRuleTargetTopilmasaFailFast() {
        UUID topicBindingId = UUID.randomUUID();

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getName()).thenReturn("Bug Default Route");
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));
        when(tenantConfigQueryService.findTopicBindingById(tenantId, topicBindingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routingDecisionService.resolve(tenantId, "BUG"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("topic binding")
                .hasMessageContaining("topilmadi");
    }

    @Test
    void matchedRuleTargetInactiveBolsaFailFast() {
        UUID topicBindingId = UUID.randomUUID();

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getName()).thenReturn("Bug Default Route");
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

        TelegramTopicBinding inactiveBinding = mock(TelegramTopicBinding.class);
        when(inactiveBinding.isActive()).thenReturn(false);
        when(tenantConfigQueryService.findTopicBindingById(tenantId, topicBindingId))
                .thenReturn(Optional.of(inactiveBinding));

        assertThatThrownBy(() -> routingDecisionService.resolve(tenantId, "BUG"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aktiv emas");
    }

    @Test
    void matchedRuleChatBindingInactiveBolsaFailFast() {
        UUID topicBindingId = UUID.randomUUID();
        UUID chatBindingId = UUID.randomUUID();

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getName()).thenReturn("Bug Default Route");
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

        TelegramChatBinding inactiveChatBinding = mock(TelegramChatBinding.class);
        when(inactiveChatBinding.getId()).thenReturn(chatBindingId);
        when(inactiveChatBinding.isActive()).thenReturn(false);

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.isActive()).thenReturn(true);
        when(topicBinding.getChatBinding()).thenReturn(inactiveChatBinding);

        when(tenantConfigQueryService.findTopicBindingById(tenantId, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        assertThatThrownBy(() -> routingDecisionService.resolve(tenantId, "BUG"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chat binding")
                .hasMessageContaining("aktiv emas");
    }
}
