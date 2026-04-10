package com.engops.platform.admin;

import java.util.UUID;

/**
 * Routing rule PATCH yangilash uchun HTTP request DTO.
 *
 * PATCH semantikasi: faqat JSON'da mavjud bo'lgan field'lar yangilanadi.
 * - name berilmasa — mavjud nom saqlanadi
 * - priority berilmasa — mavjud prioritet saqlanadi
 * - targetTopicBindingId berilmasa — o'zgarmaydi; explicit null — tozalanadi
 * - conditionExpression berilmasa — o'zgarmaydi; null/blank — tozalanadi
 *
 * Jackson faqat JSON'da mavjud field'lar uchun setter chaqiradi,
 * shuning uchun provided flag'lar orqali omitted vs explicit null farqlanadi.
 */
public class UpdateRoutingRuleRequest {

    private String name;
    private boolean nameProvided;

    private Integer priority;
    private boolean priorityProvided;

    private UUID targetTopicBindingId;
    private boolean targetTopicBindingIdProvided;

    private String conditionExpression;
    private boolean conditionExpressionProvided;

    public UpdateRoutingRuleRequest() {}

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) {
        this.priority = priority;
        this.priorityProvided = true;
    }

    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
    public void setTargetTopicBindingId(UUID targetTopicBindingId) {
        this.targetTopicBindingId = targetTopicBindingId;
        this.targetTopicBindingIdProvided = true;
    }

    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
        this.conditionExpressionProvided = true;
    }

    public boolean isNameProvided() { return nameProvided; }
    public boolean isPriorityProvided() { return priorityProvided; }
    public boolean isTargetTopicBindingIdProvided() { return targetTopicBindingIdProvided; }
    public boolean isConditionExpressionProvided() { return conditionExpressionProvided; }
}
