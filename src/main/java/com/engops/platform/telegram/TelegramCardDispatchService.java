package com.engops.platform.telegram;

import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TelegramCardView'dan to'liq outbound delivery'gacha orchestration service.
 *
 * Bu servis telegram module ichidagi outbound pipeline'ning yagona entry point'i.
 * Ichki multi-step pipeline'ni yashiradi.
 *
 * Ikki public API:
 * - dispatch(cardView) -> TelegramDeliveryResult (business/application outcome)
 * - dispatchAttempt(cardView) -> TelegramDeliveryAttempt (observability/traceability)
 *
 * Orchestration flow (ikkala method uchun bir xil):
 * TelegramCardView
 *   -> TelegramMessageRenderer.render(cardView)
 *   -> TelegramDeliveryCommandAssembler.assembleSend(message)
 *   -> TelegramOutboundDispatchService.dispatch(command)
 *   -> TelegramDeliveryResult
 *
 * dispatchAttempt qo'shimcha ravishda TelegramDeliveryAttempt trace record yaratadi.
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
     * Business/application-level facade — TelegramDeliveryResult qaytaradi.
     *
     * @param cardView render payload + action'lar
     * @return application-level delivery natijasi
     * @throws IllegalArgumentException agar cardView null bo'lsa
     * @throws IllegalStateException agar collaborator null qaytarsa
     */
    public TelegramDeliveryResult dispatch(TelegramCardView cardView) {
        return executeOrchestration(cardView).result();
    }

    /**
     * TelegramCardView'ni render, assemble, dispatch qiladi va trace record yaratadi.
     *
     * Observability/traceability-level facade — TelegramDeliveryAttempt qaytaradi.
     * Ichki orchestration dispatch(...) bilan bir xil.
     *
     * @param cardView render payload + action'lar
     * @return delivery attempt trace record
     * @throws IllegalArgumentException agar cardView null bo'lsa
     * @throws IllegalStateException agar collaborator null qaytarsa
     */
    public TelegramDeliveryAttempt dispatchAttempt(TelegramCardView cardView) {
        DispatchOutcome outcome = executeOrchestration(cardView);
        return TelegramDeliveryAttempt.of(outcome.command(), outcome.result(), Instant.now());
    }

    /**
     * Ichki orchestration — duplication oldini oladi.
     * dispatch() va dispatchAttempt() ikkisi ham shu method'ni chaqiradi.
     */
    private DispatchOutcome executeOrchestration(TelegramCardView cardView) {
        if (cardView == null) {
            throw new IllegalArgumentException("TelegramCardView null bo'lishi mumkin emas");
        }

        TelegramMessage message = renderer.render(cardView);
        if (message == null) {
            throw new IllegalStateException("TelegramMessageRenderer null qaytardi");
        }

        TelegramDeliveryCommand command = commandAssembler.assembleSend(message);
        if (command == null) {
            throw new IllegalStateException("TelegramDeliveryCommandAssembler null qaytardi");
        }

        TelegramDeliveryResult result = outboundDispatchService.dispatch(command);
        if (result == null) {
            throw new IllegalStateException("TelegramOutboundDispatchService null qaytardi");
        }

        return new DispatchOutcome(command, result);
    }

    /**
     * Ichki orchestration natijasi — command va result juftligini tashiydi.
     * Faqat service ichida ishlatiladi — public API emas.
     */
    private record DispatchOutcome(TelegramDeliveryCommand command, TelegramDeliveryResult result) {}
}
