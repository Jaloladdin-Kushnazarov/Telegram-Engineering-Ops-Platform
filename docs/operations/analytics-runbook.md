# Analytics Runbook

> Authoritative operator-facing guide for the read-only analytics
> aggregate endpoints introduced in Phase 205. Covers REST contract,
> example queries, bucket ordering invariant, SQL equivalents, and
> known limitations.

---

## 1. Overview

Three read-only REST endpoints introduced in Phase 205, all under
`/api/analytics/work-items/`:

- `GET /api/analytics/work-items/by-status?tenantId={uuid}`
- `GET /api/analytics/work-items/by-type?tenantId={uuid}`
- `GET /api/analytics/work-items/by-severity?tenantId={uuid}`

All three share a uniform response shape (see §3.2): `tenantId`,
`totalCount`, and a sorted `buckets[]` array.

Bucket ordering is deterministic: **count DESC, label ASC tie-break**
(§5). Operators and any UI built on top of these endpoints can rely on
stable ordering across successive calls.

Authorization reuses the existing **`TENANT_CONFIG_READ`** permission
via `AdminAuthorizationService.authorizeRead(tenantId, actorUserId)`.
This is a deliberate Phase 205 deviation from the original prescription
which referenced a `WORK_ITEM_READ` permission — that permission does
not exist in the V2 / V6 catalog. The platform-wide read-facade pattern
(used by `WorkItemSummaryReadFacade`, `WorkItemDetailsReadFacade`,
delivery-observability facades, and others) is followed.

Read-only path: no `audit_event` row is written for these queries
(matches the existing project convention for query services). No bot
command. No pagination — the number of distinct status / type /
severity values per tenant is bounded by the small enum-like catalog
(~20 values).

---

## 2. Prerequisites

Before a call will succeed, all of:

- **Bearer token** — the caller has a valid platform JWT.
- **ACTIVE membership + permission** — the JWT `sub` resolves to an
  `AppUser` who has at least one **ACTIVE** `Membership` in the target
  tenant, and the membership's role(s) include the
  **`TENANT_CONFIG_READ`** permission (catalog UUID
  `a0000000-0000-0000-0000-00000000000c`, seeded in V6).
- **Seeded role bindings** (V6 inspection result):
  - `ADMIN` — bound (full-permission role; receives all 13 catalog
    permissions including `TENANT_CONFIG_READ`).
  - `ENGINEER` — bound.
  - `TESTER` — bound.
  - `VIEWER` — bound.
- **Stateless caller** — every request is independent. No pagination
  cursor, no session.

---

## 3. REST endpoint contract

### 3.1 Request

Each of the three endpoints takes a single required query parameter:

| Parameter  | Type | Required | Notes                                                                |
| ---------- | ---- | -------- | -------------------------------------------------------------------- |
| `tenantId` | UUID | yes      | Target tenant. Missing → 400; malformed UUID → 400; null (service-level) → 422. |

No request body. `GET` only.

### 3.2 Response (200 OK)

The response shape is **uniform across all three endpoints**:

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "totalCount": 39,
  "buckets": [
    {"label": "RESOLVED", "count": 23},
    {"label": "REPORTED", "count": 12},
    {"label": "IN_PROGRESS", "count": 4}
  ]
}
```

Contract:

- `totalCount` is the sum of the `count` values across all returned
  buckets.
- Empty tenant (no matching work items): `totalCount = 0`, `buckets = []`.
- For **by-severity**: rows whose `severity_code IS NULL` are silently
  excluded (Phase 205 D2 invariant). A work item created without a
  severity hint (e.g. through `POST /api/intake/work-items` without
  `severityCode`) will not appear in any by-severity bucket. Use the
  §6.3 follow-up SQL query to count NULL-severity items separately
  until a future phase exposes that count via the API.
- For **by-type**: buckets carry the `WorkItemType` enum names —
  `BUG`, `INCIDENT`, `TASK` (Phase 205 service casts the enum to
  string via JPQL `CAST(... AS string)`).

### 3.3 Error code → HTTP status

The platform's `GlobalExceptionHandler` maps every `BusinessRuleException`
to HTTP 422 with the standard `ApiErrorResponse` envelope (fields:
`errorCode`, `message`, `timestamp`, `correlationId`, `path`).
`AccessDeniedException` maps to HTTP 403. Missing / invalid JWT maps
to HTTP 401.

| `errorCode`           | HTTP | When                                                |
| --------------------- | ---- | --------------------------------------------------- |
| `INVALID_TENANT_ID`   | 422  | `tenantId` resolved as null at the service layer    |
| `ACCESS_DENIED`       | 403  | caller lacks `TENANT_CONFIG_READ` in target tenant  |
| (missing `tenantId`)  | 400  | required query parameter absent (Spring binding)    |
| (malformed UUID)      | 400  | `tenantId` is not a valid UUID string               |
| (no / invalid JWT)    | 401  | bearer token missing or unverifiable                |

---

## 4. Example queries

Resolve the tenant UUID once and reuse:

```bash
export TENANT_ID=$(psql -tA -c "SELECT id FROM tenant WHERE slug='acme';")
export PLATFORM_BASE="https://platform.internal"
```

### 4.1 by-status

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$PLATFORM_BASE/api/analytics/work-items/by-status?tenantId=$TENANT_ID" \
  | jq .
```

