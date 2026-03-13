package com.engops.platform.intake;

import java.util.UUID;

/**
 * Intake natijasi — yaratilgan work item va resolved routing target haqida structured javob.
 * Controller yoki adapter shu natijani o'z formatiga moslashtiradi.
 *
 * Work item metadata:
 * - workItemId, workItemCode, workItemType, title — asosiy identifikatorlar
 * - currentStatusCode — boshlang'ich status
 * - workflowDefinitionId — ishlatilgan workflow
 * - tenantId — tenant konteksti
 *
 * Routing:
 * - routingPrepared=true — mos routing rule topildi, target validated va resolved
 * - routingPrepared=false — mos rule topilmadi, lekin work item yaratilgan (valid result)
 *
 * Resolved target (faqat routingPrepared=true holatda to'ldiriladi):
 * - matchedRoutingRuleId — tanlangan routing rule
 * - targetTopicBindingId — tanlangan topic binding
 * - targetChatBindingId — topic binding'ning chat binding'i
 * - targetTopicId — Telegram topic ID (delivery target)
 */
public class IntakeResult {

    // Work item metadata
    private final UUID workItemId;
    private final String workItemCode;
    private final String workItemType;
    private final String title;
    private final String currentStatusCode;
    private final UUID workflowDefinitionId;
    private final UUID tenantId;

    // Resolved routing target
    private final boolean routingPrepared;
    private final UUID matchedRoutingRuleId;
    private final UUID targetTopicBindingId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public IntakeResult(UUID workItemId, String workItemCode, String workItemType,
                        String title, String currentStatusCode,
                        UUID workflowDefinitionId, UUID tenantId,
                        boolean routingPrepared, UUID matchedRoutingRuleId,
                        UUID targetTopicBindingId, UUID targetChatBindingId,
                        Long targetTopicId) {
        this.workItemId = workItemId;
        this.workItemCode = workItemCode;
        this.workItemType = workItemType;
        this.title = title;
        this.currentStatusCode = currentStatusCode;
        this.workflowDefinitionId = workflowDefinitionId;
        this.tenantId = tenantId;
        this.routingPrepared = routingPrepared;
        this.matchedRoutingRuleId = matchedRoutingRuleId;
        this.targetTopicBindingId = targetTopicBindingId;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
    }

    public UUID getWorkItemId() { return workItemId; }
    public String getWorkItemCode() { return workItemCode; }
    public String getWorkItemType() { return workItemType; }
    public String getTitle() { return title; }
    public String getCurrentStatusCode() { return currentStatusCode; }
    public UUID getWorkflowDefinitionId() { return workflowDefinitionId; }
    public UUID getTenantId() { return tenantId; }
    public boolean isRoutingPrepared() { return routingPrepared; }
    public UUID getMatchedRoutingRuleId() { return matchedRoutingRuleId; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }

    /**
     * Adapter-facing DTO ga konvertatsiya — faqat delivery uchun kerakli ma'lumotlar.
     * Routing internal details (matchedRoutingRuleId, targetTopicBindingId, workflowDefinitionId)
     * bu yerda tashlanadi — adapter uchun faqat final delivery target kerak.
     */
    public PreparedDeliveryTarget toPreparedDeliveryTarget() {
        return new PreparedDeliveryTarget(
                tenantId,
                workItemId, workItemCode, workItemType, title, currentStatusCode,
                routingPrepared,
                targetChatBindingId, targetTopicId);
    }
}
