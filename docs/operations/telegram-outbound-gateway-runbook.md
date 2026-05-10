# Telegram Outbound Gateway Runbook

Operator-facing runbook for safely enabling, disabling, verifying, and
troubleshooting Telegram outbound delivery on the Telegram Engineering
Operations Platform after Phases 158 / 160 / 161.

> Audience: deployment / SRE operators. Not a developer guide.

---

## 1. Purpose

The platform delivers operational cards (work item summaries) to Telegram
chats and topics as a side effect of two business flows:

- **Intake** — when a new work item is successfully created and the tenant's
  routing configuration resolves to a prepared delivery target.
- **Workflow transition** — when an existing work item's status is
  successfully transitioned (with audit + transition history written) and
  the routing configuration resolves to a prepared delivery target.

**Source of truth:** backend + PostgreSQL. Telegram is a *projection /
interaction surface* only. A failed Telegram delivery never rolls back a
work item create or a workflow transition; the business mutation has
already been committed when delivery is attempted (Phase 160 / Phase 161
fail-soft contract).

This runbook explains how to:

- enable the real Telegram Bot API gateway in production / demo,
- keep the stub fallback for local development,
- verify that delivery is working,
- diagnose common failures,
- understand what is intentionally **not** implemented yet.

---

## 2. Activation contract

The platform ships with two implementations of the same outbound port:

| Bean                              | When loaded                                                  | What it does                                               |
|-----------------------------------|--------------------------------------------------------------|------------------------------------------------------------|
| `HttpTelegramOutboundGateway`     | `app.telegram.bot-token` is **non-blank**                    | Real Telegram Bot API call (`POST /bot{token}/sendMessage`) |
| `StubTelegramOutboundGateway`     | `app.telegram.bot-token` is **missing / empty / whitespace** | Returns a controlled `FAILED` outcome — never throws        |

These two beans are **mutually exclusive** by `@ConditionalOnExpression`.
Exactly one is present in the application context at a time. There is no
runtime toggle and no admin endpoint to flip modes — activation is
entirely property-driven.

```
'${app.telegram.bot-token:}'.trim().length() > 0   →  HttpTelegramOutboundGateway
'${app.telegram.bot-token:}'.trim().length() == 0  →  StubTelegramOutboundGateway
```

**Important security rule:** the bot token must come from the environment
or a secret manager. It must **never** be committed into
`application.properties`, `application-local.properties`,
`application-prod.properties`, README, screenshots, issue tracker
comments, chat threads, or test fixtures.

---

## 3. Configuration properties

All properties live under the `app.telegram` prefix and are bound to
`TelegramProperties`. They are only read when real-mode activation is in
effect (i.e. when `HttpTelegramOutboundGateway` is the active bean).

| Property                          | Env var (recommended)        | Default                       | Notes                                                                 |
|-----------------------------------|------------------------------|-------------------------------|-----------------------------------------------------------------------|
| `app.telegram.bot-token`          | `TELEGRAM_BOT_TOKEN`         | *(none)*                      | Secret. Env / secret-manager only. Drives real-vs-stub activation.    |
| `app.telegram.api-base-url`       | `TELEGRAM_API_BASE_URL`      | `https://api.telegram.org`    | Override only for staging / mock servers.                             |
| `app.telegram.connect-timeout-ms` | `TELEGRAM_CONNECT_TIMEOUT_MS`| `5000`                        | TCP connect timeout in milliseconds.                                  |
| `app.telegram.read-timeout-ms`    | `TELEGRAM_READ_TIMEOUT_MS`   | `10000`                       | Response read timeout in milliseconds.                                |

Spring relaxed binding maps the recommended env vars to the property
names automatically (e.g. `TELEGRAM_BOT_TOKEN` → `app.telegram.bot-token`).

---

## 4. Safe usage by environment

### Local development (default)

- Do **not** set `TELEGRAM_BOT_TOKEN`.
- The application loads `StubTelegramOutboundGateway`.
- Every dispatch attempt produces a controlled `FAILED` outcome with
  `failure_code = TELEGRAM_GATEWAY_NOT_IMPLEMENTED`. The application boots
  and the rest of the platform behaves normally.
