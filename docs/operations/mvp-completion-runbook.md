# MVP Completion Runbook

> **Phase 185.** This is the short operator entry point for going from
> "repo cloned" to "MVP demo passed" without reading every deep runbook
> first. The three deep runbooks remain authoritative; this document
> sequences them and exposes a single consolidated checklist.

---

## 1. Purpose and audience

**Purpose.** Give a single operator-facing page that:

1. Orients a fresh operator on what the platform can already do today.
2. Lists the exact steps required to run the MVP demo end-to-end.
3. Provides a one-glance "did the demo pass?" checklist.
4. States the minimum ops checklist required to take the same stack
   from a demo box to an initial production deployment.

**Audience.** A demo operator, an evaluating reviewer, or an SRE
preparing the first production cutover. Engineering-team members
implementing features should read the deep runbooks directly.

**This document does not duplicate large content.** Wherever a deep
runbook already covers a topic in detail, this page links out instead
of repeating it. The deep runbooks are:

- [`first-admin-bootstrap-runbook.md`](first-admin-bootstrap-runbook.md)
  — env vars, JWT decoder, bootstrap, audit expectations, rollback.
- [`telegram-outbound-gateway-runbook.md`](telegram-outbound-gateway-runbook.md)
  — Telegram activation, runtime flow, observability, troubleshooting,
  inbound webhook contract.
- [`demo-smoke-runbook.md`](demo-smoke-runbook.md) — full end-to-end
  smoke flow (sections §6–§9) and the Phase 179 real-Telegram manual
  smoke checklist (§13).

---

## 2. Capability snapshot — Phase 185 foundation plus later documented additions

