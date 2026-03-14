package com.engops.platform.telegram;

import com.engops.platform.intake.ProjectionPayload;
import org.springframework.stereotype.Component;

/**
 * ProjectionPayload → TelegramRenderPayload konvertatsiya qatlami.
 *
 * Bu assembler adapter-neutral ProjectionPayload'ni Telegram-specific
 * render-ready contract'ga aylantiradi.
 *
 * Enrichment:
 * - headerLine: "Bug | BUG-1" — compact card header
 * - statusLine: "Status: BUGS" — render-ready status display
 *
 * Bu enrichment plain text building block'lar — markdown/HTML yo'q.
 * Telegram adapter keyinchalik o'z formatini qo'shadi.
 *
 * Muhim:
 * - Repository access yo'q — pure mapping
 * - Business rule duplication yo'q
 * - ProjectionPayload'dan barcha kerakli ma'lumot allaqachon mavjud
 * - intake module'ning public API'si (ProjectionPayload) orqali ishlaydi
 */
@Component
public class TelegramRenderAssembler {

    /**
     * ProjectionPayload'dan Telegram render-ready payload hosil qiladi.
     *
     * @param payload adapter-neutral projection payload
     * @return Telegram-specific render-ready contract
     * @throws IllegalArgumentException agar payload null bo'lsa
     */
    public TelegramRenderPayload assemble(ProjectionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("ProjectionPayload null bo'lishi mumkin emas");
        }

        String headerLine = buildHeaderLine(payload.getDisplayTypeLabel(), payload.getWorkItemCode());
        String statusLine = buildStatusLine(payload.getCurrentStatusCode());

        return new TelegramRenderPayload(
                payload.getTenantId(),
                payload.getWorkItemId(),
                payload.getWorkItemCode(),
                payload.getWorkItemType(),
                payload.getTitle(),
                payload.getCurrentStatusCode(),
                payload.getDisplayTitle(),
                payload.getDisplayTypeLabel(),
                headerLine,
                statusLine,
                payload.isDeliveryReady(),
                payload.getTargetChatBindingId(),
                payload.getTargetTopicId());
    }

    /**
     * "Bug | BUG-1" formatida compact card header hosil qiladi.
     */
    private String buildHeaderLine(String displayTypeLabel, String workItemCode) {
        return displayTypeLabel + " | " + workItemCode;
    }

    /**
     * "Status: BUGS" formatida render-ready status display hosil qiladi.
     */
    private String buildStatusLine(String currentStatusCode) {
        return "Status: " + currentStatusCode;
    }
}
