# Demo Smoke Runbook — End-to-End Telegram Operator Flow

Operator-facing smoke runbook for verifying the current MVP flow end-to-end
on the Telegram Engineering Operations Platform after Phase 164.

> Audience: deployment / SRE / demo operators. Not a developer guide.

---

## 1. Purpose

This runbook walks an operator through a single, repeatable demo of the
full MVP path:

```
env + bootstrap + JWT
  → tenant config (chat binding + topic binding + routing rule)
    → POST /api/intake/work-items                   (Telegram card #1)
      → POST /api/work-items/{id}/transitions       (Telegram card #2)
        → GET  /api/admin/delivery-observability    (verification)
```

The goal is to prove, in real or stub Telegram mode, that the platform
produces the right cards in the right channel, records the right delivery
attempts, and remains the source of truth even if Telegram fails.

**Telegram is interaction surface only. PostgreSQL + the application layer
are the source of truth.** A failed Telegram delivery never rolls back a
work item create or a workflow transition. Per Phase 164, Telegram
dispatch runs *after* the business transaction commits, in an independent
`REQUIRES_NEW` transaction.

---

## 2. Preconditions

- Java 21
- Maven (`./mvnw` wrapper from the repo root)
- PostgreSQL running locally (`docker compose up -d` from the repo root
  is the supported path; H2 is for tests only).
- A Telegram bot **and** a Telegram chat / supergroup / topic-enabled
  group:
  - bot has been added to the chat;
  - bot has permission to post messages;
  - for topic-enabled supergroups: bot has access to the specific topic.
