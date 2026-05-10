package com.engops.platform.intake;

/**
 * Phase 164 — internal application event published when a business flow
 * (intake or workflow transition) wants a Telegram card to be dispatched.
 *
 * <p>Consumed by {@link TelegramCardDispatchEventListener} via
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so that Telegram
 * HTTP I/O happens after the surrounding business transaction commits — never
 * inside it. This eliminates the send-then-rollback divergence that the
 * pre-Phase-164 synchronous-in-transaction dispatch could produce.</p>
 *
 * <p>The event is intentionally minimal: it carries an immutable
 * {@link PreparedDeliveryTarget} snapshot (already computed by the publisher
 * with the post-mutation work item identity, status, and resolved routing
 * target) plus two log-only fields. The listener never needs to re-read the
 * work item from the database.</p>
 *
 * @param target              immutable delivery snapshot (must be non-null
 *                            with {@code deliveryReady=true}; publishers
 *                            short-circuit otherwise and never publish)
 * @param sourceFlow          {@code "INTAKE"} or {@code "WORKFLOW_TRANSITION"};
 *                            used for fail-soft log attribution only
 * @param targetStatusCode    workflow transition target status code; non-null
 *                            for {@code WORKFLOW_TRANSITION}, {@code null}
 *                            for {@code INTAKE}; log-only
 */
public record TelegramCardDispatchRequested(
        PreparedDeliveryTarget target,
        String sourceFlow,
        String targetStatusCode) {

    public static final String SOURCE_INTAKE = "INTAKE";
    public static final String SOURCE_WORKFLOW_TRANSITION = "WORKFLOW_TRANSITION";
}
