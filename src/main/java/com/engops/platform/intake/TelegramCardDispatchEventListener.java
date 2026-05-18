package com.engops.platform.intake;

import com.engops.platform.telegram.TelegramCardRefreshDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import com.engops.platform.telegram.TelegramCardViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Phase 164 + Phase 168 — AFTER_COMMIT consumer of
 * {@link TelegramCardDispatchRequested}.
 *
 * <p>This listener moves Telegram dispatch out of the business write
 * transaction. It runs only after the surrounding {@code @Transactional}
 * scope commits successfully; if the transaction rolls back, the listener
 * is never invoked and no Telegram message is sent and no
 * {@code telegram_delivery_attempt} row is written. Telegram HTTP I/O is
 * therefore no longer inside the original intake / workflow business
 * transaction.</p>
 *
 * <p><strong>Lifecycle:</strong></p>
 * <ol>
 *   <li>Intake / workflow service mutates DB state inside a transaction.</li>
 *   <li>If routing is prepared, the service publishes a
 *       {@link TelegramCardDispatchRequested} event with a fully resolved
 *       {@link PreparedDeliveryTarget}.</li>
 *   <li>Spring queues the event until the business transaction commits.</li>
 *   <li>The business transaction commits (or rolls back) and is fully
 *       finalized.</li>
 *   <li>This listener runs (synchronously, in the committing thread) and
 *       delegates to {@link TelegramCardRefreshDispatchService} which
 *       implements the Phase 179 edit-first / send-as-fallback policy:
 *       first tries {@code editMessageText} via
 *       {@link com.engops.platform.telegram.TelegramCardRefreshService};
 *       on edit success or benign "message is not modified" stops; on
 *       any other outcome falls back to the existing
 *       {@link com.engops.platform.telegram.TelegramCardDispatchRetryingService#dispatchWithRetry(TelegramCardView)}
 *       which performs render → dispatch → persist with bounded
 *       retry/backoff on transient failures.</li>
 * </ol>
 *
 * <p><strong>Transaction boundary (Phase 168):</strong> the listener method
 * itself is no longer annotated {@code @Transactional}. Each
 * {@code telegram_delivery_attempt} insert is wrapped in its own
 * {@code @Transactional(propagation = REQUIRES_NEW)} on
 * {@link com.engops.platform.telegram.JpaTelegramDeliveryAttemptPersistence#save}.
 * This keeps the Phase 164 mini-fix invariant — delivery_attempt persistence
 * runs in an independent transaction decoupled from the originating
 * business transaction — while making the boundary fine-grained enough that
 * Telegram HTTP I/O and retry backoff sleeps are NOT covered by an open
 * transaction. Hikari connection occupancy stays at near-zero between
 * retry attempts.</p>
 *
 * <p><strong>Fail-soft logging:</strong> any {@link RuntimeException} thrown
 * by the retrying dispatch chain is caught and logged with bounded metadata
 * only — {@code sourceFlow}, {@code tenantId}, {@code workItemId},
 * {@code targetStatusCode}, and {@code exceptionType} (simple class name).
 * {@code ex.getMessage()} is intentionally not logged here. This boundary
 * has no knowledge of the bot token; token-aware sanitization remains in
 * {@code HttpTelegramOutboundGateway} (Phase 158) and the intake/workflow
 * boundary log shape established by Phase 160 / Phase 161 / Phase 161
 * mini-fix is preserved.</p>
 *
 * <p><strong>Defensive guards:</strong> the listener short-circuits if the
 * event or its target is null, or if the target is not delivery-ready.
 * Publishers are expected to never emit events with non-ready targets — this
 * guard exists only as a defense-in-depth check.</p>
 *
 * <p><strong>Retry note:</strong> retry/backoff is bounded and synchronous
 * (Phase 168). It runs on the same AFTER_COMMIT thread, only for
 * {@code RATE_LIMIT} and {@code NETWORK_ERROR} outcomes; it deliberately
 * does NOT retry {@code UNKNOWN_ERROR} (stub-mode safety + duplicate-send
 * prevention) or {@code REJECTED}/{@code INVALID_REQUEST} (permanent).
 * No async pool, no scheduler, no outbox.</p>
 */
@Component
public class TelegramCardDispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramCardDispatchEventListener.class);

    private final ProjectionAssembler projectionAssembler;
    private final TelegramCardViewService telegramCardViewService;
    private final TelegramCardRefreshDispatchService telegramCardRefreshDispatchService;

    public TelegramCardDispatchEventListener(ProjectionAssembler projectionAssembler,
                                              TelegramCardViewService telegramCardViewService,
                                              TelegramCardRefreshDispatchService telegramCardRefreshDispatchService) {
        this.projectionAssembler = projectionAssembler;
        this.telegramCardViewService = telegramCardViewService;
        this.telegramCardRefreshDispatchService = telegramCardRefreshDispatchService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelegramCardDispatchRequested(TelegramCardDispatchRequested event) {
        if (event == null) {
            return;
        }
        PreparedDeliveryTarget target = event.target();
        if (target == null || !target.isDeliveryReady()) {
            return;
        }
        try {
            ProjectionPayload payload = projectionAssembler.assemble(target);
            TelegramCardView cardView = telegramCardViewService.buildCardView(payload);
            // Phase 179 — listener endi to'g'ridan-to'g'ri send retry'ga
            // delegate qilmaydi. Coordinator edit-first / send-as-fallback
            // qarorini qabul qiladi va kerak bo'lganda mavjud retry pipeline'ni
            // o'zi chaqiradi (fallback). Listener thin bo'lib qoladi va
            // AFTER_COMMIT + fail-soft invariantlari saqlanadi.
            telegramCardRefreshDispatchService.dispatch(cardView,
                    target.getTenantId(), target.getWorkItemId());
        } catch (RuntimeException ex) {
            log.warn("Telegram card dispatch failed (fail-soft) sourceFlow={} tenantId={} workItemId={} targetStatusCode={} exceptionType={}",
                    event.sourceFlow(),
                    target.getTenantId(),
                    target.getWorkItemId(),
                    event.targetStatusCode(),
                    ex.getClass().getSimpleName());
        }
    }
}