- A demo JWT with `sub` matching the bootstrap admin's `app-user-id`
  (see [Section 5](#5-jwt-preparation)).

The runbook covers **two modes**:

- **Real mode** — `TELEGRAM_BOT_TOKEN` is set; cards are sent to Telegram
  and `delivery_outcome = DELIVERED`.
- **Stub mode** — `TELEGRAM_BOT_TOKEN` is unset; the stub gateway records
  controlled `FAILED` attempts (`failure_code = UNKNOWN_ERROR`,
  `failure_reason = "Telegram outbound gateway hali implement qilinmagan"`).
  Useful when you don't want to send real traffic during a dry run.

Both modes exercise the same intake / workflow / observability path.

---

## 3. Environment variables

> The block below is for **local demo only**. Do not commit any of these
> values into git.

### macOS / Linux (zsh / bash)

```bash
# --- PostgreSQL (matches docker-compose.yml defaults) ---
export DATABASE_URL='jdbc:postgresql://localhost:5432/engops'
export DATABASE_USERNAME='engops'
export DATABASE_PASSWORD='engops_local'

# --- JWT (HS256 demo mode — see Section 5 for production options) ---
# At least 32 bytes of random data. Generate fresh per environment.
export APP_SECURITY_JWT_HMAC_SECRET='replace-with-32+-byte-random-secret'

# --- Bootstrap admin (drives BootstrapAdminInitializer) ---
export APP_BOOTSTRAP_ADMIN_ENABLED='true'
export APP_BOOTSTRAP_ADMIN_TENANT_NAME='Demo Tenant'
export APP_BOOTSTRAP_ADMIN_TENANT_SLUG='demo'
export APP_BOOTSTRAP_ADMIN_TENANT_TIMEZONE='UTC'
# UUID — must equal the JWT 'sub' claim used by the operator.
export APP_BOOTSTRAP_ADMIN_APP_USER_ID='11111111-1111-1111-1111-111111111111'
# Long — operator's real Telegram numeric user id (informational/audit).
export APP_BOOTSTRAP_ADMIN_TELEGRAM_USER_ID='123456789'
export APP_BOOTSTRAP_ADMIN_DISPLAY_NAME='Demo Admin'
# Optional Telegram username (without '@').
export APP_BOOTSTRAP_ADMIN_USERNAME='demo_admin'

# --- Bootstrap workflow seed (idempotent MVP Bug Flow) ---
export APP_BOOTSTRAP_WORKFLOW_ENABLED='true'
# default = "MVP Bug Flow"; uncomment to override:
# export APP_BOOTSTRAP_WORKFLOW_NAME='MVP Bug Flow'

# --- Telegram outbound (omit for stub mode) ---
export TELEGRAM_BOT_TOKEN='123456:replace-with-real-bot-token'
```

### Windows PowerShell

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/engops'
$env:DATABASE_USERNAME = 'engops'
$env:DATABASE_PASSWORD = 'engops_local'

$env:APP_SECURITY_JWT_HMAC_SECRET = 'replace-with-32+-byte-random-secret'

$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:APP_BOOTSTRAP_ADMIN_TENANT_NAME = 'Demo Tenant'
$env:APP_BOOTSTRAP_ADMIN_TENANT_SLUG = 'demo'
$env:APP_BOOTSTRAP_ADMIN_TENANT_TIMEZONE = 'UTC'
$env:APP_BOOTSTRAP_ADMIN_APP_USER_ID = '11111111-1111-1111-1111-111111111111'
$env:APP_BOOTSTRAP_ADMIN_TELEGRAM_USER_ID = '123456789'
$env:APP_BOOTSTRAP_ADMIN_DISPLAY_NAME = 'Demo Admin'
$env:APP_BOOTSTRAP_ADMIN_USERNAME = 'demo_admin'

$env:APP_BOOTSTRAP_WORKFLOW_ENABLED = 'true'

$env:TELEGRAM_BOT_TOKEN = '123456:replace-with-real-bot-token'
```

The full operator contract for these JWT and bootstrap envs (including
production OIDC variants) is in
[First-Admin Bootstrap Runbook](first-admin-bootstrap-runbook.md). Token
contract for `TELEGRAM_BOT_TOKEN` is in
[Telegram Outbound Gateway Runbook](telegram-outbound-gateway-runbook.md).

---

## 4. Startup

Start PostgreSQL (one terminal):

```bash
docker compose up -d
```

Start the application (second terminal, after exporting the env vars
from Section 3):

### macOS / Linux

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Windows PowerShell

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Expected boot behavior

On the first boot with `app.bootstrap.admin.enabled=true`,
`BootstrapAdminInitializer` (Phases 143 + 156) idempotently provisions:

- a `Tenant` matching `APP_BOOTSTRAP_ADMIN_TENANT_SLUG`;
- an `AppUser` whose UUID equals `APP_BOOTSTRAP_ADMIN_APP_USER_ID`
  (this is the operator's JWT `sub`);
- an ACTIVE `Membership` linking the user to the tenant;
- an ADMIN role binding (V2/V6 seed catalog → `ADMIN` role with all 13
  default permissions including `TENANT_CONFIG_WRITE`,
  `WORK_ITEM_CREATE`, `WORK_ITEM_TRANSITION`).

Additionally, with `app.bootstrap.workflow.enabled=true`,
`BootstrapAdminInitializer` seeds the default MVP Bug Flow workflow
definition (statuses: `BUGS` initial, `PROCESSING`, `TESTING`, `FIXED`
terminal; transitions: `BUGS→PROCESSING`, `PROCESSING→TESTING`,
`TESTING→FIXED`, `TESTING→BUGS`, `FIXED→BUGS`).

You will see log lines similar to:

```
INFO  c.e.p.a.b.BootstrapAdminInitializer - Bootstrap admin initialization started: tenantSlug=demo, appUserId=11111111-...
INFO  c.e.p.a.b.BootstrapAdminInitializer - Bootstrap workflow seed started: tenantId=..., workflowName=MVP Bug Flow
INFO  c.e.p.a.b.BootstrapAdminInitializer - Bootstrap workflow seed completed: tenantId=..., workflowId=...
INFO  c.e.p.a.b.BootstrapAdminInitializer - Bootstrap admin initialization completed: tenantId=..., appUserId=11111111-..., slug=demo
```

A `BOOTSTRAP_COMPLETED` audit row is recorded with
`actorUserId = APP_BOOTSTRAP_ADMIN_APP_USER_ID` and
`actionSource = BOOTSTRAP`.

Subsequent boots are no-ops at the row level — the bootstrap is fully
idempotent.

---

## 5. JWT preparation

The platform's JWT decoder is identity-only. The required claim is `sub`,
which **must equal** `APP_BOOTSTRAP_ADMIN_APP_USER_ID` (a UUID). All
authority decisions come from the DB-backed permission chain
(`Membership → Role → RolePermission → Permission`), not from JWT scopes
or roles.

For the demo, sign an HS256 JWT with the same secret you exported as
`APP_SECURITY_JWT_HMAC_SECRET`.

Minimal payload:

```json
{
  "sub": "11111111-1111-1111-1111-111111111111",
  "telegram_user_id": 123456789,
  "iat": 1700000000,
  "exp": 1700003600
}
```

Any standards-compliant JWT signing tool works — popular options:

- **jwt.io** (web): paste the payload, HS256 algorithm, paste secret,
  copy the encoded token. Suitable only for local demo with a throwaway
  secret.
- **jose CLI / jjwt-cli / step-cli**: command-line, scriptable.
- A short Python script using `PyJWT`:

  ```bash
  pip install pyjwt
  python3 -c "import jwt, time; print(jwt.encode({'sub':'11111111-1111-1111-1111-111111111111','telegram_user_id':123456789,'iat':int(time.time()),'exp':int(time.time())+3600}, '$APP_SECURITY_JWT_HMAC_SECRET', algorithm='HS256'))"
  ```

Save the encoded token to a shell variable for reuse:

```bash
export DEMO_JWT='eyJhbGciOi...'   # paste the token
```

For production deployments, replace the HMAC secret with
`APP_SECURITY_JWT_ISSUER_URI` or `APP_SECURITY_JWT_JWK_SET_URI`. See
[First-Admin Bootstrap Runbook](first-admin-bootstrap-runbook.md) for
the full security model and decoder modes.

---

## 6. Configure Telegram routing through admin APIs

Bootstrap does **not** seed `chat_binding`, `topic_binding`, or
`routing_rule` rows — these depend on real Telegram chat ids and must be
created through the admin API. The ADMIN role granted by bootstrap
already includes `TENANT_CONFIG_WRITE`, so the demo JWT is authorized.

In a third terminal, run the four `curl` commands below. Copy
`$TENANT_ID` from the bootstrap startup log line
`Bootstrap admin initialization completed: tenantId=<uuid>, ...`
(or from the `BOOTSTRAP_COMPLETED` audit row in the database). The platform
intentionally does not expose a public list-tenants or
find-tenant-by-slug endpoint — the bootstrap log is the authoritative
discovery path for the freshly seeded tenant id.

```bash
export DEMO_BASE='http://localhost:8080'
export TENANT_ID='paste-tenant-uuid-from-bootstrap-log'
```

### 6.1 Confirm tenant (read-only)

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$DEMO_BASE/api/admin/tenant-config/details?tenantId=$TENANT_ID"
```

Expected: `200 OK` with tenant + workflow definition details for the
seeded MVP Bug Flow.

### 6.2 Create a chat binding

`POST /api/admin/tenant-config/chat-bindings?tenantId={tenantId}`,
body = `CreateChatBindingRequest { chatId, chatTitle, bindingType }`.

`bindingType` accepts the values defined in `TelegramChatBindingType`
(currently `MAIN_GROUP`, `NOTIFICATION_GROUP`).

```bash
CHAT_BINDING=$(curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/admin/tenant-config/chat-bindings?tenantId=$TENANT_ID" \
  -d '{
        "chatId": -1001234567890,
        "chatTitle": "Demo Bug Channel",
        "bindingType": "NOTIFICATION_GROUP"
      }')
echo "$CHAT_BINDING"
export CHAT_BINDING_ID=$(echo "$CHAT_BINDING" | python3 -c "import sys,json; print(json.load(sys.stdin)['chatBindingId'])")
echo "CHAT_BINDING_ID=$CHAT_BINDING_ID"
```

Expected: `201 Created` with `chatBindingId` populated. The Telegram
`chatId` is the negative numeric id Telegram assigns to groups /
channels (positive for bot-private chats with users).

### 6.3 Create a topic binding

`POST /api/admin/tenant-config/topic-bindings?tenantId={tenantId}`,
body = `CreateTopicBindingRequest { chatBindingId, topicId, topicName, purpose }`.

For chat-only delivery in groups without topics, use any non-null
`topicId` your routing should target. For supergroups with topics, use
the integer topic id Telegram exposes.

```bash
TOPIC_BINDING=$(curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/admin/tenant-config/topic-bindings?tenantId=$TENANT_ID" \
  -d "{
        \"chatBindingId\": \"$CHAT_BINDING_ID\",
        \"topicId\": 1,
        \"topicName\": \"Bugs\",
        \"purpose\": \"Bug intake notifications\"
      }")
echo "$TOPIC_BINDING"
export TOPIC_BINDING_ID=$(echo "$TOPIC_BINDING" | python3 -c "import sys,json; print(json.load(sys.stdin)['topicBindingId'])")
echo "TOPIC_BINDING_ID=$TOPIC_BINDING_ID"
```

Expected: `201 Created` with `topicBindingId` populated.

### 6.4 Create a routing rule

`POST /api/admin/tenant-config/routing-rules?tenantId={tenantId}`,
body = `CreateRoutingRuleRequest { name, workItemType, priority, targetTopicBindingId, conditionExpression }`.

`workItemType` is one of `BUG`, `INCIDENT`, `TASK`. For unconditional
matching (the only supported mode today) leave `conditionExpression`
unset.

```bash
curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/admin/tenant-config/routing-rules?tenantId=$TENANT_ID" \
  -d "{
        \"name\": \"Bugs to demo channel\",
        \"workItemType\": \"BUG\",
        \"priority\": 100,
        \"targetTopicBindingId\": \"$TOPIC_BINDING_ID\",
        \"conditionExpression\": null
      }"
```

Expected: `201 Created` with the new routing rule id.

---

## 7. Create a work item through the intake API

`POST /api/intake/work-items`, body = `WorkItemIntakeRequest`:

```
tenantId               UUID    required
typeCode               String  required (BUG | INCIDENT | TASK)
title                  String  required, non-blank
description            String  optional
workflowDefinitionId   UUID    optional (auto-resolved from active BUG workflow)
initialStatusCode      String  optional (auto-resolved to BUGS for the seeded workflow)
createdByUserId        UUID    accepted but ignored — derived from JWT @CurrentActor
actionSource           String  required (e.g. MANUAL, TELEGRAM, API)
```

```bash
INTAKE=$(curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/intake/work-items" \
  -d "{
        \"tenantId\": \"$TENANT_ID\",
        \"typeCode\": \"BUG\",
        \"title\": \"Login screen breaks on Safari\",
        \"description\": \"Repro: open /login on Safari 17, click submit\",
        \"actionSource\": \"MANUAL\"
      }")
echo "$INTAKE"
export WORK_ITEM_ID=$(echo "$INTAKE" | python3 -c "import sys,json; print(json.load(sys.stdin)['workItemId'])")
export WORK_ITEM_CODE=$(echo "$INTAKE" | python3 -c "import sys,json; print(json.load(sys.stdin)['workItemCode'])")
echo "WORK_ITEM_ID=$WORK_ITEM_ID  WORK_ITEM_CODE=$WORK_ITEM_CODE"
```

Expected:

- `201 Created`
- `workItemCode` like `BUG-1`
- `currentStatusCode = "BUGS"`
- **`routingPrepared: true`**
- `targetChatBindingId` and `targetTopicId` populated

### What happens after commit

The intake transaction commits with the new `WorkItem` row, the audit
event, and a queued `TelegramCardDispatchRequested` event.
`TelegramCardDispatchEventListener` (Phase 164) fires *after* the commit
in its own `REQUIRES_NEW` transaction, runs the
render → outbound → persistence chain, and inserts a
`telegram_delivery_attempt` row.

- **Real mode (`TELEGRAM_BOT_TOKEN` set):** the bot posts a card with
  the bug summary into the configured chat / topic. The attempt row has
  `delivery_outcome = DELIVERED` and `external_message_id` populated.
- **Stub mode (no token):** no Telegram traffic; the attempt row has
  `delivery_outcome = FAILED` with
  `failure_code = UNKNOWN_ERROR` and
  `failure_reason = "Telegram outbound gateway hali implement qilinmagan"`.
  The intake is still successful from the application's perspective.

---

## 8. Transition the work item

`POST /api/work-items/{workItemId}/transitions`,
body = `WorkflowTransitionRequest`:

```
tenantId           UUID    required
targetStatusCode   String  required (e.g. PROCESSING)
actorUserId        UUID    accepted but ignored — derived from JWT @CurrentActor
actionSource       String  required (e.g. MANUAL, TELEGRAM, API)
reason             String  optional, persisted on the transition history row
```

The seeded MVP Bug Flow allows the transition `BUGS → PROCESSING`:

```bash
curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/work-items/$WORK_ITEM_ID/transitions" \
  -d "{
        \"tenantId\": \"$TENANT_ID\",
        \"targetStatusCode\": \"PROCESSING\",
        \"actionSource\": \"MANUAL\",
        \"reason\": \"Demo: triaging now\"
      }"
```

Expected:

- `200 OK`
- `currentStatusCode = "PROCESSING"`
- `lastTransitionAt` populated, `updatedAt` advanced

### What happens after commit

The transition transaction commits with the updated `WorkItem`, the
`work_item_transition` history row, the audit event, and another queued
`TelegramCardDispatchRequested` event (this time with
`sourceFlow = "WORKFLOW_TRANSITION"` and
`targetStatusCode = "PROCESSING"`). The listener fires after commit and
sends a **new** Telegram message — `editMessageText` is intentionally
not used (Phase 161 / Phase 164: the Telegram `message_id` is not stored
on `WorkItem`). You should see a second card appear in the chat.

---

## 9. Verify delivery observability

Two read-only admin endpoints surface the append-only attempt history.
Both require the same JWT.

### 9.1 Detail view for the demo work item

`GET /api/admin/delivery-observability/details?tenantId={uuid}&workItemCode={code}&historyLimit=10`

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$DEMO_BASE/api/admin/delivery-observability/details?tenantId=$TENANT_ID&workItemCode=$WORK_ITEM_CODE&historyLimit=10"
```

Expected (real mode, both intake and transition routing-prepared):

- `latestMetrics.deliveryOutcome = "DELIVERED"`
- `recentAttempts` contains **two** entries, ordered most-recent-first
- both entries have `targetChatBindingId` and `targetTopicId` populated
- both entries have `externalMessageId` populated and no
  `failureCode` / `failureReason`

Expected (stub mode):

- `latestMetrics.deliveryOutcome = "FAILED"`
- `recentAttempts` contains two entries, each with
  `failureCode = "UNKNOWN_ERROR"` and
  `failureReason = "Telegram outbound gateway hali implement qilinmagan"`

### 9.2 Tenant summary

`GET /api/admin/delivery-observability/summary?tenantId={uuid}&limit=20`

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$DEMO_BASE/api/admin/delivery-observability/summary?tenantId=$TENANT_ID&limit=20"
```

Expected: a list including the demo work item with its latest metrics
snapshot.

---

## 10. Troubleshooting

### 10.1 `401 UNAUTHORIZED`

- The JWT is missing, malformed, or signed with a different secret.
- Re-export `APP_SECURITY_JWT_HMAC_SECRET` and regenerate the token.
- Confirm the `Authorization: Bearer ...` header is being sent.

### 10.2 `403 ACCESS_DENIED`

- The JWT `sub` does not match any `app_user.id` for the tenant, **or**
  the user has no membership / role with the required permission.
- Confirm `APP_BOOTSTRAP_ADMIN_APP_USER_ID` matches the JWT `sub`.
- Check the bootstrap log line `Bootstrap admin initialization completed`.
- See `first-admin-bootstrap-runbook.md` Section 5 for full triage.

### 10.3 Intake returns `routingPrepared: false`

- A routing rule for `workItemType = BUG` does not exist, or
  `targetTopicBindingId` does not resolve to an active topic binding,
  or the chat binding behind it is missing / inactive.
- Re-run the four `curl` commands in [Section 6](#6-configure-telegram-routing-through-admin-apis).
- Verify with `GET /api/admin/tenant-config/routing-rules?tenantId=...`.

### 10.4 No attempt row appears

- If `routingPrepared: false`, the listener never publishes — *expected*.
- If `routingPrepared: true` and still no row, check application logs
  for `Telegram card dispatch failed (fail-soft)` warnings — bounded
  metadata (`sourceFlow`, `tenantId`, `workItemId`, `targetStatusCode`,
  `exceptionType`) is logged.

### 10.5 Attempts show `failure_code = UNKNOWN_ERROR` with stub reason

When the active execute-path runs through `StubTelegramOutboundGateway`,
the attempt row carries `failure_code = "UNKNOWN_ERROR"` and
`failure_reason = "Telegram outbound gateway hali implement qilinmagan"`.

- Stub mode is active. Either set `TELEGRAM_BOT_TOKEN` and restart the
  app, or accept stub mode for this demo run.

> **Note (legacy literal):** the string `TELEGRAM_GATEWAY_NOT_IMPLEMENTED`
> appears only inside the deprecated `TelegramOutboundGateway.dispatch(command)`
> path of the stub bean — production code never invokes that path. If you
> ever see it in the wild, a caller is using the legacy path; the active
> `execute(request)` path emits `UNKNOWN_ERROR`.

### 10.6 Attempts show `failure_code = INVALID_REQUEST`

- Wrong `chatId`, wrong `topicId`, bot is not a member of the chat, or
  the bot lacks permission.
- For supergroup topics, confirm the bot has access to the specific
  topic.

### 10.7 Attempts show `failure_code = RATE_LIMIT`

- Telegram HTTP 429. Retry is not implemented yet
  (see [Section 11](#11-known-limitations-intentionally-not-solved-here)).
- Reduce burst rate; future phases will add backoff.

### 10.8 Attempts show `failure_code = NETWORK_ERROR`

- Connect / read timeout, DNS, or Telegram 5xx. Validate egress to
  `api.telegram.org`. Consider raising `app.telegram.connect-timeout-ms`
  / `app.telegram.read-timeout-ms` in production.

### 10.9 Telegram card does not appear despite `DELIVERED`

- Bot is in a different chat than `chatId`; correct the chat binding.
- For topic-enabled supergroups, confirm `topicId` matches the topic
  the bot is allowed to post in.

For the full Telegram-side failure semantics and operator response
playbook, see
[Telegram Outbound Gateway Runbook](telegram-outbound-gateway-runbook.md)
Section 10.

---

## 11. Known limitations intentionally not solved here

These gaps are recognized and deferred to later phases. Operators should
not be surprised by them during the demo.

- **No retry / backoff.** `RATE_LIMIT` and `NETWORK_ERROR` outcomes are
  recorded once and never retried.
- **No inbound webhook / `callback_query` handling.** Inline buttons may
  be rendered, but their presses are not received by the backend.
- **No `parse_mode` / Markdown / HTML rendering.** All outbound text is
  plain text.
- **No `editMessageText`.** Each transition sends a fresh card; old
  cards are not updated.
- **No `@Async` worker, scheduler, or outbox.** Dispatch is synchronous
  AFTER_COMMIT in the committing thread (Phase 164).
- **No web admin UI.** All configuration is via `curl` against the admin
  API.
- **No automatic chat / topic / routing seed.** Bootstrap intentionally
  does not provision Telegram chat ids — production safety requires
  these to come from the operator's environment, not from migrations.

---

## 12. Success checklist

A complete demo run should tick every box below.

- [ ] PostgreSQL is running (`docker compose ps`).
- [ ] Application starts cleanly with the demo profile.
- [ ] `BootstrapAdminInitializer` log lines confirm tenant + admin user
      + ADMIN role binding + MVP Bug Flow workflow seed.
- [ ] `BOOTSTRAP_COMPLETED` audit row exists for the tenant.
- [ ] Demo JWT is accepted (no `401` on admin endpoints).
- [ ] `GET /api/admin/tenant-config/details` returns the seeded MVP Bug
      Flow.
- [ ] Chat binding, topic binding, and routing rule are created
      (Section 6.2 / 6.3 / 6.4 each return `201 Created`).
- [ ] Intake `POST /api/intake/work-items` returns `201` with
      `routingPrepared: true`.
- [ ] **Card #1** appears in the Telegram chat / topic (real mode), or
      a `FAILED` attempt row exists with `failure_code = UNKNOWN_ERROR`
      and reason "Telegram outbound gateway hali implement qilinmagan"
      (stub mode).
- [ ] Transition `POST /api/work-items/{id}/transitions` returns `200`
      with `currentStatusCode: "PROCESSING"`.
- [ ] **Card #2** appears as a *new* message (not an edit of card #1)
      in the same chat / topic (real mode), or a second `FAILED` attempt
      row exists (stub mode).
- [ ] `GET /api/admin/delivery-observability/details?...` returns two
      attempts in `recentAttempts`.
- [ ] Application logs contain no token substrings — token sanitization
      (Phase 158) is intact.

If every box is ticked, the MVP demo path is verified end-to-end.
