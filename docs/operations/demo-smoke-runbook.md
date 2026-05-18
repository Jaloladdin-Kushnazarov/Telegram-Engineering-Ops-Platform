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
delegates to `TelegramCardRefreshDispatchService`, which since **Phase
179** runs an **edit-first / send-as-fallback** decision tree:

1. The card view is rendered once.
2. The coordinator queries the latest `DELIVERED` `SEND_NEW_MESSAGE`
   row in `telegram_delivery_attempt` for `(tenantId, workItemId)` as
   the active-card seed (Phase 177), resolves its
   `target_chat_binding_id` to a numeric `chat_id` via
   `TenantConfigQueryService`, and calls `editMessageText` on the
   stored `external_message_id` (Phase 177 gateway primitive).
3. Branches:
   - **Edit `SUCCESS`** — the existing card updates in place. **No new
     `telegram_delivery_attempt` row is appended** (Phase 179 does not
     persist `EDIT_MESSAGE` attempts).
   - **Edit `REJECTED` with description containing "message is not
     modified"** (case-insensitive) — treated as a benign no-op; no
     new message is sent and no new attempt row is appended.
   - **No prior delivered card** (e.g. first send for this work item,
     or all previous sends failed permanently) — the coordinator
     immediately falls back to the existing send retry pipeline,
     which appends a new `SEND_NEW_MESSAGE` attempt row.
   - **Edit `REJECTED` for any other reason / `FAILED` (`RATE_LIMIT`,
     `NETWORK_ERROR`, `UNKNOWN_ERROR`) / null result / refresh service
     `RuntimeException`** — fallback to the existing
     `TelegramCardDispatchRetryingService.dispatchWithRetry(cardView)`
     which renders, sends, retries (`RATE_LIMIT` / `NETWORK_ERROR`),
     and persists a `SEND_NEW_MESSAGE` attempt row per attempt.

In real mode with the demo's first transition you will most often see
the original card update in place (edit `SUCCESS`); in stub mode the
stub gateway forces an edit failure, the coordinator falls back to
send, and the stub send also produces a `FAILED` `SEND_NEW_MESSAGE`
attempt row.

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

- `latestMetrics.deliveryOutcome = "DELIVERED"`.
- Since **Phase 179**, the post-transition dispatch is **edit-first /
  send-as-fallback**. A successful `editMessageText` does **not**
  append a new `telegram_delivery_attempt` row (Phase 179 intentionally
  does not persist `EDIT_MESSAGE` attempts). The two real-mode branches
  the operator may observe in `recentAttempts`:
  - **Edit-success branch** — `recentAttempts` contains **one**
    `DELIVERED` `SEND_NEW_MESSAGE` entry from the intake send. The
    transition's edit ran in place against this row's
    `external_message_id`; no second row appears.
  - **Edit-fallback branch** (no prior delivered card / edit rejected
    / edit failed) — `recentAttempts` contains **two** entries
    ordered most-recent-first: the intake `SEND_NEW_MESSAGE` and the
    transition fallback `SEND_NEW_MESSAGE` from the existing send
    retry pipeline. Both entries have `targetChatBindingId` and
    `targetTopicId` populated; both have `externalMessageId` populated
    and no `failureCode` / `failureReason`.

Expected (stub mode):

- `latestMetrics.deliveryOutcome = "FAILED"`.
- Phase 179 edit-first attempts run through
  `StubTelegramOutboundGateway`, which returns a structured failure
  for both `editMessageText` and `sendMessage`. The coordinator
  treats the stub edit failure as fallback-required and triggers the
  existing send retry pipeline, which also fails through the stub —
  producing a new `FAILED` `SEND_NEW_MESSAGE` row each time.
- `recentAttempts` therefore typically contains entries (one per
  routing-prepared event) with
  `failureCode = "UNKNOWN_ERROR"` and
  `failureReason = "Telegram outbound gateway hali implement qilinmagan"`.

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

- Telegram HTTP 429. Phase 168 retry is active by default — the platform
  automatically re-attempts the send up to `app.telegram.retry.max-attempts`
  times with capped exponential backoff. Each retry creates its own
  append-only attempt row, so look for a subsequent `DELIVERED` row to
  confirm the retry succeeded.
