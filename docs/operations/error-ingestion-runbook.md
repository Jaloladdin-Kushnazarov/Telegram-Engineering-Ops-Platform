# Error Ingestion Runbook

> Authoritative operator + SDK-integrator guide for the SDK-facing error
> ingestion endpoint introduced in Phase 203. Covers REST contract,
> client integration patterns, severity derivation, audit trail, and
> verification queries.

---

## 1. Overview

- `POST /api/intake/errors` is a **synchronous** REST endpoint introduced
  in Phase 203. SDKs / agents POST a structured error report; the
  platform creates exactly one `INCIDENT`-type `WorkItem` in the
  specified tenant and returns its identifiers.
- The endpoint is a thin adapter over the existing
  `IntakeApplicationService.submit(...)` use-case (Phase 195/196).
  `workItemType` is forced to `INCIDENT`; severity is derived from
  the request (see §5).
- Authorization reuses the existing **`WORK_ITEM_CREATE`** permission
  (Phase 203 D4). No new permission. No new schema. No bot command.
- Each successful ingestion writes two audit rows: the existing
  `WORK_ITEM/CREATED` and a Phase 203-specific
  `WORK_ITEM/ERROR_INGESTED`. The `ERROR_INGESTED` payload is **PII-free**
  (see §6).
- If the target tenant has an active routing rule for `INCIDENT`, the
  Phase 164 AFTER_COMMIT pipeline emits a `TelegramCardDispatchRequested`
  event — the same dispatch path as `POST /api/intake/work-items`.

---

## 2. Prerequisites

Before an SDK call will succeed, all of the following must hold:

- **Caller identity** — the JWT `sub` claim must resolve to an existing
  `AppUser`; that user must have at least one **ACTIVE** `Membership`
  in the target tenant; that membership's role must include the
  existing `WORK_ITEM_CREATE` permission. The seeded `ADMIN`, `ENGINEER`,
  and `TESTER` roles all have it by default (V2 + V6 migrations).
- **Target workflow** — the tenant must have at least one **ACTIVE**
  `workflow_definition` whose `work_item_type = 'INCIDENT'`. The
  `INCIDENT_BASIC` template (Phase 198, V7 seed) provides this
  automatically when the tenant is onboarded with that template
  selected. See
  [`tenant-onboarding-runbook.md` §3](tenant-onboarding-runbook.md#3-path-a--rest-endpoint-post-apiadmintenants).
- **Bearer token** — the caller has a valid platform JWT.
- **Server-side caller** — the SDK / agent runs server-side. The
  endpoint is **not designed for browser / client-side direct calls**
  (the JWT belongs in a secret manager, not in a browser bundle).

---

## 3. REST endpoint contract

### 3.1 Request body example

Full shape (all fields, illustrative values):

```json
{
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "sourceService": "payment-api",
  "errorMessage": "NullPointerException at PaymentService.charge():42",
  "errorStackTrace": "java.lang.NullPointerException\n  at PaymentService.charge(PaymentService.java:42)\n  at PaymentController.pay(PaymentController.java:18)",
  "severityHint": "HIGH",
  "httpStatusCode": 500,
  "environment": "production",
  "tags": ["oncall:bob", "release:1.2.3", "region:eu"]
}
```

Required fields: `tenantId`, `sourceService` (1..100 chars),
`errorMessage` (1..500 chars).

Optional: `errorStackTrace` (≤ 5000), `severityHint`
(`CRITICAL` / `HIGH` / `MEDIUM` / `LOW`), `httpStatusCode` (integer),
`environment` (≤ 50), `tags` (0..10 entries, each ≤ 50 chars).

### 3.2 Response body example (201 Created)

```json
{
  "workItemId": "33333333-3333-3333-3333-333333333333",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "title": "[production][payment-api] NullPointerException at PaymentService.charge():42",
  "workItemType": "INCIDENT",
  "severityCode": "HIGH",
  "statusCode": "REPORTED",
  "createdAt": "2026-05-23T17:30:00Z"
}
```

`Location` response header points at the work item read URL:
`/api/admin/work-items/details/by-id?tenantId=<...>&workItemId=<...>`.

### 3.3 Error code → HTTP status

The platform's `GlobalExceptionHandler` maps every `BusinessRuleException`
to HTTP 422 with the standard `ApiErrorResponse` envelope (fields:
`errorCode`, `message`, `timestamp`, `correlationId`, `path`).
`AccessDeniedException` maps to HTTP 403. Missing / invalid JWT maps
to HTTP 401. The error codes below are emitted verbatim by
`ErrorIngestionService` (Phase 203).

| `errorCode`                | HTTP | When                                                          |
| -------------------------- | ---- | ------------------------------------------------------------- |
| `INVALID_TENANT_ID`        | 422  | `tenantId` null or absent                                     |
| `INVALID_SOURCE_SERVICE`   | 422  | `sourceService` blank or > 100 chars                          |
| `INVALID_ERROR_MESSAGE`    | 422  | `errorMessage` blank or > 500 chars                           |
| `INVALID_SEVERITY_HINT`    | 422  | `severityHint` not in `CRITICAL` / `HIGH` / `MEDIUM` / `LOW`  |
| `INVALID_STACK_TRACE`      | 422  | `errorStackTrace` > 5000 chars                                |
| `INVALID_ENVIRONMENT`      | 422  | `environment` > 50 chars                                      |
| `INVALID_TAG`              | 422  | a tag is blank or > 50 chars                                  |
| `TOO_MANY_TAGS`            | 422  | `tags` > 10 entries                                           |
| `NO_INCIDENT_WORKFLOW`     | 422  | target tenant has no ACTIVE INCIDENT-type `workflow_definition` (translated from the intake `NO_ACTIVE_WORKFLOW` code) |
| `ACCESS_DENIED`            | 403  | actor lacks `WORK_ITEM_CREATE` in the target tenant           |
| (missing body / bad JSON)  | 400  | Spring deserialization failure                                |
| (no / invalid JWT)         | 401  | bearer token missing or unverifiable                          |

---

## 4. SDK integration patterns

### 4.1 Minimal curl (manual test)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $SDK_JWT" \
  -H 'Content-Type: application/json' \
  "$PLATFORM_BASE/api/intake/errors" \
  -d '{
        "tenantId": "11111111-1111-1111-1111-111111111111",
        "sourceService": "payment-api",
        "errorMessage": "NullPointerException at PaymentService.charge():42",
        "httpStatusCode": 500,
        "environment": "production"
      }' | jq .