Sample 200 response (a tenant with a small bug + incident mix):

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "totalCount": 39,
  "buckets": [
    {"label": "RESOLVED", "count": 23},
    {"label": "REPORTED", "count": 12},
    {"label": "IN_PROGRESS", "count": 4}
  ]
}
```

### 4.2 by-type

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$PLATFORM_BASE/api/analytics/work-items/by-type?tenantId=$TENANT_ID" \
  | jq .
```

Sample 200 response:

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "totalCount": 39,
  "buckets": [
    {"label": "BUG", "count": 21},
    {"label": "INCIDENT", "count": 12},
    {"label": "TASK", "count": 6}
  ]
}
```

### 4.3 by-severity

```bash
curl -s -H "Authorization: Bearer $DEMO_JWT" \
  "$PLATFORM_BASE/api/analytics/work-items/by-severity?tenantId=$TENANT_ID" \
  | jq .
```

Sample 200 response (note: `totalCount` here is lower than §4.1 and
§4.2 because NULL-severity rows are excluded):

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "totalCount": 27,
  "buckets": [
    {"label": "HIGH", "count": 11},
    {"label": "CRITICAL", "count": 8},
    {"label": "MEDIUM", "count": 6},
    {"label": "LOW", "count": 2}
  ]
}
```

> **Callout — NULL severity:** work items with `severity_code IS NULL`
> are **excluded** from this aggregate. To see the count of
> unassigned-severity items, use the direct SQL follow-up in §6.3
> until a future phase exposes it via the API.

---

## 5. Bucket ordering invariant

The Phase 205 service applies a deterministic `Comparator<AnalyticsBucket>`
to every result before returning it:

- Buckets are sorted by **`count` DESC** (highest count first).
- Ties on `count` are broken by **`label` ASC** (alphabetical,
  case-sensitive, default Java `String.compareTo`).

Worked example. Repository returns rows in any order; service yields
the canonical ordering:

| Input (repository order)               | Output (service-sorted)                |
| -------------------------------------- | -------------------------------------- |
| `[{ZEBRA:5}, {APPLE:5}, {MANGO:8}, {BANANA:5}]` | `[{MANGO:8}, {APPLE:5}, {BANANA:5}, {ZEBRA:5}]` |
| `[{LOW:1}, {HIGH:7}, {CRITICAL:3}, {MEDIUM:7}]` | `[{HIGH:7}, {MEDIUM:7}, {CRITICAL:3}, {LOW:1}]` |