- If `RATE_LIMIT` rows persist without a terminal `DELIVERED`, reduce
  burst rate upstream or widen `max-attempts` / `max-backoff-ms` for
  this environment.

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

- **Bounded synchronous retry / backoff** *(Phase 168)*. `RATE_LIMIT`
  and `NETWORK_ERROR` outcomes are retried in-process by
  `TelegramCardDispatchRetryingService` on the AFTER_COMMIT thread with
  capped exponential backoff (defaults: `max-attempts=3`,
  `initial-backoff-ms=500`, `max-backoff-ms=5000`, `multiplier=2.0`).
  `INVALID_REQUEST` and `UNKNOWN_ERROR` are not retried. Each attempt
  is a separate `telegram_delivery_attempt` row. There is still no
  async worker, no scheduler, and no outbox — the retry layer is purely
  in-thread.
- **Authorized callback workflow execution + ephemeral toast feedback +
  edit-first card refresh are now implemented** *(Phases 173 / 175 /
  179)*. Inline buttons posted by the bot reach
  `POST /api/telegram/webhook`; the webhook validates the
  `X-Telegram-Bot-Api-Secret-Token` header, parses
  `<UUID>:<ACTION_CODE>` data, resolves the Telegram user to a
  platform `AppUser`, derives the tenant server-side from the
  `WorkItem`, enforces ACTIVE membership and the
  `WORK_ITEM_TRANSITION` permission, executes the workflow transition
  via `WorkflowTransitionService`, and emits a bounded
  `answerCallbackQuery` toast back to the operator (Phase 175). After
  the workflow tx commits, the AFTER_COMMIT projection pipeline runs
  the Phase 179 edit-first / send-as-fallback path. See
  [Telegram Outbound Gateway Runbook §12](telegram-outbound-gateway-runbook.md#12-inbound-webhook-phase-171)
  for the full inbound contract.
- **No `parse_mode` / Markdown / HTML rendering.** All outbound text is
  plain text.
- **Telegram card refresh is edit-first / send-as-fallback** *(Phase
  179)*. A workflow transition with a prior delivered card updates
  that card in place via `editMessageText`; otherwise it falls back to
  the existing send retry pipeline and appends a new card. Successful
  edits intentionally do not persist `EDIT_MESSAGE` attempt rows in
  this phase.
- **No `@Async` worker, scheduler, or outbox.** Dispatch is synchronous
  AFTER_COMMIT in the committing thread (Phase 164).
- **No web admin UI.** All configuration is via `curl` against the admin
  API.
- **No automatic chat / topic / routing seed.** Bootstrap intentionally
  does not provision Telegram chat ids — production safety requires
  these to come from the operator's environment, not from migrations.

Still out of scope (deliberate, deferred to later bounded phases):

- **No `EDIT_MESSAGE` attempt persistence.** Successful edits leave no
  row in `telegram_delivery_attempt`; coordinator bounded logs are the
  observability signal for now.
- **No `telegram_active_card` projection table.** Active-card identity
  is currently derived from the latest DELIVERED `SEND_NEW_MESSAGE`
  attempt row (Phase 177 read model).
- **No stale-card disable / cleanup.** Old cards remain clickable;
  server-side authorization and the strict state-machine continue to
  guarantee correctness regardless of which card was clicked.
- **No `editMessageReplyMarkup` keyboard-only edits.**
- **No generic workflow engine / BPM DSL.**

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
- [ ] **Card refresh** observed per the Phase 179 edit-first /
      send-as-fallback decision tree — exactly one of the following
      branches is accepted:
      - *Real mode, prior delivered card + edit success:* Card #1
        updates in place; **no** new Telegram message is appended;
        `recentAttempts` still shows only the intake
        `SEND_NEW_MESSAGE` row.
      - *Real mode, no prior card / edit fallback:* Card #2 appears
        as a *new* message in the same chat / topic; the existing
        send retry pipeline appends a `SEND_NEW_MESSAGE` attempt row.
      - *Stub mode:* the stub edit fails, the coordinator falls back
        to send, the stub send also fails, and a `FAILED`
        `SEND_NEW_MESSAGE` attempt row is appended for the transition.
- [ ] For callback-triggered transitions (Phase 173/175), an
      `answerCallbackQuery` toast appears for the operator who clicked
      the inline button.
- [ ] `GET /api/admin/delivery-observability/details?...` returns the
      expected attempt count for the branch above — **one** row in the
      edit-success branch, **two** rows in the edit-fallback branch
      (real mode), and a row per failed dispatch in stub mode.
- [ ] Application logs contain no token substrings — token sanitization
      (Phase 158) is intact.

If every box is ticked, the MVP demo path is verified end-to-end.

---

## 13. Phase 179 manual smoke checklist — real Telegram mode

This section walks an operator through verifying the **Phase 179
edit-first / send-as-fallback** behavior on a real bot, branch by
branch. It complements Section 12: where Section 12 verifies the
end-to-end MVP path, this section verifies the specific Phase 179
dispatch decisions, the **observability caveat that successful edits
do not persist `EDIT_MESSAGE` attempt rows**, and the
**active-card identity invariant** (the canonical card is derived from
the latest `DELIVERED` `SEND_NEW_MESSAGE` row in
`telegram_delivery_attempt`, never from the clicked Telegram message
identity).

> **Background invariant.** Phase 179 intentionally does not persist
> `EDIT_MESSAGE` attempt rows. A successful edit therefore leaves
> `telegram_delivery_attempt` unchanged — the underlying
> `SEND_NEW_MESSAGE` row remains the active-card seed for the next
> transition. The observable signals for a successful edit are
> (a) the Telegram card visibly updates in place and (b) the
> coordinator log line emitted by
> `com.engops.platform.telegram.TelegramCardRefreshDispatchService`.
>
> The coordinator log shape (INFO level) is exactly:
>
> ```
> Telegram card refresh dispatch outcome=<OutcomeCategory> tenantId=<uuid>
>   workItemId=<uuid> resultType=<null|SUCCESS|REJECTED|FAILED>
>   error=<null|RATE_LIMIT|NETWORK_ERROR|INVALID_REQUEST|UNKNOWN_ERROR>
>   exceptionType=<null|class simple name>
> ```
>
> `<OutcomeCategory>` is one of the enum values in
> `TelegramCardRefreshDispatchService.OutcomeCategory`:
> `SKIPPED_BAD_INPUT`, `RENDERER_THREW_SWALLOWED`, `EDITED`,
> `NOT_MODIFIED`, `EDIT_REJECTED_FALLBACK_SEND`,
> `EDIT_RATE_LIMIT_FALLBACK_SEND`, `EDIT_NETWORK_FALLBACK_SEND`,
> `EDIT_FAILED_FALLBACK_SEND`, `EDIT_NULL_RESULT_FALLBACK_SEND`,
> `REFRESH_THREW_FALLBACK_SEND`.

### 13.1 Branch A — `EDITED` (happy edit-first path)

This is the most common branch in real mode after Phase 179.

**Preconditions:**

- `TELEGRAM_BOT_TOKEN` is set; the application has been restarted.
- Tenant routing (chat binding, topic binding, routing rule) is
  configured per Section 6.
- An intake has already produced a `DELIVERED` `SEND_NEW_MESSAGE` row
  for the work item (Section 7 has been executed; Card #1 is visible
  in the chat / topic).

**Operator action:**

1. Transition the same work item from `BUGS` → `PROCESSING` per
   Section 8 (either via the admin HTTP API or, equivalently, by
   pressing the **Start Processing** inline button — Phase 173/175
   path).

**Expected Telegram-visible result:**

- **Card #1 updates in place.** Its status line / title / inline
  keyboard reflect the new `PROCESSING` state.
- **No new Telegram message appears.** The chat does not gain a
  Card #2.

**Expected delivery observability:**

- `GET /api/admin/delivery-observability/details?tenantId=…&workItemCode=…&historyLimit=10`
  returns:
  - `latestMetrics.deliveryOutcome = "DELIVERED"` (unchanged).
  - `recentAttempts` contains exactly the **one** row from the
    intake step. The transition added no row — successful edits are
    not persisted.
- `latestMetrics.externalMessageId` still points at the intake card's
  Telegram `message_id` (the edit reused that id).

**Expected coordinator log line:**

```
Telegram card refresh dispatch outcome=EDITED tenantId=<tenant> workItemId=<wi>
  resultType=SUCCESS error=null exceptionType=null
```

**Token safety check:**

- Tail the application log for the test window. Confirm that no log
  line contains:
  - the configured `TELEGRAM_BOT_TOKEN` substring,
  - the request URL with `/bot{token}/…`,
  - the rendered card `text`,
  - any inbound `callback_data` value,
  - any raw exception message from Telegram.

### 13.2 Branch B — `NOT_MODIFIED` (benign no-op, diagnostic)

This branch is **hard to trigger manually** in the demo because the
seeded MVP Bug Flow status transitions always change the rendered
status line. Treat this section as a **diagnostic interpretation
guide**, not a mandatory manual step.

**When it occurs:**

- Telegram's `editMessageText` returns
  `{"ok":false,"description":"Bad Request: message is not modified"}`
  (or any description containing `"message is not modified"`
  case-insensitively). This happens when the new card text and inline
  keyboard are byte-equivalent to the current Telegram message
  content.

**Expected behavior:**

- **No new Telegram message is sent.** The coordinator treats this as
  a benign no-op (the card already shows the desired state).
- **No new attempt row** is added to `telegram_delivery_attempt`.
- The coordinator log line is:

  ```
  Telegram card refresh dispatch outcome=NOT_MODIFIED tenantId=<tenant>
    workItemId=<wi> resultType=REJECTED error=INVALID_REQUEST exceptionType=null
  ```

**Why this branch is safe:**

- Suppressing the fallback send avoids appending a duplicate-looking
  card to the chat for a re-rendered identical projection.
- Authorization and the strict state-machine remain authoritative
  (Phase 173); a button press that produced `NOT_MODIFIED` did so
  *after* the transition committed successfully — the UX is
  consistent with the persisted state.

**Token safety check:** same as Branch A.

### 13.3 Branch C — `EDIT_REJECTED_FALLBACK_SEND` (fallback send after edit failure)

This is the most useful manually-triggered failure branch. It proves
that the system self-heals when the active-card seed is no longer
editable.

**Preconditions:**

- Same as Branch A (real mode; routing configured; intake card
  delivered).

**Operator action:**

1. In the Telegram client, **manually delete Card #1** (long-press →
   Delete for everyone, or use a bot-admin workflow your environment
   allows).