```

Expect 201 + a JSON body with `workItemId`, `severityCode: "CRITICAL"`
(http 500 → CRITICAL when no `severityHint`).

### 4.2 Java client (Spring `RestClient`)

Wrap the call in a fire-and-forget helper that **never** propagates an
exception back to the application thread:

```java
@Component
public class PlatformErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(PlatformErrorReporter.class);

    private final RestClient restClient;
    private final UUID tenantId;
    private final String jwt;

    public PlatformErrorReporter(@Value("${platform.baseUrl}") String baseUrl,
                                  @Value("${platform.tenantId}") UUID tenantId,
                                  @Value("${platform.jwt}") String jwt) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.tenantId = tenantId;
        this.jwt = jwt;
    }

    public void report(String sourceService, Throwable t, Integer httpStatus) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", tenantId);
            body.put("sourceService", sourceService);
            body.put("errorMessage", String.valueOf(t.getMessage()));
            body.put("errorStackTrace", stackTraceOf(t));
            if (httpStatus != null) body.put("httpStatusCode", httpStatus);
            restClient.post()
                    .uri("/api/intake/errors")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            // Fire-and-forget — never crash the calling application.
            log.warn("Error report failed sourceService={} exceptionType={}",
                    sourceService, ex.getClass().getSimpleName());
        }
    }
}
```

### 4.3 Python client (`requests`)

```python
import logging
import requests
import traceback

LOG = logging.getLogger(__name__)
PLATFORM_BASE = "https://platform.internal"
TENANT_ID = "11111111-1111-1111-1111-111111111111"
JWT = "..."  # loaded from secret manager at boot

def report_error(source_service: str, exc: BaseException, http_status: int | None = None) -> None:
    body = {
        "tenantId": TENANT_ID,
        "sourceService": source_service,
        "errorMessage": str(exc)[:500],
        "errorStackTrace": "".join(traceback.format_exception(exc))[:5000],
    }
    if http_status is not None:
        body["httpStatusCode"] = http_status
    try:
        requests.post(
            f"{PLATFORM_BASE}/api/intake/errors",
            headers={"Authorization": f"Bearer {JWT}"},
            json=body,
            timeout=2,
        )
    except Exception as e:  # never re-raise — fire-and-forget
        LOG.warning("Error report failed sourceService=%s exceptionType=%s",
                    source_service, type(e).__name__)
