# Tenant Onboarding Runbook

> Authoritative operator-facing guide for creating a new tenant on the
> Telegram Engineering Operations Platform. Covers both onboarding paths
> (REST endpoint + Telegram bot command) introduced through Phases
> 198/199/200/201.

---

## 1. Overview

Two paths are available for provisioning a new tenant:

- **(a) REST endpoint** — `POST /api/admin/tenants` (introduced in Phase 199).
  JWT-protected. Suited for automation, CI, and operator scripting.
- **(b) Telegram bot command** — `/onboard ...` (introduced in Phase 201).
  Convenient for an admin operating from a Telegram client.

Both paths converge on the same backend orchestrator
(`TenantOnboardingService`, Phase 199), wrapped in a single
`@Transactional` boundary. A failure mid-onboarding (slug conflict,
unknown workflow template, validation error) rolls back the entire
transaction — no partial tenant state.

Both paths require the **`TENANT_ONBOARD`** permission. The seeded
`ADMIN` role has it by default (V8 migration binds `TENANT_ONBOARD`
permission to the `ADMIN` role; V6 already binds the broader admin
permission set).

Both paths produce the **same audit trail** — at least 8 audit rows per
successful onboarding (more if multiple workflow templates are seeded
or a new `AppUser` is created). The bot path adds one extra row at the
top (`TELEGRAM_BOT_COMMAND_EXECUTED`). See §5 for the full list.

---

## 2. Prerequisites

Before either path will succeed, the operator must satisfy all of:

- The operator already exists as an **`AppUser`** in the platform, with
  **at least one ACTIVE membership** in some existing tenant. This is
  the chicken-and-egg solver for "you cannot yet be a member of the
  tenant you are creating" — authorization is checked globally across
  any of the operator's existing ACTIVE memberships
  (`OperationalAuthorizationService.authorizeGlobal`, Phase 199 D4).
- The operator's role in that existing tenant must include
  **`TENANT_ONBOARD`** permission. The seeded `ADMIN` role has it
  (V8 migration). If the operator's only role is `ENGINEER` / `TESTER`
  / `VIEWER`, onboarding will return `403 ACCESS_DENIED`.
- The workflow template codes to be seeded must exist in the
  `workflow_template` catalog (Phase 198, V7 seed). The four
  system-seeded templates are: **`BUG_MINIMAL`**, **`BUG_FULL`**,
  **`INCIDENT_BASIC`**, **`TASK_BASIC`**. Custom templates are not
  yet supported.
- **For Path A (REST):** the operator has a valid JWT whose `sub` claim
  resolves to their `AppUser.id`.
- **For Path B (bot):** the operator's Telegram numeric user id is
  registered (their existing `AppUser.telegram_user_id`); their Telegram
  client has the bot in a chat.

---

## 3. Path A — REST endpoint (`POST /api/admin/tenants`)

The endpoint accepts a JSON body and returns `201 Created` on success.
The full request body shape is documented in
`TenantOnboardingRequest` (Phase 199).

### 3.1 Request body example

```json
{
  "tenantName": "Acme Corp",
  "tenantSlug": "acme",
  "tenantTimezone": "Asia/Tashkent",
  "adminTelegramUserId": 123456789,
  "adminDisplayName": "Demo Admin",
  "adminUsername": "demo_admin",
  "workflowTemplateCodes": ["BUG_MINIMAL", "TASK_BASIC"]
}
```

Field notes:

- `tenantTimezone` is optional. If omitted or blank, the server defaults
  to `"UTC"`.
- `adminUsername` is optional (the Telegram `@handle` without the `@`).
- `workflowTemplateCodes` must contain at least one code; maximum 10.

