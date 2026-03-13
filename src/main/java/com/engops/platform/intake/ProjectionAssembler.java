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

        return new ProjectionPayload(
                target.getTenantId(),
                target.getWorkItemId(),
                target.getWorkItemCode(),
                target.getWorkItemType(),
                target.getTitle(),
                target.getCurrentStatusCode(),
                target.isDeliveryReady(),
                target.getTargetChatBindingId(),
                target.getTargetTopicId());
    }
}