2. Transition the work item from `BUGS` → `PROCESSING` (admin HTTP
   API or inline button).

**Expected Telegram-visible result:**

- The edit attempt fails because the message no longer exists
  Telegram-side. The coordinator falls back to the existing send
  retry pipeline.
- **A new card (Card #2) appears** in the same chat / topic.

**Expected delivery observability:**

- `GET /api/admin/delivery-observability/details?...` now returns:
  - `recentAttempts` contains **two** `SEND_NEW_MESSAGE` rows:
    the original intake row and the new fallback-send row from the
    transition.
  - `latestMetrics.deliveryOutcome = "DELIVERED"` and
    `latestMetrics.externalMessageId` points at the new fallback-send
    message id.

**Expected coordinator log line:**

```
Telegram card refresh dispatch outcome=EDIT_REJECTED_FALLBACK_SEND
  tenantId=<tenant> workItemId=<wi>
  resultType=REJECTED error=INVALID_REQUEST exceptionType=null
```

**Why the active-card seed remains stable:**

- The new fallback-send adds a `DELIVERED` `SEND_NEW_MESSAGE` row.
- `findLatestDeliveredSendMessage(tenantId, workItemId)` (Phase 177)
  now returns this newer row, so the **next** transition's edit-first
  attempt will target the new card. No drift, no orphan seeds.

**Token safety check:** same as Branch A.

### 13.4 Branch D — diagnostic transient failures (`EDIT_RATE_LIMIT_FALLBACK_SEND` / `EDIT_NETWORK_FALLBACK_SEND` / `EDIT_FAILED_FALLBACK_SEND` / `EDIT_NULL_RESULT_FALLBACK_SEND` / `REFRESH_THREW_FALLBACK_SEND`)

This branch is **not a mandatory manual demo step.** Do not force
Telegram-side rate limits or network failures during a demo run.
Treat the table below as a diagnostic interpretation guide for log
analysis after the fact.

| Coordinator log `outcome` | What happened | Operator-visible result |
|---|---|---|
| `EDIT_RATE_LIMIT_FALLBACK_SEND` | Telegram returned HTTP 429 on the edit | Coordinator fell back to send; the existing `TelegramCardDispatchRetryingService` then handles its own RATE_LIMIT retry policy on the fallback send. Operator may see one or more SEND attempt rows. |
| `EDIT_NETWORK_FALLBACK_SEND` | Telegram returned 5xx, timeout, or connection error on the edit | Same fallback behavior; SEND retry pipeline owns retries. |
| `EDIT_FAILED_FALLBACK_SEND` | Telegram returned `UNKNOWN_ERROR` (parse failure, unexpected runtime, or `INVALID_REQUEST` classified as `FAILED`) on the edit | Fallback send; SEND retry pipeline applies its existing policy. |
| `EDIT_NULL_RESULT_FALLBACK_SEND` | Edit gateway returned a `null` result (defensive) | Fallback send. |
| `REFRESH_THREW_FALLBACK_SEND` | `TelegramCardRefreshService.refresh(...)` threw an unexpected `RuntimeException` | Fallback send; the refresh service's own internal exception was swallowed by the coordinator (`exceptionType` log field is populated with the class simple name). |

In all five sub-cases:

- The coordinator does **not** retry the edit. Single-shot edit only.
- The fallback `TelegramCardDispatchRetryingService.dispatchWithRetry(cardView)`
  uses the same retry/backoff policy it has had since Phase 168 for
  `RATE_LIMIT` and `NETWORK_ERROR`. Each retry persists its own
  `SEND_NEW_MESSAGE` attempt row.
- The webhook HTTP behavior is unchanged: 401 only for invalid
  secret; everything else returns 200 so Telegram does not retry-loop.

**Token safety check:** confirm that, even on failure paths, no log
line carries the bot token, the URL with token, the rendered text,
or callback_data.

### 13.5 Branch E — stub mode

This branch lets operators run the smoke flow without sending real
Telegram traffic. It is also useful in CI / pre-prod where the bot
token is intentionally absent.

**Preconditions:**

- `TELEGRAM_BOT_TOKEN` is **unset** (or blank).
- The application has been restarted.
- `StubTelegramOutboundGateway` is therefore the active
  `TelegramOutboundGateway` bean.

**Operator action:**

1. Submit an intake per Section 7.
2. Transition the work item per Section 8.

**Expected behavior:**

- The stub `editMessageText` returns a structured failure with
  `failure_code = UNKNOWN_ERROR` and
  `failure_reason = "Telegram outbound gateway hali implement qilinmagan"`.
- The coordinator falls back to the existing send pipeline.
- The stub `sendMessage` also returns a structured failure.
- **No real Telegram traffic is generated.**

**Expected delivery observability:**

- `recentAttempts` contains one `FAILED` `SEND_NEW_MESSAGE` row per
  dispatch event (intake produces one, transition produces one).
  Each row has:
  - `delivery_outcome = "FAILED"`,
  - `failure_code = "UNKNOWN_ERROR"`,
  - `failure_reason = "Telegram outbound gateway hali implement qilinmagan"`.
- The intake's row is added by the existing intake → send retry
  pipeline. The transition's row is added by the coordinator's
  fallback send branch (the stub edit failed first).

**Expected coordinator log lines** (one per dispatch event):

```
Telegram card refresh dispatch outcome=EDIT_FAILED_FALLBACK_SEND
  tenantId=<tenant> workItemId=<wi>
  resultType=FAILED error=UNKNOWN_ERROR exceptionType=null
```

**Token safety check:**

- No token exists in stub mode, so token leakage cannot occur from
  the gateway. The bounded log shape must nevertheless show no
  rendered text, no callback_data, and no exception messages.

### 13.6 Branch F — callback-triggered transition (Phase 173 / 175 path)

This branch verifies that the **operator-clicked card identity is
not used as the active-card identity for refresh.** Authorization
comes from Phases 173/175; the canonical card is DB-derived (Phase
177).

**Preconditions:**

- Real Telegram mode (Branch A preconditions).
- The intake card (Card #1) is visible in the chat and carries the
  inline keyboard rendered by `TelegramActionAssembler`.

**Operator action:**

1. In the Telegram chat, **press the "Start Processing" inline
   button** on Card #1. This produces a `callback_query` to
   `POST /api/telegram/webhook`.

**Expected webhook behavior (Phase 171 / 173 / 175):**

- The webhook validates `X-Telegram-Bot-Api-Secret-Token` and
  returns `200 OK` for the callback (always, by design).
- `TelegramCallbackQueryService` parses
  `<UUID workItemId>:<ACTION_CODE>` data.
- `intake.TelegramCallbackActionExecutionService` resolves the
  Telegram user to a platform `AppUser`, derives the tenant
  server-side from the `WorkItem`, enforces ACTIVE membership and
  `WORK_ITEM_TRANSITION` permission, and invokes
  `WorkflowTransitionService.transition(...)` with
  `actionSource = "TELEGRAM_CALLBACK"`.
- `TelegramCallbackAcknowledgementService` (Phase 175) emits a
  bounded `answerCallbackQuery` toast back to the operator —
  e.g. *"Action applied."* for `EXECUTED`. The toast appears in the
  Telegram client briefly.

**Expected after-commit behavior (Phase 179):**

- The AFTER_COMMIT listener fires the coordinator. The coordinator
  resolves the active-card seed from
  `findLatestDeliveredSendMessage(tenantId, workItemId)` — **not**
  from `callback_query.message.chat.id` or
  `callback_query.message.messageId`. The clicked-card identity is
  never treated as authoritative.
- In real mode with the prior intake send delivered, the coordinator
  edits the canonical latest card (which is the intake card in this
  demo). Branch A's observability invariants apply: card updates in
  place, no new row.

**Why this is safe:**

- A confused operator who scrolls back and clicks an **old** card
  cannot trick the platform into editing the wrong card. The
  workflow transition still runs server-side under Phase 173
  authorization; the refresh still targets the canonical latest
  delivered card; the click identity influences only the
  `answerCallbackQuery` toast destination (which Telegram routes
  back to the clicker, not the chat).

**Token safety check:** same as Branch A.

### 13.7 Phase 179 smoke success checklist

A complete Phase 179 manual smoke run should tick the following boxes
for **at least Branches A, C, E, and F**. Branches B and D are
diagnostic and are not required for a green smoke run.

- [ ] **Branch A (`EDITED`)** — original card updates in place; no
      new Telegram message; `recentAttempts` count unchanged after
      transition; coordinator log shows `outcome=EDITED`.
- [ ] **Branch C (`EDIT_REJECTED_FALLBACK_SEND`)** — Card #1 deleted
      Telegram-side; transition produces Card #2 via fallback send;
      new `SEND_NEW_MESSAGE` row appears in `recentAttempts`;
      coordinator log shows
      `outcome=EDIT_REJECTED_FALLBACK_SEND`.
- [ ] **Branch E (stub mode)** — token unset; no real Telegram
      traffic; one `FAILED` `SEND_NEW_MESSAGE` row per dispatch
      event; coordinator log shows `outcome=EDIT_FAILED_FALLBACK_SEND`.
- [ ] **Branch F (callback-triggered)** — `answerCallbackQuery` toast
      appears; clicked-card identity is **not** used as active-card
      identity; refresh targets the canonical latest delivered SEND;
      Branch A observability invariants apply.
- [ ] Application log contains no bot-token substring, no
      `/bot{token}/…` URL, no rendered card text, no
      `callback_data` value, no raw Telegram exception message.

If every required box ticks, the Phase 179 edit-first /
send-as-fallback path is verified end-to-end on real Telegram and in
stub mode.