### 3.2 Response body example

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "tenantSlug": "acme",
  "tenantName": "Acme Corp",
  "createdAt": "2026-05-23T08:00:00Z",
  "adminAppUserId": "22222222-2222-2222-2222-222222222222",
  "adminMembershipId": "33333333-3333-3333-3333-333333333333",
  "workflowDefinitions": [
    {
      "workflowDefinitionId": "55555555-5555-5555-5555-555555555555",
      "templateCode": "BUG_MINIMAL",
      "workItemType": "BUG"
    },
    {
      "workflowDefinitionId": "66666666-6666-6666-6666-666666666666",
      "templateCode": "TASK_BASIC",
      "workItemType": "TASK"
    }
  ]
}
```

The `Location` response header points at the future
`GET /api/admin/tenants/{tenantId}` URL (the GET surface itself is not
implemented in this phase — the header is a stable forward-compatible
hint).

### 3.3 Error code → HTTP status

The platform's `GlobalExceptionHandler` (existing project pattern)
maps every `BusinessRuleException` to HTTP 422 with an
`ApiErrorResponse` envelope (fields: `errorCode`, `message`,
`timestamp`, `correlationId`, `path`). `AccessDeniedException` maps to
HTTP 403.

| `errorCode`                  | HTTP | When                                                          |
| ---------------------------- | ---- | ------------------------------------------------------------- |
| `SLUG_TAKEN`                 | 422  | `tenantSlug` already exists                                   |
| `UNKNOWN_WORKFLOW_TEMPLATE`  | 422  | requested template code not in `workflow_template` catalog    |
| `DUPLICATE_WORKFLOW_NAME`    | 422  | two requested templates resolve to the same workflow name     |
| `INVALID_SLUG`               | 422  | slug fails regex `^[a-z0-9]([a-z0-9-]*[a-z0-9])?$` or length  |
| `INVALID_TENANT_NAME`        | 422  | tenant name blank or > 200 chars                              |
| `INVALID_DISPLAY_NAME`       | 422  | admin display name blank or > 200 chars                       |
| `INVALID_TELEGRAM_USER_ID`   | 422  | `adminTelegramUserId` ≤ 0 or null                             |
| `NO_TEMPLATES_REQUESTED`     | 422  | `workflowTemplateCodes` empty                                 |
| `TOO_MANY_TEMPLATES`         | 422  | `workflowTemplateCodes` size > 10                             |
| `ACCESS_DENIED`              | 403  | actor lacks `TENANT_ONBOARD` in any active membership         |
| (missing body / bad JSON)    | 400  | Spring deserialization failure                                |

### 3.4 `curl` example

```bash
curl -s -X POST \
  -H "Authorization: Bearer $DEMO_JWT" \
  -H 'Content-Type: application/json' \
  "$DEMO_BASE/api/admin/tenants" \
  -d '{
        "tenantName": "Acme Corp",
        "tenantSlug": "acme",
        "tenantTimezone": "Asia/Tashkent",
        "adminTelegramUserId": 123456789,
        "adminDisplayName": "Demo Admin",
        "workflowTemplateCodes": ["BUG_MINIMAL"]
      }'
```

The JWT must be a valid platform JWT (see
[`demo-smoke-runbook.md` §5](demo-smoke-runbook.md#5-jwt-preparation)
for token minting).

---

## 4. Path B — Telegram bot command (`/onboard`)

The operator types `/onboard` in any chat the bot is in. The webhook
parses the message, the dispatcher routes it to `OnboardCommand`
(Phase 201), and the bot replies in the same chat.

### 4.1 Syntax

```
/onboard <slug> "<tenant_name>" <admin_telegram_user_id> "<admin_display_name>" <template_code_1> [<template_code_2> ...]
```

Quoting rules (handled by `TokenizedBotCommandArguments`, Phase 201
D2):

- Tokens are separated by whitespace (space, tab, newline).
- Double quotes group multi-word values: `"Acme Corp"` is one token.
- Single quotes have **no special meaning** (literal — `it's` is one
  token).
- Inside double quotes, backslash escapes the next character: `\"` →
  `"`, `\\` → `\`.
- Unmatched double quote produces the reply:
  `❌ Argumentlarda xatolik: Tugallanmagan qo'sh tirnoq`.

### 4.2 Example — success path (single template)

Operator types:

```
/onboard acme "Acme Corp" 123456789 "Demo Admin" BUG_MINIMAL
```

Bot replies:

```
✅ Tenant yaratildi:
Slug: acme
Tenant ID: 11111111-1111-1111-1111-111111111111
Admin user ID: 22222222-2222-2222-2222-222222222222
Workflows: 1 ta (BUG_MINIMAL)
```

### 4.3 Example — success path (multi-template)

Operator types:

```
/onboard widgets "Widgets Inc" 987654321 "Alice Wonder" BUG_MINIMAL TASK_BASIC INCIDENT_BASIC
```

Bot replies (UUIDs are illustrative):

```
✅ Tenant yaratildi:
Slug: widgets
Tenant ID: ...
Admin user ID: ...
Workflows: 3 ta (BUG_MINIMAL, TASK_BASIC, INCIDENT_BASIC)
```

### 4.4 Example — slug already taken

Operator types:

```
/onboard acme "Another Tenant" 555111222 "Bob Builder" BUG_MINIMAL
```

Bot replies:

```
❌ Bu slug allaqachon band: 'acme'
```

### 4.5 Example — access denied

If the operator's account lacks `TENANT_ONBOARD` in any of its ACTIVE
memberships, the service raises `AccessDeniedException`. The bot
replies:

```
❌ Sizda TENANT_ONBOARD ruxsati yo'q.
```

### 4.6 Example — unknown template

```
/onboard acme "Acme Corp" 123456789 "Demo Admin" NO_SUCH_TEMPLATE
```

