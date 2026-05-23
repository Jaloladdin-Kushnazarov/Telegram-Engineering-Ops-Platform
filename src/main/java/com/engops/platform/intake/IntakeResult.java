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

    // Phase 194 — work item attribute snapshot captured at intake commit time
    // so the AFTER_COMMIT projection pipeline can render optional Telegram
    // card lines without re-reading the WorkItem. Both fields are nullable.
    // Owner intentionally NOT carried (Phase 196 scope).
    private final String priorityCode;
    private final String severityCode;

    // Phase 195 — owner snapshot for HTTP response echo. NOT yet rendered on
    // the Telegram card (Phase 196 scope) — the projection pipeline does not
    // consume this field. Surfaced through the HTTP response so the intake
    // client can confirm the owner that was applied at create time.
    private final UUID currentOwnerUserId;

    // Resolved routing target
    private final boolean routingPrepared;
    private final UUID matchedRoutingRuleId;
    private final UUID targetTopicBindingId;
    private final UUID targetChatBindingId;
    private final Long targetTopicId;

    public IntakeResult(UUID workItemId, String workItemCode, String workItemType,
                        String title, String currentStatusCode,
                        UUID workflowDefinitionId, UUID tenantId,
                        String priorityCode, String severityCode,
                        UUID currentOwnerUserId,
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
        this.priorityCode = priorityCode;
        this.severityCode = severityCode;
        this.currentOwnerUserId = currentOwnerUserId;
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
    public String getPriorityCode() { return priorityCode; }
    public String getSeverityCode() { return severityCode; }
    public UUID getCurrentOwnerUserId() { return currentOwnerUserId; }
    public boolean isRoutingPrepared() { return routingPrepared; }
    public UUID getMatchedRoutingRuleId() { return matchedRoutingRuleId; }
    public UUID getTargetTopicBindingId() { return targetTopicBindingId; }
    public UUID getTargetChatBindingId() { return targetChatBindingId; }
    public Long getTargetTopicId() { return targetTopicId; }

    /**
     * Adapter-facing DTO ga konvertatsiya — faqat delivery uchun kerakli ma'lumotlar.
     * Routing internal details (matchedRoutingRuleId, targetTopicBindingId, workflowDefinitionId)
     * bu yerda tashlanadi — adapter uchun faqat final delivery target kerak.
     *
     * <p>Phase 196 backward-compat overload: hech qanday owner display label
     * mavjud emas (null bilan delegate qiladi). Mavjud test sirti
     * ({@code toPreparedDeliveryTargetRoutingPreparedHolatda},
     * {@code toPreparedDeliveryTargetRoutingYoqHolatda},
     * {@code toPreparedDeliveryTargetForwardsPriorityAndSeverity}) shu yo'l
     * orqali invariantni saqlaydi.</p>
     */
    public PreparedDeliveryTarget toPreparedDeliveryTarget() {
        return toPreparedDeliveryTarget(null);
    }

    /**
     * Phase 196 — adapter-facing DTO ga konvertatsiya, oldindan resolve
     * qilingan owner display label bilan. Production yo'lda
     * {@code IntakeApplicationService.publishTelegramCardDispatchEventSafely}
     * shu overload'ni chaqiradi: u {@code IdentityQueryService.findUserById}
     * orqali {@code AppUser.displayName}'ni hal qilib, natijani shu yerga
     * uzatadi. Raw owner UUID hech qachon bu zanjirga kirmaydi —
     * faqat oldindan tayyorlangan label String'i.
     *
     * @param ownerDisplayLabel nullable — null/blank bo'lsa Telegram render
     *                          {@code Owner: ...} qatorini chiqarmaydi.
     */
    public PreparedDeliveryTarget toPreparedDeliveryTarget(String ownerDisplayLabel) {
        return new PreparedDeliveryTarget(
                tenantId,
                workItemId, workItemCode, workItemType, title, currentStatusCode,
                priorityCode, severityCode,
                routingPrepared,
                targetChatBindingId, targetTopicId,
                ownerDisplayLabel);
    }
}
