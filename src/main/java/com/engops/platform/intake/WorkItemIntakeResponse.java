package com.engops.platform.intake;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Work item intake natijasi uchun HTTP response DTO.
 *
 * Field'lar IntakeResult'dan to'liq olinadi. Yangi field qo'shilmaydi.
 *
 * Routing field'lar (matchedRoutingRuleId, targetTopicBindingId,
 * targetChatBindingId, targetTopicId) faqat routingPrepared=true holatda
 * to'ldiriladi; aks holda null bo'ladi va JSON'da o'tkazib yuboriladi
 * (@JsonInclude(NON_NULL)).
 *
 * @param tenantId tenant identifikatori
 * @param workItemId yaratilgan work item identifikatori
 * @param workItemCode work item kodi (masalan BUG-1)
 * @param typeCode work item turi
 * @param title sarlavha
 * @param currentStatusCode boshlang'ich (joriy) status
 * @param workflowDefinitionId ishlatilgan workflow definition
 * @param routingPrepared routing target topildimi
 * @param matchedRoutingRuleId tanlangan routing rule (nullable)
 * @param targetTopicBindingId tanlangan topic binding (nullable)
 * @param targetChatBindingId topic binding'ning chat binding'i (nullable)
 * @param targetTopicId Telegram topic ID — delivery target (nullable)
 * @param priorityCode Phase 195 — yaratilgan work item'ning ustuvorlik kodi
 *                     (nullable). Faqat intake create vaqtida set qilinsa
 *                     to'ldiriladi; aks holda JSON'da o'tkazib yuboriladi
 *                     ({@link JsonInclude.Include#NON_NULL}).
 * @param severityCode Phase 195 — yaratilgan work item'ning jiddiylik kodi
 *                     (nullable, NON_NULL).
 * @param currentOwnerUserId Phase 195 — yaratilgan work item'ning egasi
 *                           (nullable, NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItemIntakeResponse(
        UUID tenantId,
        UUID workItemId,
        String workItemCode,
        String typeCode,
        String title,
        String currentStatusCode,
        UUID workflowDefinitionId,
        boolean routingPrepared,
        UUID matchedRoutingRuleId,
        UUID targetTopicBindingId,
        UUID targetChatBindingId,
        Long targetTopicId,
        String priorityCode,
        String severityCode,
        UUID currentOwnerUserId) {}