This ordering is **stable across calls** and **independent of database
row order** — guaranteed by the service layer's comparator, not by the
repository query (the repository's `GROUP BY` clause has no `ORDER BY`).

Why it matters for operators: a UI or chart renderer on top of these
endpoints can rely on consistent ordering between successive refreshes;
no "the chart looks different every time" surprise.

---

## 6. SQL equivalents (operator ad-hoc queries)

Operators who want to run the same aggregates directly against
PostgreSQL (e.g. when the platform is unavailable, for cross-tenant
rollups, or for time-windowed views the API doesn't expose) can use the
queries below. Column names match the V3 schema and the Phase 205
JPQL → SQL mapping exactly: `work_item.current_status_code`,
`work_item.type_code`, `work_item.severity_code`.

### 6.1 by-status

```sql
SELECT current_status_code AS label,
       COUNT(*)            AS count
  FROM work_item
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
 GROUP BY current_status_code
 ORDER BY count DESC, label ASC;
```

### 6.2 by-type

```sql
SELECT type_code AS label,
       COUNT(*)  AS count
  FROM work_item
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
 GROUP BY type_code
 ORDER BY count DESC, label ASC;
```

### 6.3 by-severity (NULL excluded, matching the API)

```sql
SELECT severity_code AS label,
       COUNT(*)      AS count
  FROM work_item
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
   AND severity_code IS NOT NULL
 GROUP BY severity_code
 ORDER BY count DESC, label ASC;
```

To see NULL-severity work items separately (count of items the API
silently excludes):

```sql
SELECT COUNT(*) AS unassigned_severity
  FROM work_item
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
   AND severity_code IS NULL;
```

For the broader audit-trail / verification-query pattern used by other
runbooks, see
[`tenant-onboarding-runbook.md` §6](tenant-onboarding-runbook.md#6-verification-queries-read-only).

---

## 7. Known limitations

- **No pagination.** The number of distinct status / type / severity
  values per tenant is bounded (~20), so all buckets fit in one
  response. If a tenant's schema ever drifts to thousands of distinct
  values, the response stays semantically correct but UI rendering may
  degrade.
- **No date-range / time-window filter.** Aggregates are all-time. For
  "last 24 hours," use a direct SQL query with
  `AND created_at > now() - INTERVAL '24 hours'`.
- **No custom dimensions.** `by-owner`, `by-tag`, `by-environment`,
  `by-source-service` and similar are not exposed. Future phases may
  add them.
- **No bot command.** Phase 206+ may add `/analytics` or similar.
- **No dashboard UI.** Phase 209+ (web UI) will add a renderer; until
  then operators consume via `curl + jq`.
- **No granular `ANALYTICS_READ` permission.** `TENANT_CONFIG_READ` is
  reused; any operator with config-read also gets analytics-read. A
  finer-grained permission is a future phase candidate.
- **No real-time stream.** Snapshot-only; no SSE / WebSocket push.
- **`bucket.count` is wire-typed as JSON number.** A tenant with more
  than 2³¹ items in a single bucket would overflow JavaScript's number
  precision (`Number.MAX_SAFE_INTEGER` is 2⁵³ − 1). Practically
  impossible at MVP scale; documented for completeness.

---

## 8. Troubleshooting

The three most common operator-facing situations:

- **Empty response (`totalCount: 0`, `buckets: []`).** The tenant has
  no work items in that dimension. For **by-severity**, this can also
  mean every work item in the tenant has `severity_code IS NULL`.
  Verify with the §6.3 SQL query plus its NULL-severity follow-up. If
  by-status / by-type are also empty, the tenant likely has no work
  items at all — verify with
  `SELECT COUNT(*) FROM work_item WHERE tenant_id = ...`.

- **`403 ACCESS_DENIED`.** The caller's role lacks
  `TENANT_CONFIG_READ` in the target tenant. Fix:
  - An existing `ADMIN` of that tenant grants the caller a role that
    already includes `TENANT_CONFIG_READ` (`ADMIN` / `ENGINEER` /
    `TESTER` / `VIEWER` all do, per V6 seed); **or**
  - An admin attaches `TENANT_CONFIG_READ` to the caller's existing
    role via the admin write endpoints. Verify with:
    `SELECT r.code FROM role r JOIN role_permission rp ON rp.role_id = r.id JOIN permission p ON p.id = rp.permission_id WHERE p.code = 'TENANT_CONFIG_READ';`.

- **`400` missing `tenantId` / malformed UUID.** The query string is
  malformed. The endpoint requires `?tenantId=<uuid>`; no JSON body
  is accepted (it's a `GET`). Verify with the §4 curl examples.

---

> Cross-references:
> - End-to-end smoke checklist:
>   [`demo-smoke-runbook.md` §17](demo-smoke-runbook.md#17-analytics-smoke).
> - MVP completion overview:
>   [`mvp-completion-runbook.md` §2](mvp-completion-runbook.md#2-capability-snapshot--phase-185-foundation-plus-later-documented-additions).
> - Verification-query pattern used by sibling runbooks:
>   [`tenant-onboarding-runbook.md` §6](tenant-onboarding-runbook.md#6-verification-queries-read-only),
>   [`error-ingestion-runbook.md` §7](error-ingestion-runbook.md#7-verification-queries-read-only).
