package com.engops.platform.routing;

import com.engops.platform.sharedkernel.exception.BusinessRuleException;
import com.engops.platform.tenantconfig.TenantConfigQueryService;
import com.engops.platform.tenantconfig.model.RoutingRule;
import com.engops.platform.tenantconfig.model.TelegramChatBinding;
import com.engops.platform.tenantconfig.model.TelegramTopicBinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Routing decision servisi — work item turi bo'yicha mos unconditional routing rule topadi,
 * tanlangan target'ni validatsiya qiladi va resolved delivery target qaytaradi.
 *
 * Hozirgi phase'da faqat unconditional rule'lar (conditionExpression == null yoki blank)
 * candidate sifatida qatnashadi. Conditional rule'lar evaluate qilinmaydi —
 * ular full routing engine phase'da ishga tushiriladi.
 *
 * Deterministic selection policy (unconditional candidatelar orasida):
 * - 0 ta mos rule → prepared=false (valid holat)
 * - 1 ta mos rule → shu ishlatiladi
 * - N ta mos rule → eng yuqori priority tanlanadi (DESC tartibda birinchi)
 * - Agar 2+ rule bir xil eng yuqori priority'ga ega → fail-fast (noaniqlik)
 *
 * Target validation (matched rule uchun):
 * - targetTopicBindingId null bo'lmasligi kerak
 * - shu tenant uchun topilishi kerak (cross-tenant himoya)
 * - topic binding active bo'lishi kerak
 * - chat binding active bo'lishi kerak
 *
 * Resolved target projection:
 * - matchedRoutingRuleId, targetTopicBindingId, targetChatBindingId, targetTopicId
 *
 * Side effect yo'q — faqat query, selection, validation va projection.
 *
 * Cross-module bog'lanishlar:
 * - TenantConfigQueryService — routing rule'larni va topic binding'larni olish uchun (public API)
 */
@Service
@Transactional(readOnly = true)
public class RoutingDecisionService {

    private final TenantConfigQueryService tenantConfigQueryService;

    public RoutingDecisionService(TenantConfigQueryService tenantConfigQueryService) {
        this.tenantConfigQueryService = tenantConfigQueryService;
    }

    /**
     * Tenant va work item turi bo'yicha routing qarorini aniqlaydi.
     * Matched rule topilsa, uning target topic binding'i ham validatsiya qilinadi.
     *
     * @param tenantId tenant identifikatori
     * @param workItemType work item turi (masalan "BUG", "INCIDENT", "TASK")
     * @return routing qarori — matched yoki none
     */
    public RoutingDecision resolve(UUID tenantId, String workItemType) {
        List<RoutingRule> activeRules = tenantConfigQueryService
                .findActiveRoutingRulesByType(tenantId, workItemType);

        // Faqat unconditional rule'lar — conditionExpression null yoki blank
        List<RoutingRule> candidates = activeRules.stream()
                .filter(rule -> rule.getConditionExpression() == null
                        || rule.getConditionExpression().isBlank())
                .toList();

        if (candidates.isEmpty()) {
            return RoutingDecision.none();
        }

        // candidates allaqachon priority DESC tartibda (repository query tartibini saqlab qoladi)
        RoutingRule topRule = candidates.getFirst();

        // Noaniqlik tekshiruvi: agar 2+ unconditional rule bir xil eng yuqori priority'ga ega bo'lsa
        if (candidates.size() > 1) {
            RoutingRule secondRule = candidates.get(1);
            if (topRule.getPriority() == secondRule.getPriority()) {
                throw new BusinessRuleException("AMBIGUOUS_ROUTING",
                        "'" + workItemType + "' turi uchun " + countRulesWithPriority(candidates, topRule.getPriority())
                                + " ta unconditional routing rule bir xil prioritetga (=" + topRule.getPriority()
                                + ") ega. Prioritetlarni aniqlashtiring");
            }
        }

        // Target validation va resolve — matched rule'ning target'i valid va resolved bo'lsin
        TelegramTopicBinding resolvedTarget = validateAndResolveTarget(tenantId, topRule);
        TelegramChatBinding chatBinding = resolvedTarget.getChatBinding();

        return RoutingDecision.matched(
                topRule.getId(),
                resolvedTarget.getId(),
                chatBinding.getId(),
                resolvedTarget.getTopicId());
    }

    /**
     * Matched routing rule'ning targetTopicBindingId'ini validatsiya qiladi va resolved target qaytaradi:
     * - targetTopicBindingId null bo'lmasligi kerak
     * - shu tenant uchun topilishi kerak
     * - topic binding active bo'lishi kerak
     * - chat binding active bo'lishi kerak
     */
    private TelegramTopicBinding validateAndResolveTarget(UUID tenantId, RoutingRule rule) {
        UUID targetId = rule.getTargetTopicBindingId();

        if (targetId == null) {
            throw new BusinessRuleException("ROUTING_TARGET_MISSING",
                    "'" + rule.getName() + "' routing rule uchun targetTopicBindingId ko'rsatilmagan");
        }

        TelegramTopicBinding topicBinding = tenantConfigQueryService
                .findTopicBindingById(tenantId, targetId)
                .orElseThrow(() -> new BusinessRuleException("ROUTING_TARGET_NOT_FOUND",
                        "'" + rule.getName() + "' routing rule ko'rsatayotgan topic binding (id="
                                + targetId + ") shu tenant uchun topilmadi"));

        if (!topicBinding.isActive()) {
            throw new BusinessRuleException("ROUTING_TARGET_INACTIVE",
                    "'" + rule.getName() + "' routing rule ko'rsatayotgan topic binding (id="
                            + targetId + ") aktiv emas");
        }

        TelegramChatBinding chatBinding = topicBinding.getChatBinding();
        if (!chatBinding.isActive()) {
            throw new BusinessRuleException("ROUTING_CHAT_BINDING_INACTIVE",
                    "'" + rule.getName() + "' routing rule ko'rsatayotgan topic binding'ning "
                            + "chat binding'i (id=" + chatBinding.getId() + ") aktiv emas");
        }

        return topicBinding;
    }

    private long countRulesWithPriority(List<RoutingRule> rules, int priority) {
        return rules.stream().filter(r -> r.getPriority() == priority).count();
    }
}
