package com.engops.platform.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WorkItem details endpoint'ining HTTP response DTO'si.
 *
 * Ichki WorkItem va WorkItemUpdate modellaridan
 * tashqi HTTP kontraktiga aniq mapping.
 *
 * Ichki entity'larni to'g'ridan-to'g'ri expose qilmaydi —
 * bu record alohida response contract vazifasini bajaradi.
 *
 * @param workItemId work item identifikatori
 * @param workItemCode work item kodi (masalan "BUG-1")
 * @param title work item sarlavhasi
 * @param typeCode work item turi (BUG, INCIDENT, TASK)
 * @param currentStatusCode hozirgi holat kodi
 * @param priorityCode ustuvorlik kodi
 * @param severityCode jiddiylik kodi
 * @param environmentCode muhit kodi
 * @param sourceService manba servis nomi
 * @param correlationKey korrelyatsiya kaliti
 * @param currentOwnerUserId hozirgi egasi
 * @param openedAt ochilgan vaqt
 * @param lastTransitionAt oxirgi holat o'tkazish vaqti
 * @param resolvedAt yechilgan vaqt
 * @param reopenedCount qayta ochilishlar soni
 * @param archived arxivlangan holat
 * @param updates yangilanishlar ro'yxati (createdAt ASC tartibda)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItemDetailsResponse(
        UUID workItemId,
        String workItemCode,
        String title,
        String typeCode,
        String currentStatusCode,
        String priorityCode,
        String severityCode,
        String environmentCode,
        String sourceService,
        String correlationKey,
        UUID currentOwnerUserId,
        Instant openedAt,
        Instant lastTransitionAt,
        Instant resolvedAt,
        int reopenedCount,
        boolean archived,
        List<UpdateItemResponse> updates) {

    /**
     * Bitta work item update nested response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateItemResponse(
            UUID updateId,
            UUID tenantId,
            UUID workItemId,
            UUID authorUserId,
            String updateTypeCode,
            String body,
            String visibilityCode,
            Instant createdAt) {}
}