Bot replies:

```
❌ Noma'lum workflow shabloni: 'NO_SUCH_TEMPLATE'
```

### 4.7 Known limitations (bot path)

The bot path has two flags that are **not** exposed in Phase 201 and
default to the same values the server would assign anyway:

- **Timezone:** the bot sends `tenantTimezone = null`, the server
  defaults to `"UTC"`. To set a non-default timezone, use the REST path
  (Phase 201 D3).
- **Admin Telegram `@handle`:** the bot sends `adminUsername = null`.
  To set a non-null username, use the REST path.

Additional bot-path limitations are listed in §7.

---

## 5. Audit trail per successful onboarding

Every successful onboarding writes a deep audit trail. The table below
lists the rows in emission order. The full set is reproducible against
the `audit_event` table.

| `event_type`                       | `entity_type`             | `action_source`     | Source phase (inline traceability)         |
| ---------------------------------- | ------------------------- | ------------------- | ------------------------------------------ |
| `TELEGRAM_BOT_COMMAND_EXECUTED`    | `APP_USER`                | `TELEGRAM_COMMAND`  | Phase 200 (only for bot path)              |
| `CREATED`                          | `TENANT`                  | `ADMIN_API`         | Phase 199 (via `TenantConfigCommandService.createTenant`) |
| `TENANT_CREATED`                   | `TENANT`                  | `ADMIN_API`         | Phase 199 (onboarding-level)               |
| `CREATED`                          | `APP_USER`                | `ADMIN_API`         | Phase 199 (only if new `AppUser`)          |
| `CREATED`                          | `MEMBERSHIP`              | `ADMIN_API`         | Phase 199                                  |
| `ADMIN_MEMBERSHIP_CREATED`         | `MEMBERSHIP`              | `ADMIN_API`         | Phase 199 (onboarding-level)               |
| `CREATED`                          | `MEMBERSHIP_ROLE_BINDING` | `ADMIN_API`         | Phase 199                                  |
| `CREATED` × N                      | `WORKFLOW_DEFINITION`     | `ADMIN_API`         | Phase 199 (one per template seeded)        |
| `CREATED` × ΣK                     | `WORKFLOW_STATUS`         | `ADMIN_API`         | Phase 199 (one per status across templates) |
| `CREATED` × ΣT                     | `WORKFLOW_TRANSITION_RULE`| `ADMIN_API`         | Phase 199 (one per transition across templates) |
| `WORKFLOW_SEEDED` × N              | `WORKFLOW_DEFINITION`     | `ADMIN_API`         | Phase 199 (onboarding-level, one per template) |

Notes:

- The bot path adds exactly **one** extra row at the top
  (`TELEGRAM_BOT_COMMAND_EXECUTED`) via Phase 200's
  `recordEventInNewTransaction` (REQUIRES_NEW). All other rows are
  written inside the Phase 199 `@Transactional` boundary
  (MANDATORY propagation) and either all commit together or all roll
  back together.
- Existing `CREATED` rows (Phase 199 reused from
  `TenantConfigCommandService` / `IdentityCommandService`) carry
  `actor_user_id = NULL`. Onboarding-level rows
  (`TENANT_CREATED`, `ADMIN_MEMBERSHIP_CREATED`, `WORKFLOW_SEEDED`)
  carry the requesting operator's `appUserId` as `actor_user_id`.
- For BUG_MINIMAL alone: N = 1, ΣK = 4 statuses (BUGS / PROCESSING /
  TESTING / FIXED), ΣT = 5 transitions. Minimum audit count
  (REST path, existing `AppUser`): 1 + 1 + 1 + 1 + 1 + 1 + 4 + 5 + 1
  = 15 rows.

---

## 6. Verification queries (read-only)

After a successful onboarding, the operator can confirm each layer
landed correctly by querying the platform's PostgreSQL database. The
column names below match the real schema (V1–V8).

### 6.1 Tenant row exists

```sql
SELECT id, slug, name, timezone, status, created_at
  FROM tenant
 WHERE slug = 'acme';
```

Expected: exactly one row with `status = 'ACTIVE'` and `timezone`
matching the request (or `'UTC'` if omitted).

### 6.2 Admin membership + ADMIN role binding

```sql
SELECT m.id            AS membership_id,
       au.id           AS app_user_id,
       au.display_name,
       au.telegram_user_id,
       m.status        AS membership_status,
       r.code          AS role_code
  FROM membership m
  JOIN app_user au
    ON au.id = m.user_id
  JOIN membership_role_binding mrb
    ON mrb.membership_id = m.id
  JOIN role r
    ON r.id = mrb.role_id
 WHERE m.tenant_id = (SELECT id FROM tenant WHERE slug = 'acme');
```