> Phase 190 admin write surface (owner / priority / severity) is
> already implemented and is verified through the consolidated
> checklist in §5 plus the demo smoke recipes in
> [`demo-smoke-runbook.md` §14](demo-smoke-runbook.md#14-phase-190-admin-write-smoke--owner--priority--severity).
>
> **Phase 194 + 195 + 196.** The Telegram card text now renders optional
> `Priority: <code>` and `Severity: <code>` lines when the projected
> `WorkItem` carries those fields at intake commit or workflow
> transition commit. When both fields are absent the card text remains
> the pre-Phase-194 three-line shape (`header` / `[code] title` /
> `Status: ...`), preserving Phase 179 `NOT_MODIFIED` behavior for
> unchanged work items. Phase 195 extends the intake API
> (`POST /api/intake/work-items`) with optional `priorityCode` /
> `severityCode` / `ownerUserId` fields. Phase 196 additionally renders
> an `Owner: <displayName>` line resolved publisher-side via
> `IdentityQueryService.findUserById(ownerUserId)`; the Telegram module
> never imports identity (ArchUnit-enforced), and a null / blank
> `displayName` omits the line (the raw owner UUID is never rendered).
> Admin-write endpoints from Phase 190 continue to **not** publish a
> Telegram refresh event; new priority / severity / owner values appear
> in the card only on the next intake or workflow transition for that
> work item.
> See [`demo-smoke-runbook.md` §7 "Card text shape"](demo-smoke-runbook.md#card-text-shape)
> for the exact rendered format.
>
> The bullet list below remains the Phase 185 foundation; it is not
> rewritten here to avoid duplication.

What the platform can do today (verified, tested, documented):

- Multi-tenant identity, membership, and role/permission catalog with
  ADMIN seed.
- Idempotent first-admin bootstrap (tenant + admin user + ADMIN role
  binding + MVP Bug workflow seed: `BUGS` → `PROCESSING` → `TESTING` →
  `FIXED` plus `TESTING → BUGS` and `FIXED → BUGS` reopen).
- JWT-protected admin tenant config API: tenant, app users, workflow
  definitions / statuses / transition rules, chat bindings, topic
  bindings, routing rules, memberships, roles, role-permission
  bindings. Default-deny `/api/**`.
- Intake API (`POST /api/intake/work-items`) and workflow transition
  API (`POST /api/work-items/{id}/transitions`) with server-side
  `WORK_ITEM_CREATE` / `WORK_ITEM_TRANSITION` enforcement.
- Telegram outbound: `sendMessage` (Phase 158), `editMessageText`
  (Phase 177), `answerCallbackQuery` (Phase 175). Token-aware
  sanitization. Stub fallback when token is blank.
- AFTER_COMMIT card dispatch (Phase 164) with edit-first /
  send-as-fallback policy (Phase 179) and bounded synchronous
  retry/backoff for `RATE_LIMIT` / `NETWORK_ERROR` (Phase 168).
- Telegram inbound webhook (`POST /api/telegram/webhook`) with
  constant-time secret token (Phase 171), parser + authorized workflow
  execution (Phase 173), bounded UX toast (Phase 175).
- Append-only delivery attempt history + 7 admin observability
  endpoints under `/api/admin/delivery-observability` (Phase 158+).
- **Phase 185.** Denial/failure callback outcomes
  (`NOT_A_MEMBER`, `PERMISSION_DENIED`, `INVALID_TRANSITION`,
  `UNEXPECTED_FAILURE`) emit a fail-soft `TELEGRAM_CALLBACK_DENIED`
  audit row via `AuditService.recordEventInNewTransaction(...)`
  (REQUIRES_NEW). Successful EXECUTED path continues to use the
  existing `STATUS_TRANSITION` audit from `WorkflowTransitionService`.
- **Phase 195 + 196.** Intake accepts optional `priorityCode` /
  `severityCode` / `ownerUserId`; Telegram cards render `Priority:` /
  `Severity:` / `Owner:` lines when set, with owner displayName
  resolved publisher-side via `IdentityQueryService` (telegram module
  remains identity-independent, ArchUnit-enforced).

---

## 3. Path A — real Telegram demo run

Use this path when the operator has a real Telegram bot token and a
test chat/topic.

### 3.1 Environment

Set the demo env vars per
[`first-admin-bootstrap-runbook.md` §3](first-admin-bootstrap-runbook.md#3-jwt-decoder-configuration)
and
[`first-admin-bootstrap-runbook.md` §4](first-admin-bootstrap-runbook.md#4-bootstrap-properties)
(JWT decoder + bootstrap properties).

In addition, set Telegram activation env per
[`telegram-outbound-gateway-runbook.md` §3](telegram-outbound-gateway-runbook.md#3-configuration-properties):

- `TELEGRAM_BOT_TOKEN` — real Bot API token.
- `APP_TELEGRAM_WEBHOOK_SECRET_TOKEN` — a strong random secret for the
  webhook header check.

> Webhook secret is **fail-closed**: if it is blank, every inbound
> request is rejected with `401 + UNAUTHORIZED` envelope.

### 3.2 Start the stack

Local Docker Postgres + Spring Boot per
[`demo-smoke-runbook.md` §4](demo-smoke-runbook.md#4-startup).

### 3.3 First-admin bootstrap (one time)

Follow
[`first-admin-bootstrap-runbook.md` §5](first-admin-bootstrap-runbook.md#5-first-run-sequence)
to seed:

- one tenant,
- one app user (bound to a Telegram user id),
- one ADMIN role binding,
- the MVP Bug workflow.

`BootstrapAdminInitializer` is idempotent — re-running the app with the
same bootstrap env is a no-op. The `BOOTSTRAP_COMPLETED` audit row is
the success signal.

### 3.4 Operator JWT

Mint a demo JWT per
[`demo-smoke-runbook.md` §5](demo-smoke-runbook.md#5-jwt-preparation).
The `sub` claim must equal `APP_BOOTSTRAP_ADMIN_APP_USER_ID`.

### 3.5 Configure tenant routing

Create the chat binding, topic binding, and routing rule per
[`demo-smoke-runbook.md` §6](demo-smoke-runbook.md#6-configure-telegram-routing-through-admin-apis).

### 3.6 Register the webhook with Telegram (one time)

Run the `setWebhook` curl in
[`telegram-outbound-gateway-runbook.md` §12.3](telegram-outbound-gateway-runbook.md#123-one-time-setwebhook-registration)
so Telegram delivers callback updates to `/api/telegram/webhook` with
the `X-Telegram-Bot-Api-Secret-Token` header.

### 3.7 Run the demo flow

Follow
[`demo-smoke-runbook.md` §7–§9](demo-smoke-runbook.md#7-create-a-work-item-through-the-intake-api):

1. Intake `POST /api/intake/work-items` — Card #1 appears in the
   configured chat/topic.
2. Transition `POST /api/work-items/{id}/transitions` from
   `BUGS → PROCESSING` — Card #1 updates in place (Phase 179
   edit-first branch).
3. Press the **Start Processing** inline button on a *different* card
   to also exercise the callback path (Phase 173/175). An ephemeral
   toast appears to the operator.
4. Verify delivery observability via
   `GET /api/admin/delivery-observability/details?...`.

### 3.8 Phase 179 manual smoke (real Telegram)

To verify every edit-first / send-as-fallback branch
(`EDITED`, `NOT_MODIFIED`, `EDIT_REJECTED_FALLBACK_SEND`, diagnostic
transient failures, callback path), execute
[`demo-smoke-runbook.md` §13](demo-smoke-runbook.md#13-phase-179-manual-smoke-checklist--real-telegram-mode)
branch by branch.

---

## 4. Path B — stub mode demo run

Use this path when the operator has no real Telegram bot token but
still wants to demonstrate the full backend chain (authorization,
intake, transition, audit, delivery observability).

### 4.1 Environment

Identical to Path A, **except** leave `TELEGRAM_BOT_TOKEN` blank. The
`StubTelegramOutboundGateway` activates automatically because
`TelegramOutboundGatewayConfiguration` is mutually exclusive on
non-blank token (Phase 158).

The webhook secret can be left blank as well; in that mode the
webhook endpoint rejects every inbound request with
`401 + UNAUTHORIZED` (fail-closed). For a callback-path demo, set the
webhook secret and *manually POST* to `/api/telegram/webhook` with the
matching `X-Telegram-Bot-Api-Secret-Token` header (no real Telegram
needed).

### 4.2 Run the demo flow

Identical to Path A §3.5–§3.7 with two semantic differences:

- Every dispatch produces a `FAILED` `telegram_delivery_attempt` row
  with `failure_code = UNKNOWN_ERROR` and the stub reason
  "Telegram outbound gateway hali implement qilinmagan".
- Card refresh (Phase 179) falls back to the send retry pipeline,
  which also returns `UNKNOWN_ERROR` from the stub. This is the
  documented stub-mode branch in
  [`demo-smoke-runbook.md` §13.5](demo-smoke-runbook.md#135-branch-e--stub-mode).

Path B verifies authorization, transaction discipline, AFTER_COMMIT
dispatch, retry policy, audit emission, and observability without
touching real Telegram.

---

## 5. Consolidated success checklist

A demo run is "passed" only if every box below is ticked. Each box
maps to a single observable signal in logs, HTTP responses, or DB
queries documented in the deep runbooks.

- [ ] PostgreSQL is reachable (`docker compose ps`).
- [ ] Spring Boot starts cleanly with the chosen profile.
- [ ] `BootstrapAdminInitializer` log lines confirm tenant + admin
      user + ADMIN role binding + MVP Bug Flow workflow seed.
- [ ] `BOOTSTRAP_COMPLETED` audit row exists for the tenant.
- [ ] Demo JWT is accepted (`401` does not occur on admin endpoints).
- [ ] `GET /api/admin/tenant-config/details` returns the seeded MVP
      Bug Flow.
- [ ] Chat binding, topic binding, and routing rule each return
      `201 Created` from the admin API.
- [ ] Intake `POST /api/intake/work-items` returns `201` with
      `routingPrepared: true`.
- [ ] Path A only: Card #1 is visible in the configured chat/topic.
- [ ] Path B only: a `FAILED` `SEND_NEW_MESSAGE` attempt row exists
      with `failure_code = UNKNOWN_ERROR`.
- [ ] Transition `POST /api/work-items/{id}/transitions` returns
      `200` with `currentStatusCode: "PROCESSING"`.
- [ ] Card refresh behavior matches exactly one of the Phase 179
      decision-tree branches documented in
      [`demo-smoke-runbook.md` §12](demo-smoke-runbook.md#12-success-checklist).
- [ ] For callback-triggered transitions (Path A), an
      `answerCallbackQuery` toast appears to the operator who clicked
      the inline button.
- [ ] **Phase 185.** For an intentionally-denied callback (try a
      transition with a JWT whose actor lacks `WORK_ITEM_TRANSITION`,
      or click a button on a work item the actor is not a member
      of), one `TELEGRAM_CALLBACK_DENIED` audit row is written for
      the denied outcome. See §8 below.
- [ ] **Phase 190 admin write smoke.** Run the three admin write
      endpoints against the demo work item per
      [`demo-smoke-runbook.md` §14](demo-smoke-runbook.md#14-phase-190-admin-write-smoke--owner--priority--severity):
      assign owner, update priority (`HIGH`), update severity
      (`CRITICAL`). Verify the three resulting audit rows
      (`OWNER_ASSIGNED`, `PRIORITY_CHANGED`, `SEVERITY_CHANGED`, all
      with `action_source = ADMIN_API`); verify at least one `403
      ACCESS_DENIED` against a non-permitted actor and one `422`
      with `INVALID_PRIORITY_CODE` or `INVALID_SEVERITY_CODE`. The
      Telegram card is intentionally **not** re-rendered by these
      admin writes (Phase 190 scope decision).
- [ ] `GET /api/admin/delivery-observability/details?...` returns the
      expected attempt count for the executed branch.
- [ ] Application logs contain no bot-token substring — token
      sanitization (Phase 158) is intact.

If every applicable box is ticked, the MVP demo path is verified
end-to-end.

---

## 6. Production-readiness-MVP checklist

This is a minimum checklist for taking the same stack from "demo
passes" to an initial production deployment with the current MVP
feature set. It introduces no new features and no new infrastructure
beyond what the existing runbooks describe.

- [ ] `spring.profiles.active=prod`.
- [ ] `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` are
      set to production values via the deployment platform — local
      fallback defaults must not leak.
- [ ] The historical leaked password documented in
      [README "Security note (Phase 152)"](../../README.md#local-db-credentials-and-overrides)
      is rotated outside the repo if it was reused.
- [ ] JWT decoder is configured for production by setting **exactly
      one** of `APP_SECURITY_JWT_ISSUER_URI` or
      `APP_SECURITY_JWT_JWK_SET_URI` per
      [`first-admin-bootstrap-runbook.md` §3](first-admin-bootstrap-runbook.md#3-jwt-decoder-configuration).
      `APP_SECURITY_JWT_HMAC_SECRET` must be unset in production.
- [ ] `TELEGRAM_BOT_TOKEN` is set in the secret manager, not in
      configuration files. Verify token rotation procedure with
      [`telegram-outbound-gateway-runbook.md` §4](telegram-outbound-gateway-runbook.md#4-safe-usage-by-environment).
- [ ] `APP_TELEGRAM_WEBHOOK_SECRET_TOKEN` is set to a strong random
      secret and matched by the operator's `setWebhook` call.
- [ ] `setWebhook` has been run exactly once for the production bot
      and the HTTPS endpoint is reachable from Telegram.
- [ ] `BOOTSTRAP_COMPLETED` audit row exists once and bootstrap env
      vars are then disabled (`APP_BOOTSTRAP_ADMIN_ENABLED=false`).
- [ ] `/actuator/health` is reachable to the load balancer.
      `/actuator/{info, metrics, flyway}` require authentication
      (Phase 146 default-deny).
- [ ] Application logs do not contain the bot token, the webhook
      secret, raw callback_data, or rendered Telegram text. A spot
      `grep` after a few minutes of traffic is sufficient.
- [ ] At least one `TELEGRAM_CALLBACK_DENIED` audit row has been
      written by intentionally driving a denied callback in staging
      to verify the Phase 185 audit emission path works in the
      production-equivalent deployment.

Items intentionally outside this checklist (deferred or
out-of-scope): Dockerfile, Kubernetes manifests, Micrometer
dashboards, web admin UI, multi-bot fan-out, conditional routing
rules, `parse_mode` rendering. None of these block the MVP demo or
an initial production deployment with the current feature set.

---

## 7. Known limitations recap

These are recognized and deferred. The exhaustive list with rationale
lives in
[`demo-smoke-runbook.md` §11](demo-smoke-runbook.md#11-known-limitations-intentionally-not-solved-here).
Highlights:

- No `parse_mode` rendering — all Telegram text is plain text.
- No `editMessageReplyMarkup` keyboard-only edits.
- No `EDIT_MESSAGE` attempt persistence — successful edits leave no
  row in `telegram_delivery_attempt`; coordinator bounded logs are
  the observability signal.
- No `telegram_active_card` projection table — active-card identity
  is derived from the latest DELIVERED `SEND_NEW_MESSAGE` attempt
  row (Phase 177 read model).
- No stale-card disable / cleanup — server-side authorization and
  the strict state machine guarantee correctness regardless of
  which card was clicked.
- No `@Async` worker, scheduler, or outbox.
- No web admin UI — configuration is via `curl` against the admin
  API.
- No automatic chat / topic / routing seed — production safety
  requires these to come from the operator's environment.
- No conditional routing rules — only unconditional routing.

---

## 8. Phase 185 audit note

Phase 185 adds **denial / failure callback audit emission**.

When `TelegramCallbackActionExecutionService` produces an
`ExecutionOutcome` that is neither a happy `EXECUTED` (already audited
as `STATUS_TRANSITION` by `WorkflowTransitionService`) nor a
"cannot-attribute" case (`USER_NOT_FOUND` has no resolved actor;
`WORK_ITEM_NOT_FOUND` has no derived tenant), the service writes one
`TELEGRAM_CALLBACK_DENIED` audit row through
`AuditService.recordEventInNewTransaction(...)` with
`Propagation.REQUIRES_NEW`. The covered outcomes are:

- `NOT_A_MEMBER`
- `PERMISSION_DENIED`
- `INVALID_TRANSITION` (from a `BusinessRuleException` or from an
  unmapped action code)
- `UNEXPECTED_FAILURE`

Audit row shape:

| Column            | Value                                                   |
| ----------------- | ------------------------------------------------------- |
| `event_type`      | `TELEGRAM_CALLBACK_DENIED`                              |
| `entity_type`     | `WORK_ITEM`                                             |
| `entity_id`       | the `workItemId` from the parsed callback              |
| `tenant_id`       | the tenant derived server-side from `WorkItem`         |
| `actor_user_id`   | the resolved `AppUser` id                              |
| `action_source`   | `TELEGRAM_CALLBACK`                                     |
| `old_value_json`  | `null`                                                  |
| `new_value_json`  | `{"outcome":"...","actionCode":"...","targetStatusCode":"..."}` |

The payload is intentionally narrow. It **never** contains raw
`callback_data`, the bot token, the webhook secret, exception
messages, `from.username`, the full Telegram update payload, or any
rendered card text.

The emission is **fail-soft**: if the audit write throws, the service
swallows it with a bounded warning log (class simple name only), the
`ExecutionOutcome` returned to the controller is unchanged, the
`answerCallbackQuery` acknowledgement is still attempted, and nothing
propagates back to Telegram. The webhook continues to return `200 OK`
exactly as before — Telegram retry loops are not triggered.

Operators can query the audit table for denial activity:

```sql
SELECT occurred_at, tenant_id, actor_user_id, entity_id, new_value_json
  FROM audit_event
 WHERE event_type = 'TELEGRAM_CALLBACK_DENIED'
 ORDER BY occurred_at DESC
 LIMIT 100;
```

---

## 9. Where to go next

- For deep details on a specific subsystem, follow the link inside
  each section above to the deep runbook.
- For the full Phase 179 manual smoke matrix, see
  [`demo-smoke-runbook.md` §13](demo-smoke-runbook.md#13-phase-179-manual-smoke-checklist--real-telegram-mode).
- For project memory (phase log, architectural patterns, sensitive
  cases), see `MEMORY.md` outside the repo (operator-local). This
  document does not duplicate it.
- For future bounded phases: production deployment artifacts
  (Dockerfile, deployment doc), Micrometer counters for
  `TelegramCardRefreshDispatchService.OutcomeCategory`,
  `flyway` actuator endpoint scoping for prod, and the optional
  consolidation of `editMessageReplyMarkup` for keyboard-only edits
  are the natural candidates after Phase 185.
