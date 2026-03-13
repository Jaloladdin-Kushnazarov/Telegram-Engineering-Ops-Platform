package com.engops.platform.intake;

import java.util.UUID;

/**
 * Intake natijasi — yaratilgan work item va routing preparation haqida structured javob.
 * Controller yoki adapter shu natijani o'z formatiga moslashtiradi.
 *
 * Routing preparation:
 * - routingPrepared=true — mos routing rule topildi, targetTopicBindingId aniq
 * - routingPrepared=false — mos rule topilmadi, lekin work item yaratilgan (valid result)
 */
public class IntakeResult {

    private final UUID workItemId;
    private final String workItemCode;
    private final String currentStatusCode;
    private final UUID workflowDefinitionId;
    private final UUID tenantId;

    // Routing preparation
    private final boolean routingPrepared;
    private final UUID matchedRoutingRuleId;
    private final UUID targetTopicBindingId;

    public IntakeResult(UUID workItemId, String workItemCode, String currentStatusCode,
                        UUID workflowDefinitionId, UUID tenantId,
                        boolean routingPrepared, UUID matchedRoutingRuleId,
                        UUID targetTopicBindingId) {
        this.workItemId = workItemId;
        this.workItemCode = workItemCode;
        this.currentStatusCode = currentStatusCode;
        this.workflowDefinitionId = workflowDefinitionId;
        this.tenantId = tenantId;
        this.routingPrepared = routingPrepared;
        this.matchedRoutingRuleId = matchedRoutingRuleId;
        this.targetTopicBindingId = targetTopicBindingId;
    }

    public UUID getWorkItemId() { return workItemId; }
    public String getWorkItemCode() { return workItemCode; }
    public String getCurrentStatusCode() { return currentStatusCode; }
    public UUID getWorkflowDefinitionId() { return workflowDefinitionId; }
    public UUID getTenantId() { return tenantId; }
    public boolean isRoutingPrepared() { return routingPrepared; }
    public UUID getMatchedRoutingRuleId() { return matchedRoutingRuleId; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
}
