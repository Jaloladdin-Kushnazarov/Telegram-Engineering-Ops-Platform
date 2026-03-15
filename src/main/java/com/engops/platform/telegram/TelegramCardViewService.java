package com.engops.platform.telegram;

import com.engops.platform.intake.ProjectionPayload;
import org.springframework.stereotype.Service;

/**
 * Telegram modulining public application service'i — adapter-facing entry point.
 *
 * Future Telegram adapter (outbound message sender) shu servis orqali ishlaydi:
 * 1. ProjectionPayload qabul qiladi (intake module'ning public API'si)
 * 2. TelegramRenderAssembler orqali render payload hosil qiladi
 * 3. TelegramActionAssembler orqali action'lar va card view hosil qiladi
 * 4. Tayyor TelegramCardView qaytaradi
 *
 * Bu servis orchestration qiladi — render va action assembler'lardagi
 * rule/policy/enrichment logikasini takrorlamaydi.
 *
 * Muhim:
 * - Repository access yo'q — pure orchestration
 * - Side effect yo'q
 * - Business rule yo'q — faqat assembler'larni ketma-ket chaqiradi
 * - Stateless — concurrent-safe
 * - intake module'dan faqat ProjectionPayload (public API) olinadi
 */
@Service
public class TelegramCardViewService {

    private final TelegramRenderAssembler renderAssembler;
    private final TelegramActionAssembler actionAssembler;

    public TelegramCardViewService(TelegramRenderAssembler renderAssembler,
                                    TelegramActionAssembler actionAssembler) {
        this.renderAssembler = renderAssembler;
        this.actionAssembler = actionAssembler;
    }

    /**
     * ProjectionPayload'dan tayyor Telegram card view hosil qiladi.
     *
     * Bu telegram module'ning yagona public entry point'i —
     * future adapter shu metodni chaqirib, tayyor card view oladi.
     *
     * @param projectionPayload adapter-neutral projection payload (intake module'dan)
     * @return tayyor Telegram card view (render payload + action'lar)
     * @throws IllegalArgumentException agar projectionPayload null bo'lsa
     */
    public TelegramCardView buildCardView(ProjectionPayload projectionPayload) {
        if (projectionPayload == null) {
            throw new IllegalArgumentException("ProjectionPayload null bo'lishi mumkin emas");
        }

        TelegramRenderPayload renderPayload = renderAssembler.assemble(projectionPayload);

        return actionAssembler.assemble(renderPayload);
    }
}
