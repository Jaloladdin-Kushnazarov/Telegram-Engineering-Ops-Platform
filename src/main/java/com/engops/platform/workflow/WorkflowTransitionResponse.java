package com.engops.platform.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow transition natijasi uchun HTTP response DTO.
 *
 * Field'lar transition qilingan {@code WorkItem}'dan to'g'ridan-to'g'ri olinadi
 * (model getters orqali — yangi getter qo'shilmaydi). previousStatusCode mavjud
 * service/model surface'ida ochilmagan, shuning uchun response'da qaytarilmaydi —
 * faqat yangi (joriy) status ko'rsatiladi.
 *
 * @param tenantId tenant identifikatori
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi (BUG-1 va h.k.)
 * @param typeCode work item turi
 * @param title sarlavha
 * @param currentStatusCode transition'dan keyingi joriy status
 * @param workflowDefinitionId workflow definition
 * @param currentOwnerUserId joriy egasi (nullable)
 * @param lastTransitionAt oxirgi transition vaqti
 * @param resolvedAt yakunlangan vaqt (nullable, faqat terminal status'ga o'tganda)
 * @param reopenedCount qayta ochilish hisobi
 * @param updatedAt oxirgi o'zgartirish vaqti
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowTransitionResponse(
        UUID tenantId,
        UUID workItemId,
        String workItemCode,
        String typeCode,
        String title,
        String currentStatusCode,
        UUID workflowDefinitionId,
        UUID currentOwnerUserId,
        Instant lastTransitionAt,
        Instant resolvedAt,
        int reopenedCount,
        Instant updatedAt) {}
