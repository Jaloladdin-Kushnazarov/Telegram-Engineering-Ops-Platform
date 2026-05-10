package com.engops.platform.intake;

import com.engops.platform.telegram.TelegramCardDispatchService;
import com.engops.platform.telegram.TelegramCardView;
import com.engops.platform.telegram.TelegramCardViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Phase 164 — AFTER_COMMIT consumer of {@link TelegramCardDispatchRequested}.
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
 *       performs the existing render → dispatch → persist chain inside
 *       its own independent transaction.</li>
 * </ol>
 *
 * <p><strong>REQUIRES_NEW transaction (Phase 164 mini-fix):</strong> the
 * listener method is annotated
 * {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} so the
 * {@code telegram_delivery_attempt} insert performed inside
 * {@link TelegramCardDispatchService#dispatchAttempt} runs in a *new,
 * independent* transaction. Although the original business transaction has
 * already committed when AFTER_COMMIT callbacks fire, the originating
 * transaction's resources (connection, JDBC state) may still be bound to
 * the thread; without an explicit new transaction the listener's writes
 * could land in an ambiguous, completed-but-not-cleaned-up context. A
 * fresh REQUIRES_NEW scope removes that ambiguity and gives the delivery
 * attempt insert deterministic commit semantics that are fully decoupled
 * from the upstream business transaction.</p>
 *
 * <p><strong>Fail-soft logging:</strong> any {@link RuntimeException} thrown
 * by the dispatch chain is caught and logged with bounded metadata only —
 * {@code sourceFlow}, {@code tenantId}, {@code workItemId},
 * {@code targetStatusCode}, and {@code exceptionType} (simple class name).
 * {@code ex.getMessage()} is intentionally not logged here. This boundary
 * has no knowledge of the bot token; token-aware sanitization remains in
 * {@code HttpTelegramOutboundGateway} (Phase 158) and the intake/workflow
 * boundary log shape established by Phase 160 / Phase 161 / Phase 161
 * mini-fix is preserved. Because the {@code RuntimeException} is caught
 * inside the listener's own REQUIRES_NEW transaction, the delivery_attempt
 * row that {@code TelegramCardDispatchService} persists before re-throwing
 * (when a later step fails) commits independently — the listener's outer
 * scope sees a returned method, not a rolled-back transaction.</p>
 *
 * <p><strong>Defensive guards:</strong> the listener short-circuits if the
 * event or its target is null, or if the target is not delivery-ready.
 * Publishers are expected to never emit events with non-ready targets — this
 * guard exists only as a defense-in-depth check.</p>
 *
 * <p>No retry, no async thread pool, no scheduling, and no outbox: the
 * listener stays single-attempt and synchronous-AFTER_COMMIT. Those concerns
 * are deferred to later phases on top of this boundary.</p>
 */
@Component
public class TelegramCardDispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramCardDispatchEventListener.class);

    private final ProjectionAssembler projectionAssembler;
    private final TelegramCardViewService telegramCardViewService;
    private final TelegramCardDispatchService telegramCardDispatchService;

    public TelegramCardDispatchEventListener(ProjectionAssembler projectionAssembler,
                                              TelegramCardViewService telegramCardViewService,
                                              TelegramCardDispatchService telegramCardDispatchService) {
        this.projectionAssembler = projectionAssembler;
        this.telegramCardViewService = telegramCardViewService;
        this.telegramCardDispatchService = telegramCardDispatchService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            telegramCardDispatchService.dispatchAttempt(cardView);
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