```

### 4.4 Best-practice callouts

- **DO** report fire-and-forget. Your error reporter must **never** raise
  back into the application thread. A failing reporter must not crash
  the app it's trying to observe.
- **DO** sample high-volume errors client-side. The platform does not
  (yet) have rate limiting; an unsampled fatal in a tight loop can
  flood the target tenant. Typical strategies: token-bucket per
  `(sourceService, errorClass)` key, deduplication within a short
  window, or environment-conditional reporting (off in load tests).

---

## 5. Severity derivation matrix

The Phase 203 service uses this exact precedence: explicit
`severityHint` (validated against the enum) wins; otherwise
`httpStatusCode` is consulted; otherwise `MEDIUM` is the default.

| `severityHint` | `httpStatusCode` | resolved `severityCode` | example                            |
| -------------- | ---------------- | ----------------------- | ---------------------------------- |
| `CRITICAL`     | (any)            | `CRITICAL`              | hint takes precedence              |
| `HIGH`         | 500              | `HIGH`                  | hint wins over status              |
| (omit)         | 500              | `CRITICAL`              | 5xx → CRITICAL                     |
| (omit)         | 503              | `CRITICAL`              | 5xx → CRITICAL                     |
| (omit)         | 404              | `HIGH`                  | 4xx → HIGH                         |
| (omit)         | 401              | `HIGH`                  | 4xx → HIGH                         |
| (omit)         | 301              | `MEDIUM`                | 3xx falls to default               |
| (omit)         | 200              | `MEDIUM`                | 2xx falls to default               |
| (omit)         | (omit)           | `MEDIUM`                | nothing supplied → default         |
| `LOW`          | (any)            | `LOW`                   | hint takes precedence              |

**Important:** an invalid `severityHint` (anything other than
`CRITICAL` / `HIGH` / `MEDIUM` / `LOW`) is **rejected** at validation
with HTTP 422 `INVALID_SEVERITY_HINT`. It does **not** silently fall
back to derivation.

---

## 6. Audit trail per successful ingestion

Each successful `POST /api/intake/errors` writes two audit rows
(in emission order):

| `event_type`     | `entity_type` | `action_source` | Source                                                                       |
| ---------------- | ------------- | --------------- | ---------------------------------------------------------------------------- |
| `CREATED`        | `WORK_ITEM`   | `INTAKE_API`    | Phase 195 (via `IntakeApplicationService` → `WorkItemCommandService.create`) |
| `ERROR_INGESTED` | `WORK_ITEM`   | `INTAKE_API`    | Phase 203 (`ErrorIngestionService.ingest`)                                   |

The `ERROR_INGESTED` row's `new_value_json` payload is **bounded** —
verified byte-for-byte by Phase 203 tests:

```json
{
  "sourceService": "payment-api",
  "severityCode": "HIGH",
  "httpStatusCode": 500,
  "tagCount": 3
}
```

**What is NEVER in the audit payload** (security-critical, by design):

- `errorMessage` — may contain user input, internal endpoint paths, or
  inadvertent secrets.
- `errorStackTrace` — may contain file paths, internal class names, or
  secret values from local variables shown in some logging setups.
- tag values — operator-supplied, could contain anything; only
  `tagCount` is recorded.

Beyond audit, the Phase 164 AFTER_COMMIT pipeline may publish a
`TelegramCardDispatchRequested` event if the tenant has an active
routing rule for `INCIDENT`. The card render itself goes through the
existing Phase 194/196 path and is unaffected by Phase 203.

For the broader audit-trail pattern (onboarding-level vs. resource-level
audit rows), see
[`tenant-onboarding-runbook.md` §5](tenant-onboarding-runbook.md#5-audit-trail-per-successful-onboarding).

---

## 7. Verification queries (read-only)

Operators can confirm a successful ingestion landed by querying
PostgreSQL directly. The column names below match the real schema
(V1–V8).

> **Schema note for §7.3:** `audit_event.new_value_json` is declared as
> `TEXT` (not `jsonb`) in V3. JSON-aware extraction queries must
> therefore cast the column with `::jsonb` before using the `->>`
> operator. Rows whose payloads are not valid JSON would fail the cast;
> in practice the `ERROR_INGESTED` rows emitted by
> `ErrorIngestionService` are always valid JSON.

### 7.1 Recently ingested INCIDENT work items for a tenant

```sql
SELECT id,
       title,
       severity_code,
       current_status_code,
       created_at
  FROM work_item
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
   AND type_code = 'INCIDENT'
 ORDER BY created_at DESC
 LIMIT 20;
