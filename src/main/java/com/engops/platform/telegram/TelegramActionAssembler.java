package com.engops.platform.telegram;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Telegram card action'larni assemble qiluvchi pure policy.
 *
 * Bu assembler TelegramRenderPayload asosida qaysi action'lar
 * ko'rinishi kerakligini aniqlaydi.
 *
 * Hozirgi holatda MVP bug flow asosida statik policy:
 * - BUGS → Start Processing
 * - PROCESSING → Send to Testing
 * - TESTING → Mark Fixed, Return to Bugs
 * - FIXED → Reopen
 *
 * Bu MVP-only policy — keyingi phase'larda WorkflowDefinition'dan
 * dynamic transition rule'lar asosida almashtiriladi.
 *
 * INCIDENT va TASK turlari uchun hali MVP flow aniqlanmagan —
 * bo'sh action list qaytariladi (valid holat).
 *
 * Muhim:
 * - Repository access yo'q — pure mapping/policy
 * - Side effect yo'q
 * - Business rule duplication yo'q — bu faqat visibility contract,
 *   actual transition validation WorkflowTransitionService'da qoladi
 * - Action execution bu phase'da yo'q
 */
@Component
public class TelegramActionAssembler {

    /**
     * TelegramRenderPayload va TelegramCardAction list'dan TelegramCardView hosil qiladi.
     *
     * @param renderPayload render-ready payload
     * @return to'liq card view (render + actions)
     * @throws IllegalArgumentException agar renderPayload null bo'lsa
     */
    public TelegramCardView assemble(TelegramRenderPayload renderPayload) {
        if (renderPayload == null) {
            throw new IllegalArgumentException("TelegramRenderPayload null bo'lishi mumkin emas");
        }

        List<TelegramCardAction> actions = resolveActions(renderPayload);

        return new TelegramCardView(renderPayload, actions);
    }

    /**
     * Work item type va current status asosida mavjud action'larni aniqlaydi.
     * Hozircha faqat BUG type uchun MVP flow asosida.
     */
    private List<TelegramCardAction> resolveActions(TelegramRenderPayload payload) {
        if (!"BUG".equals(payload.getWorkItemType())) {
            return List.of();
        }

        return resolveBugActions(payload.getCurrentStatusCode(),
                payload.getWorkItemId());
    }

    /**
     * BUG type uchun MVP action'larni aniqlaydi.
     *
     * CLAUDE.md MVP bug flow:
     * BUGS → PROCESSING → TESTING → FIXED
     * TESTING → BUGS (return)
     * FIXED → BUGS (reopen)
     */
    private List<TelegramCardAction> resolveBugActions(String currentStatusCode,
                                                        UUID workItemId) {
        return switch (currentStatusCode) {
            case "BUGS" -> List.of(
                    buildAction("START_PROCESSING", "Start Processing",
                            "PROCESSING", true, false, workItemId));

            case "PROCESSING" -> List.of(
                    buildAction("SEND_TO_TESTING", "Send to Testing",
                            "TESTING", true, false, workItemId));

            case "TESTING" -> List.of(
                    buildAction("MARK_FIXED", "Mark Fixed",
                            "FIXED", true, false, workItemId),
                    buildAction("RETURN_TO_BUGS", "Return to Bugs",
                            "BUGS", true, true, workItemId));

            case "FIXED" -> List.of(
                    buildAction("REOPEN", "Reopen",
                            "BUGS", true, true, workItemId));

            default -> List.of();
        };
    }

    private TelegramCardAction buildAction(String actionCode, String label,
                                            String targetStatusCode,
                                            boolean enabled, boolean confirmationRequired,
                                            UUID workItemId) {
        String callbackData = workItemId + ":" + actionCode;
        return new TelegramCardAction(workItemId, actionCode, label, targetStatusCode,
                enabled, confirmationRequired, callbackData);
    }
}
