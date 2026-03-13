package com.engops.platform.routing;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.RoutingRule;
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
    }

    @Test
    void bittaUnconditionalRuleValidTargetMatchedQaytaradi() {
        UUID ruleId = UUID.randomUUID();
        UUID topicBindingId = UUID.randomUUID();

        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getId()).thenReturn(ruleId);
        when(rule.getTargetTopicBindingId()).thenReturn(topicBindingId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(rule));

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.isActive()).thenReturn(true);
        when(tenantConfigQueryService.findTopicBindingById(tenantId, topicBindingId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(ruleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(topicBindingId);
    }

    @Test
    void birNechtaUnconditionalTurliPriorityEngYuqoriTanlanadi() {
        UUID highRuleId = UUID.randomUUID();
        UUID highTopicId = UUID.randomUUID();

        RoutingRule highRule = mock(RoutingRule.class);
        when(highRule.getId()).thenReturn(highRuleId);
        when(highRule.getPriority()).thenReturn(200);
        when(highRule.getTargetTopicBindingId()).thenReturn(highTopicId);

        RoutingRule lowRule = mock(RoutingRule.class);
        when(lowRule.getPriority()).thenReturn(100);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(highRule, lowRule));

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.isActive()).thenReturn(true);
        when(tenantConfigQueryService.findTopicBindingById(tenantId, highTopicId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(highRuleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(highTopicId);
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
        UUID unconditionalTopicId = UUID.randomUUID();
        RoutingRule unconditionalLowRule = mock(RoutingRule.class);
        when(unconditionalLowRule.getId()).thenReturn(unconditionalRuleId);
        when(unconditionalLowRule.getTargetTopicBindingId()).thenReturn(unconditionalTopicId);

        when(tenantConfigQueryService.findActiveRoutingRulesByType(tenantId, "BUG"))
                .thenReturn(List.of(conditionalHighRule, unconditionalLowRule));

        TelegramTopicBinding topicBinding = mock(TelegramTopicBinding.class);
        when(topicBinding.isActive()).thenReturn(true);
        when(tenantConfigQueryService.findTopicBindingById(tenantId, unconditionalTopicId))
                .thenReturn(Optional.of(topicBinding));

        RoutingDecision decision = routingDecisionService.resolve(tenantId, "BUG");

        assertThat(decision.isPrepared()).isTrue();
        assertThat(decision.getMatchedRoutingRuleId()).isEqualTo(unconditionalRuleId);
        assertThat(decision.getTargetTopicBindingId()).isEqualTo(unconditionalTopicId);
    }

    // ==================== TARGET VALIDATION TESTLARI ====================

    @Test
    void matchedRuleTargetNullBolsaFailFast() {
        RoutingRule rule = mock(RoutingRule.class);
        when(rule.getName()).thenReturn("Bug Default Route");
        // getTargetTopicBindingId() default null

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
}
