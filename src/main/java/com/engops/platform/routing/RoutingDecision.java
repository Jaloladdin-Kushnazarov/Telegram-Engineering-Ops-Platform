package com.engops.platform.routing;

import java.util.UUID;

/**
 * Routing qarori natijasi — work item uchun mos routing rule topilganmi yoki yo'qmi.
 *
 * - prepared=true — unconditional mos rule topildi
 * - prepared=false — mos rule topilmadi (valid holat, work item yaratilgan)
 */
public class RoutingDecision {

    private final boolean prepared;
    private final UUID matchedRoutingRuleId;
    private final UUID targetTopicBindingId;

    private RoutingDecision(boolean prepared, UUID matchedRoutingRuleId, UUID targetTopicBindingId) {
        this.prepared = prepared;
        this.matchedRoutingRuleId = matchedRoutingRuleId;
        this.targetTopicBindingId = targetTopicBindingId;
    }

    public static RoutingDecision none() {
        return new RoutingDecision(false, null, null);
    }

    public static RoutingDecision matched(UUID routingRuleId, UUID targetTopicBindingId) {
        return new RoutingDecision(true, routingRuleId, targetTopicBindingId);
    }

    public boolean isPrepared() { return prepared; }
    public UUID getMatchedRoutingRuleId() { return matchedRoutingRuleId; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
}