Expected: at least one row with `role_code = 'ADMIN'` and
`membership_status = 'ACTIVE'`.

### 6.3 Workflow definitions + statuses

```sql
SELECT wd.name            AS workflow_name,
       wd.work_item_type,
       ws.name            AS status_name,
       ws.status_order,
       ws.initial,
       ws.terminal
  FROM workflow_definition wd
  JOIN workflow_status ws
    ON ws.workflow_definition_id = wd.id
 WHERE wd.tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
 ORDER BY wd.name, ws.status_order;
```

Expected: one row per (workflow, status) pair. For `BUG_MINIMAL`:
four statuses in order BUGS / PROCESSING / TESTING / FIXED, with
`initial = TRUE` on BUGS and `FALSE` elsewhere. (Note: the V2
`workflow_status` schema uses `name` for the status code and `initial`
for the initial flag; this differs from the Phase 198 template catalog
schema which uses `status_code` and `is_initial`. The values are
preserved 1:1 during seeding.)

### 6.4 Audit trail spot-check

```sql
SELECT occurred_at, event_type, entity_type, action_source, actor_user_id
  FROM audit_event
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
 ORDER BY occurred_at ASC;
```

Expected: the rows described in §5, in emission order.

---

## 7. Known limitations

- **Multi-tenant routing for bot operators:** the bot picks the **first
  ACTIVE membership** of the operator (Phase 200 D13). If the operator
  belongs to multiple tenants, there is no per-chat or per-command
  tenant selection yet. The REST path is unaffected (it does not
  consume the operator's tenant context; it creates a new one).
- **Bot path `--tz` flag:** not exposed via bot; server defaults to
  `"UTC"` (Phase 201 D3). Use REST path with `tenantTimezone` if
  another zone is needed.
- **Bot path `--username` flag:** not exposed via bot (Phase 201 D3).
  Use REST path with `adminUsername` if needed.
- **Rate limiting:** no per-operator rate limit on bot commands
  (Phase 200 D14). One operator can issue back-to-back `/onboard`
  commands; the service-layer slug uniqueness check is the only guard
  against accidental duplicate onboarding.
- **Interactive wizard:** `/onboard` is one-shot. No "are you sure?"
  confirmation and no multi-step state machine (Phase 201 D16).
- **Custom workflow templates:** only the four V7-seeded templates
  (`BUG_MINIMAL`, `BUG_FULL`, `INCIDENT_BASIC`, `TASK_BASIC`) are
  available. Tenant-owned custom templates are a future phase.
- **Telegram routing config:** a freshly onboarded tenant has **no**
  chat / topic / routing-rule rows. The operator must configure
  Telegram routing separately via the existing admin endpoints
  (`POST /api/admin/tenant-config/chat-bindings` etc.) AFTER
  onboarding (Phase 199 D12). Without this, intake events for the new
  tenant will have `routingPrepared: false`.

---

## 8. Troubleshooting

The three most common operator-facing errors:

- **`SLUG_TAKEN` (422 / "Bu slug allaqachon band: '...'")**
  - The chosen `tenantSlug` already maps to an existing tenant. Slugs
    are globally unique (V2 schema: `tenant.slug UNIQUE NOT NULL`).
  - Query to confirm:
    `SELECT slug, status FROM tenant WHERE slug = '<your-slug>';`.
  - Pick a different slug. There is no rename surface in this phase.

- **`UNKNOWN_WORKFLOW_TEMPLATE` (422 / "Noma'lum workflow shabloni: '...'")**
  - The requested template code is not in the `workflow_template`
    catalog. The four V7-seeded codes are: `BUG_MINIMAL`, `BUG_FULL`,
    `INCIDENT_BASIC`, `TASK_BASIC`.
  - Query to list available codes:
    `SELECT code, name, work_item_type FROM workflow_template ORDER BY code;`.

- **`ACCESS_DENIED` (403 REST / "Sizda TENANT_ONBOARD ruxsati yo'q.")**
  - The operator's existing-tenant role does not include
    `TENANT_ONBOARD`. The seeded `ADMIN` role does (V8 binds it).
  - An existing `ADMIN` of any tenant must either (a) grant the
    operator the `ADMIN` role in their current tenant, or (b) attach
    `TENANT_ONBOARD` to whatever existing role the operator already
    has, via the `POST /api/admin/tenant-config/role-permissions`
    endpoint.

---

> Cross-references:
> - End-to-end smoke checklist: see
>   [`demo-smoke-runbook.md` §15](demo-smoke-runbook.md#15-onboarding-smoke).
> - MVP completion overview: see
>   [`mvp-completion-runbook.md` §2](mvp-completion-runbook.md#2-capability-snapshot--phase-185-foundation-plus-later-documented-additions).
