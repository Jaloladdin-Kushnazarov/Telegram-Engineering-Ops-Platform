package com.engops.platform.routing;

import java.util.UUID;

/**
 * Routing qarori natijasi — work item uchun resolved routing target.
 *
 * - prepared=true — unconditional mos rule topildi, target validated va resolved
 * - prepared=false — mos rule topilmadi (valid holat, work item yaratilgan)
 *
 * Matched holatda future consumer (Telegram adapter) uchun tayyor delivery target:
 * - matchedRoutingRuleId — qaysi rule tanlangani
 * - targetTopicBindingId — qaysi topic binding tanlangani
 * - targetChatBindingId — qaysi chat binding ichida ekanligi
 * - targetTopicId — Telegram topic ID (actual delivery target)
 */
public class RoutingDecision {

    private final boolean prepared;
    private final UUID matchedRoutingRuleId;
    private final UUID targetTopicBindingId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    private RoutingDecision(boolean prepared, UUID matchedRoutingRuleId,
                            UUID targetTopicBindingId, UUID targetChatBindingId,
                            Long targetTopicId) {
        this.prepared = prepared;
        this.matchedRoutingRuleId = matchedRoutingRuleId;
        this.targetTopicBindingId = targetTopicBindingId;
        this.targetChatBindingId = targetChatBindingId;
        this.targetTopicId = targetTopicId;
    }

    public static RoutingDecision none() {
        return new RoutingDecision(false, null, null, null, null);
    }

    public static RoutingDecision matched(UUID routingRuleId, UUID targetTopicBindingId,
                                           UUID targetChatBindingId, long targetTopicId) {
        return new RoutingDecision(true, routingRuleId, targetTopicBindingId,
                targetChatBindingId, targetTopicId);
    }

    public boolean isPrepared() { return prepared; }
    public UUID getMatchedRoutingRuleId() { return matchedRoutingRuleId; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }
}
