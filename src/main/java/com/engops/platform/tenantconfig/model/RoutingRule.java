package com.engops.platform.tenantconfig.model;

import com.engops.platform.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Yo'naltirish qoidasi — work itemlarni tegishli topic'ga yo'naltirish uchun.
 * Masalan: BUG tipidagi itemlar "bugs" topic'iga yo'naltiriladi.
 */
@Entity
@Table(name = "routing_rule")
public class RoutingRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 50)
    @Column(name = "work_item_type", nullable = false)
    private String workItemType;

    @Column(name = "target_topic_binding_id")
    private UUID targetTopicBindingId;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    protected RoutingRule() {}

    public RoutingRule(UUID tenantId, String name, String workItemType) {
        this.tenantId = tenantId;
        this.name = name;
        this.workItemType = workItemType;
    }

    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorkItemType() { return workItemType; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
    public void setTargetTopicBindingId(UUID targetTopicBindingId) { this.targetTopicBindingId = targetTopicBindingId; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
}
