package com.engops.platform.intake;

import org.springframework.stereotype.Component;

/**
 * PreparedDeliveryTarget → ProjectionPayload konvertatsiya qatlami.
 *
 * Bu assembler intake module'ning internal chiqishini (PreparedDeliveryTarget)
 * adapter-consumed stabil contract'ga (ProjectionPayload) aylantiradi.
 *
 * Hozirgi holatda bu pure mapping — hech qanday repository yoki external lookup yo'q.
 * Keyingi phase'larda bu assembler ichida qo'shimcha enrichment bo'lishi mumkin:
 * - display hint'lar
 * - computed field'lar
 * - adapter-neutral formatting context
 *
 * Bu enrichment PreparedDeliveryTarget'ni o'zgartirmaydi —
 * faqat ProjectionPayload'ga qo'shiladi.
 *
 * Muhim: bu assembler business rule'larni takrorlamaydi.
 * Barcha business qarorlar allaqachon IntakeApplicationService tomonidan qilingan.
 */
@Component
public class ProjectionAssembler {

    /**
     * PreparedDeliveryTarget dan projection-ready payload hosil qiladi.
     *
     * @param target allaqachon resolved delivery target
     * @return adapter-ready projection payload
     * @throws IllegalArgumentException agar target null bo'lsa
     */
    public ProjectionPayload assemble(PreparedDeliveryTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("PreparedDeliveryTarget null bo'lishi mumkin emas");
        }

        String displayTitle = buildDisplayTitle(target.getWorkItemCode(), target.getTitle());
        String displayTypeLabel = buildDisplayTypeLabel(target.getWorkItemType());

        return new ProjectionPayload(
                target.getTenantId(),
                target.getWorkItemId(),
                target.getWorkItemCode(),
                target.getWorkItemType(),
                target.getTitle(),
                target.getCurrentStatusCode(),
                displayTitle,
                displayTypeLabel,
                target.isDeliveryReady(),
                target.getTargetChatBindingId(),
                target.getTargetTopicId());
    }

    /**
     * "[BUG-1] Login xato" formatida display title hosil qiladi.
     */
    private String buildDisplayTitle(String workItemCode, String title) {
        return "[" + workItemCode + "] " + title;
    }

    /**
     * "BUG" → "Bug", "INCIDENT" → "Incident", "TASK" → "Task".
     * WorkItemType enum nomidan odam o'qiydigan label hosil qiladi.
     */
    private String buildDisplayTypeLabel(String workItemType) {
        if (workItemType == null || workItemType.isEmpty()) {
            return workItemType;
        }
        return workItemType.substring(0, 1).toUpperCase()
                + workItemType.substring(1).toLowerCase();
    }
}