```

### 7.2 ERROR_INGESTED audit rows for a tenant

```sql
SELECT occurred_at,
       entity_id      AS work_item_id,
       actor_user_id,
       new_value_json
  FROM audit_event
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
   AND event_type = 'ERROR_INGESTED'
 ORDER BY occurred_at DESC
 LIMIT 20;
```

### 7.3 Top source services by ingested error count (last 24h)

Because `new_value_json` is `TEXT`, the JSON value is parsed by casting
to `jsonb` first:

```sql
SELECT (new_value_json::jsonb)->>'sourceService' AS source_service,
       COUNT(*) AS error_count
  FROM audit_event
 WHERE tenant_id = (SELECT id FROM tenant WHERE slug = 'acme')
   AND event_type = 'ERROR_INGESTED'
   AND occurred_at > now() - INTERVAL '24 hours'
 GROUP BY source_service
 ORDER BY error_count DESC;
```

---

## 8. Known limitations

- **No bot command.** Phase 203 is REST-only. A future phase may add
  `/report-error` or a similar Telegram bot affordance.
- **No granular SDK permission.** The endpoint reuses the existing
  `WORK_ITEM_CREATE` permission (Phase 203 D4). An "SDK-only" role
  carrying a narrower `INCIDENT_INGEST` permission is a future
  improvement; today, granting an SDK identity `ENGINEER` also gives
  it the broader work-item authority.
- **No batch ingestion.** Each request creates exactly one work item.
  SDKs should sample / aggregate client-side for high-volume errors
  (see §4.4).
- **No rate limiting.** A misbehaving SDK can flood the tenant; the
  only mitigation today is client-side sampling and monitoring the
  §7.3 query.
- **No deduplication.** Two identical errors create two distinct work
  items. Fingerprint-based dedup is a future phase candidate.
- **No structured stack trace parsing.** The stack trace is stored
  verbatim in the `description`; no class/method/line extraction.
- **Truncation is silent.** Title > 200 characters and description
  > 5000 characters are truncated without returning a warning in the
  response. SDKs should size their messages accordingly.
- **No client-side TLS / certificate pinning guidance.** The endpoint
  expects a JWT over HTTPS in production; transport security is
  deployment-level (load balancer / API gateway).

---

## 9. Troubleshooting

The three most common SDK-facing errors:

- **`NO_INCIDENT_WORKFLOW` (422)** — the target tenant has no ACTIVE
  `workflow_definition` whose `work_item_type = 'INCIDENT'`. Fix:
  re-run onboarding for that tenant including the `INCIDENT_BASIC`
  template (see
  [`tenant-onboarding-runbook.md` §3](tenant-onboarding-runbook.md#3-path-a--rest-endpoint-post-apiadmintenants)),
  or insert a workflow_definition manually via the admin write
  endpoints.

- **`ACCESS_DENIED` (403)** — the caller's role lacks `WORK_ITEM_CREATE`
  in the target tenant. Fix: have an existing `ADMIN` of that tenant
  grant the caller's role the permission, OR use a different identity
  (e.g. an SDK service account) that already has it through one of
  the seeded `ADMIN` / `ENGINEER` / `TESTER` roles.

- **`INVALID_SEVERITY_HINT` (422)** — only `CRITICAL`, `HIGH`, `MEDIUM`,
  and `LOW` are accepted. The endpoint does **not** silently fall back
  to HTTP-status derivation if you send `WEIRD`; it rejects the
  request. Omit the field if you want automatic derivation.

---

> Cross-references:
> - End-to-end smoke checklist:
>   [`demo-smoke-runbook.md` §16](demo-smoke-runbook.md#16-error-ingestion-smoke).
> - MVP completion overview:
>   [`mvp-completion-runbook.md` §2](mvp-completion-runbook.md#2-capability-snapshot--phase-185-foundation-plus-later-documented-additions).
> - Onboarding audit pattern (sibling runbook):
>   [`tenant-onboarding-runbook.md` §5](tenant-onboarding-runbook.md#5-audit-trail-per-successful-onboarding).