- This means observability endpoints will show *attempts*, not *deliveries*
  — which is correct for local.

### Demo / staging / production

- Provide `TELEGRAM_BOT_TOKEN` via the deployment platform's secret store
  (Kubernetes Secret, AWS Secrets Manager, GCP Secret Manager, systemd
  EnvironmentFile with `0600` permissions, etc.).
- Restart the application; activation only happens at startup.
- Optionally override the timeouts and base URL for environment-specific
  network constraints.

### Disabling real outbound

- Unset / remove `TELEGRAM_BOT_TOKEN` from the environment.
- Restart the application. The stub gateway will load again and real
  Telegram traffic will stop.
- There is no in-process kill switch — stopping outbound requires a
  restart.

### Token leakage response

- Treat any leaked token as compromised.
- Revoke / rotate the token via [@BotFather](https://t.me/BotFather)
  immediately.
- Update the secret-manager value and restart the application.
- Audit `git log -p` and CI artifacts to confirm no copy was committed.

---

## 5. Routing prerequisites

Telegram dispatch only runs when **routing is prepared**. Routing is
considered prepared when:

- the tenant has at least one matching `routing_rule` for the work item
  type, **and**
- that rule resolves to a target chat binding (`telegram_chat_binding`),
  **and**
- the target topic id is either explicitly absent (chat-only delivery) or
  resolved through topic binding configuration.

If any of these are missing, the application logs nothing extraordinary,
no Telegram call is made, and no `telegram_delivery_attempt` row is
written. The intake / transition still succeeds.

This is intentional fail-fast: it prevents repeated invalid sends when a
tenant is mid-configuration.

---

## 6. Runtime flow

### 6.1 Intake flow (Phase 164: AFTER_COMMIT dispatch)

```
HTTP POST  /api/intake/work-items
  → IntakeController
  → IntakeApplicationService.submit(IntakeCommand)              // @Transactional
        ├─ validate command
        ├─ authorize actor (WORK_ITEM_CREATE)
        ├─ resolve workflow definition + initial status
        ├─ RoutingDecisionService.resolve(tenantId, typeCode)        // fail-fast
        ├─ WorkItemCommandService.create(...)                        // mutation + audit
        └─ if routing.isPrepared():
              eventPublisher.publishEvent(
                  TelegramCardDispatchRequested(target, "INTAKE", null))   // queued in TX

  ── transaction commits ────────────────────────────────────────

  → TelegramCardDispatchEventListener.onTelegramCardDispatchRequested(event)
        @TransactionalEventListener(phase = AFTER_COMMIT)
        ProjectionAssembler.assemble(target)
        → TelegramCardViewService.buildCardView(payload)
        → TelegramCardDispatchService.dispatchAttempt(view)
              → TelegramMessageRenderer.render
              → TelegramDeliveryCommandAssembler.assembleSend
              → TelegramOutboundDispatchService.dispatch(command)
                    → TelegramOutboundGateway.execute(request)   // Http or Stub
              → JpaTelegramDeliveryAttemptPersistence.save(attempt)
        catch RuntimeException → bounded log, swallow
```

### 6.2 Workflow transition flow (Phase 164: AFTER_COMMIT dispatch)

```
HTTP POST  /api/workflow/work-items/{id}/transitions
  → WorkflowTransitionController
  → WorkflowTransitionService.transition(...)                    // @Transactional
        ├─ load work item (tenant-safe)
        ├─ authorize actor (WORK_ITEM_TRANSITION)
        ├─ validate transition rule
        ├─ mutate status / mark resolved / mark reopened
        ├─ WorkItemCommandService.save(workItem)
        ├─ WorkItemTransitionRepository.save(history)
        ├─ AuditService.recordEvent(...)                              // STATUS_TRANSITION
        └─ try:
              RoutingDecisionService.resolve(tenantId, typeCode)      // re-resolved
              if routing.isPrepared():
                    PreparedDeliveryTarget(... newStatusCode ...)
                    eventPublisher.publishEvent(
                        TelegramCardDispatchRequested(target,
                            "WORKFLOW_TRANSITION", targetStatusCode))    // queued in TX
           catch RuntimeException → bounded log, swallow

  ── transaction commits ────────────────────────────────────────

  → TelegramCardDispatchEventListener.onTelegramCardDispatchRequested(event)
        (same AFTER_COMMIT chain as intake)
```

If the surrounding transaction rolls back, no event is delivered and the
listener never runs — there is no Telegram message and no
`telegram_delivery_attempt` row. This is by design.

**Append-only.** Each transition produces a *new* `sendMessage` attempt;
`editMessageText` is **not** used because the Telegram `message_id` is
not stored on `WorkItem`. Existing Telegram messages are not edited or
deleted by the platform.

---

## 7. Observability and verification

### 7.1 Append-only attempt history

Every dispatch attempt — including stub-mode failed attempts — is
persisted in `telegram_delivery_attempt`. The table is append-only; rows
are never updated or deleted by application code.

Each row carries: `attempt_id`, `attempted_at`, `tenant_id`,
`work_item_id`, `operation`, `target_chat_binding_id`, `target_topic_id`,
`delivery_outcome`, `external_message_id` (when delivered),
`failure_code` and `failure_reason` (when not delivered).

### 7.2 Admin observability HTTP endpoints

All endpoints are `GET` only and live under
`/api/admin/delivery-observability`. They require an authenticated
actor with the appropriate observability permission.

| Endpoint                                     | Purpose                                                 |
|----------------------------------------------|---------------------------------------------------------|
| `GET /summary`                               | Tenant-scoped compact summary list                      |
| `GET /summary/by-status?statusCode=...`      | Summary list filtered by current status                 |
| `GET /summary/by-owner?ownerUserId=...`      | Summary list filtered by owner                          |
| `GET /details?workItemCode=...`              | Detailed view by work item code (e.g. `BUG-1`)          |
| `GET /details/by-id?workItemId=...`          | Detailed view by work item UUID                         |
| `GET /details/by-status?statusCode=...`      | Detailed list filtered by current status                |
| `GET /details/by-owner?ownerUserId=...`      | Detailed list filtered by owner                         |

The detailed views include the latest delivery metrics snapshot and the
most recent attempt history rows, so operators can see what was sent
without opening the database.

### 7.3 Outcome semantics

| `delivery_outcome` | Meaning                                                                                                                            |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `DELIVERED`        | Telegram accepted the send. `external_message_id` is populated when the response provided one.                                     |
| `REJECTED`         | Permanent-style failure: invalid request / invalid chat binding / Telegram 4xx (other than 429). `failure_code` and `failure_reason` are populated. |
| `FAILED`           | Transient or unknown failure: rate limit, network error, server error, timeout, unexpected exception. `failure_code` and `failure_reason` are populated. |

### 7.4 Failure code reference

| `failure_code`                       | Source / meaning                                                                                  |
|--------------------------------------|---------------------------------------------------------------------------------------------------|
| `TELEGRAM_GATEWAY_NOT_IMPLEMENTED`   | Stub fallback ran. Means real token is not active in this environment.                            |
| `INVALID_REQUEST`                    | Telegram client error (4xx other than 429), `ok=false` response, or chat binding lookup failed.   |
| `RATE_LIMIT`                         | Telegram HTTP 429. Retry is **not** implemented yet — the row records the throttling event only.  |
| `NETWORK_ERROR`                      | Connect / read timeout, IO error, or Telegram HTTP 5xx.                                           |
| `UNKNOWN_ERROR`                      | Unexpected exception during request build, transport, or response parsing.                        |
| `DISPATCH_NOT_SUPPORTED`             | Defensive — only produced if a caller invokes the deprecated `gateway.dispatch(command)` path. Production code does not use this path. |

### 7.5 Verifying a delivery end-to-end

1. Create a work item via the intake API for a tenant whose routing is
   fully configured.
2. Confirm the new card appears in the target Telegram chat / topic.
3. Open
   `GET /api/admin/delivery-observability/details?tenantId=...&workItemCode=...`
   and confirm `latestMetrics.deliveryOutcome = DELIVERED` and at least
   one entry under `recentAttempts`.
4. Transition the work item via the workflow API.
5. Confirm a *new* card appears in Telegram (not an edit of the previous
   one) and a *new* attempt row appears in the details view.

---

## 8. Security notes

- The bot token is a high-value secret. Treat it like a database password.
- Token comes only from `TELEGRAM_BOT_TOKEN` (or the secret manager bound
  to it). It is not committed to git.
- `HttpTelegramOutboundGateway` sanitizes the bot-token substring out of
  any error message it surfaces or persists, defending against accidental
  leakage through `failure_reason`.
- Intake (`IntakeApplicationService.dispatchTelegramCardSafely`) and
  workflow (`WorkflowTransitionService.dispatchTelegramCardSafely`)
  fail-soft logging is deliberately **bounded**: only `tenantId`,
  `workItemId`, optionally `targetStatusCode`, and `exceptionType`
  (simple class name) are logged. `ex.getMessage()` is **not** logged at
  the boundary, which prevents any token / secret substring from leaking
  upward through layers that have no business knowing about the token.
- Do **not** expose the token in `actuator/info`, `actuator/env`,
  custom health indicators, or build / deploy logs. The standard
  Boot env-sanitization rules apply; do not override them.
- Do **not** paste the token into issue trackers, chat threads, support
  tickets, screenshots, or pull request descriptions.

---

## 9. Current limitations / out of scope

These are deliberate omissions in the current implementation. They are
recognized gaps and are scheduled for later phases.

- **No retry / backoff.** `RATE_LIMIT` and `NETWORK_ERROR` outcomes are
  recorded but not retried. Operators must currently treat them as
  signals; there is no automatic recovery.
- **No inbound webhook / `callback_query` handling.** The platform does
  not consume Telegram updates. Inline keyboards may be rendered on
  outbound cards but their button presses are not received or processed
  by the backend yet.
- **No `parse_mode` / Markdown / HTML rendering.** All outbound text is
  plain text. Special characters are sent as-is.
- **No `editMessageText`.** Each transition sends a *new* message
  because the Telegram `message_id` is not stored on `WorkItem`. Old
  cards are not updated, deleted, or superseded by the bot.
- **Synchronous AFTER_COMMIT side effect** *(Phase 164)*. Intake and
  workflow services publish a `TelegramCardDispatchRequested` application
  event inside their `@Transactional` scope; a
  `@TransactionalEventListener(phase = AFTER_COMMIT)` listener performs
  the render → outbound → delivery_attempt persistence chain on the same
  thread, *after* the surrounding transaction commits. The listener
  method itself is annotated
  `@Transactional(propagation = REQUIRES_NEW)` so the
  `telegram_delivery_attempt` insert runs in an independent transaction
  decoupled from the originating business transaction (Phase 164
  mini-fix). Telegram HTTP I/O no longer runs inside the business write
  transaction, and a rolled-back business transaction never produces a
  Telegram message or a `telegram_delivery_attempt` row. There is still
  no async worker pool, no retry, and no outbox.
- **Pre-persist exception observability gap.** A `RuntimeException`
  thrown *before* `JpaTelegramDeliveryAttemptPersistence.save(attempt)`
  succeeds will not produce a `telegram_delivery_attempt` row. The
  fail-soft boundary in the AFTER_COMMIT listener logs only bounded
  metadata (`sourceFlow`, `tenantId`, `workItemId`, `targetStatusCode`,
  `exceptionType`), so diagnosis of these cases relies on the structured
  log entry rather than on the attempt history.

---

## 10. Troubleshooting checklist

### 10.1 No Telegram messages arrive at all

Work through these in order:

1. **Token missing / wrong mode.** Confirm `TELEGRAM_BOT_TOKEN` is set
   in the running environment and the application has been restarted
   since it was set. Check whether attempts show
   `failure_code = TELEGRAM_GATEWAY_NOT_IMPLEMENTED` — that means stub
   mode is active.
2. **Routing not prepared.** Confirm the tenant has a matching routing
   rule, a chat binding, and (if topics are used) a topic binding. With
   no prepared routing, *no attempt row is written*.
3. **Bot not in target chat.** Add the bot to the target chat / channel
   / supergroup and grant it permission to post. For topic-enabled
   supergroups, the bot also needs access to the specific topic.
4. **Wrong chat or topic id.** Validate `telegram_chat_binding.chat_id`
   and the resolved topic id. Telegram's public id format for groups /
   channels is a negative integer; bot-private chats use the positive
   user id of the chat partner.
5. **Application not restarted after env change.** Activation is read
   only at startup. Confirm the running process actually inherits the
   new env value.

### 10.2 Attempts show `TELEGRAM_GATEWAY_NOT_IMPLEMENTED`

- Stub fallback is running. Real token is not active in this
  environment. See section 4 ("Demo / staging / production").

### 10.3 Attempts show `INVALID_REQUEST`

- Bad chat id, bad topic id, bot not a member of the chat, or a
  malformed payload. The Telegram side has rejected the call.
- Inspect `failure_reason` for the Telegram-supplied description (with
  the bot token sanitized out).

### 10.4 Attempts show `RATE_LIMIT`

- The bot has exceeded Telegram's send-rate limits.
- Retry is **not** implemented yet (see section 9). Reduce burst rate
  upstream until automated retry / backoff lands in a future phase.

### 10.5 Attempts show `NETWORK_ERROR`

- Connect / read timeout, DNS failure, or Telegram HTTP 5xx.
- Validate egress connectivity from the application host to
  `api.telegram.org` (or the configured `app.telegram.api-base-url`).
- Consider raising `app.telegram.connect-timeout-ms` and
  `app.telegram.read-timeout-ms` if the environment has high latency to
  Telegram.

### 10.6 Attempts show `UNKNOWN_ERROR`

- An unexpected runtime exception was caught by the gateway. Check the
  application log for the warning emitted by
  `HttpTelegramOutboundGateway` (token-sanitized).

### 10.7 Work item / transition succeeded but no attempt row exists

- Routing was not prepared (expected — see 10.1.2), **or**
- A `RuntimeException` was thrown before the attempt was persisted
  (rare; see section 9).
- In the second case the boundary logs at `WARN` with bounded metadata
  (`tenantId`, `workItemId`, optionally `targetStatusCode`,
  `exceptionType`). Search the application log for
  `Telegram card dispatch failed (fail-soft)`.

---

## 11. Operator checklist

### 11.1 Before enabling real outbound

- [ ] Tenant + first admin bootstrapped (see
      [First-Admin Bootstrap Runbook](first-admin-bootstrap-runbook.md)).
- [ ] At least one `WorkflowDefinition` is active for the work item types
      you intend to deliver.
- [ ] `telegram_chat_binding` rows exist for the tenant.
- [ ] `routing_rule` rows resolve to a chat binding (and optional topic
      binding) for the target work item type.
- [ ] Bot has been added to each target chat with permission to post.
      For supergroups with topics, the bot has access to the topic.
- [ ] `TELEGRAM_BOT_TOKEN` is provisioned via the secret manager / env
      and is **not** present in any committed file.

### 11.2 After enabling real outbound

- [ ] Restart the application.
- [ ] Submit a test intake for the configured tenant.
- [ ] Confirm a card appears in the target chat / topic.
- [ ] Transition the test work item.
- [ ] Confirm a new card (not an edit) appears.
- [ ] Open
      `GET /api/admin/delivery-observability/details?tenantId=...&workItemCode=...`
      and confirm `latestMetrics.deliveryOutcome = DELIVERED`.
- [ ] Tail the application log for the test window and confirm no token
      substring appears.

### 11.3 Disabling

- [ ] Unset `TELEGRAM_BOT_TOKEN` in the deployment environment.
- [ ] Restart the application.
- [ ] Confirm subsequent attempts record
      `failure_code = TELEGRAM_GATEWAY_NOT_IMPLEMENTED` (stub mode is
      active again).

---
