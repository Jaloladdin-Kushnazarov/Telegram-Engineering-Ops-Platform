package com.engops.platform.telegram;

import org.springframework.stereotype.Service;

/**
 * TelegramCardView'dan to'liq outbound delivery'gacha orchestration service.
 *
 * Bu servis telegram module ichidagi outbound pipeline'ning yagona entry point'i.
 * Ichki multi-step pipeline'ni yashiradi — caller faqat TelegramCardView beradi
 * va TelegramDeliveryResult oladi.
 *
 * Orchestration flow:
 * TelegramCardView
 *   -> TelegramMessageRenderer.render(cardView)
 *   -> TelegramDeliveryCommandAssembler.assembleSend(message)
 *   -> TelegramOutboundDispatchService.dispatch(command)
 *   -> TelegramDeliveryResult
 *
 * Muhim:
 * - Pure orchestration — rendering, assembly, dispatch logikasi bu yerda yo'q
 * - Repository access yo'q
 * - HTTP yo'q
 * - Business rule yo'q — faqat null guard
 * - Retry yo'q
 * - Stateless — concurrent-safe
 */
@Service
public class TelegramCardDispatchService {

    private final TelegramMessageRenderer renderer;
    private final TelegramDeliveryCommandAssembler commandAssembler;
    private final TelegramOutboundDispatchService outboundDispatchService;

    public TelegramCardDispatchService(TelegramMessageRenderer renderer,
                                       TelegramDeliveryCommandAssembler commandAssembler,
                                       TelegramOutboundDispatchService outboundDispatchService) {
        this.renderer = renderer;
        this.commandAssembler = commandAssembler;
        this.outboundDispatchService = outboundDispatchService;
    }

    /**
     * TelegramCardView'ni render, assemble va dispatch qiladi.
     *
     * @param cardView render payload + action'lar
     * @return application-level delivery natijasi
     * @throws IllegalArgumentException agar cardView null bo'lsa
     */
    public TelegramDeliveryResult dispatch(TelegramCardView cardView) {
        if (cardView == null) {
            throw new IllegalArgumentException("TelegramCardView null bo'lishi mumkin emas");
        }

        TelegramMessage message = renderer.render(cardView);

        TelegramDeliveryCommand command = commandAssembler.assembleSend(message);

        return outboundDispatchService.dispatch(command);
    }
}
