package com.engops.platform.telegram;

import java.util.UUID;

/**
 * Telegram card ustidagi bitta action — future inline keyboard button uchun contract.
 *
 * Bu DTO adapter uchun "qaysi action mavjud, uni ko'rsatish kerakmi" ma'lumotini beradi.
 * Hali real callback handling yo'q — faqat stable foundation.
 *
 * Structured callback foundation:
 * - workItemId: qaysi work item uchun action (authoritative identity)
 * - actionCode: qanday action (masalan "START_PROCESSING")
 *
 * Future handler shu ikki field orqali action'ni aniqlab, execute qiladi —
 * callbackData parse qilish shart emas.
 *
 * Transport field:
 * - callbackData: Telegram callback_data uchun serialized representation
 *   (formati: "workItemId:actionCode")
 *
 * Qo'shimcha field'lar:
 * - label: foydalanuvchiga ko'rinadigan matn
 * - targetStatusCode: maqsad status (display/confirmation uchun)
 * - enabled: action hozir ko'rsatilishi mumkinmi
 * - confirmationRequired: action uchun tasdiqlash kerakmi
 */
public class TelegramCardAction {

    // Structured callback foundation
    private final UUID workItemId;
    private final String actionCode;

    // Display
    private final String label;
    private final String targetStatusCode;
    private final boolean enabled;
    private final boolean confirmationRequired;

    // Transport (derived from structured fields)
    private final String callbackData;

    public TelegramCardAction(UUID workItemId, String actionCode, String label,
                               String targetStatusCode, boolean enabled,
                               boolean confirmationRequired, String callbackData) {
        this.workItemId = workItemId;
        this.actionCode = actionCode;
        this.label = label;
        this.targetStatusCode = targetStatusCode;
        this.enabled = enabled;
        this.confirmationRequired = confirmationRequired;
        this.callbackData = callbackData;
    }

    public UUID getWorkItemId() { return workItemId; }
    public String getActionCode() { return actionCode; }
    public String getLabel() { return label; }
    public String getTargetStatusCode() { return targetStatusCode; }
    public boolean isEnabled() { return enabled; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public String getCallbackData() { return callbackData; }
}
