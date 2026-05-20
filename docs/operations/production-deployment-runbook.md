# Production Deployment Runbook

> **Phase 187.** This is the operator entry point for taking the
> Telegram Engineering Operations Platform from a built jar to a
> running production instance on a single VM or small cloud server.
> It does not introduce any new feature, behavior, or HTTP surface —
> it only describes how to package and run what already exists at
> HEAD.

---

## 1. Purpose and scope

**Purpose.** Provide a reproducible, hand-runnable deployment baseline:
build a Docker image of the Spring Boot modular monolith, run it next
to a PostgreSQL container, expose it behind an operator-owned HTTPS
reverse proxy, and bring up the Telegram webhook.

**Target audience.** A backend engineer, SRE, or DevOps operator
deploying v1 to a single VM (cloud or self-hosted). Familiarity with
Docker, Docker Compose, PostgreSQL, and Telegram Bot API is assumed.

**What this runbook is.** A single-VM, single-instance, single-tenant
or small-multi-tenant deployment baseline. Configuration via env
vars. Docker Compose orchestration.

**What this runbook is not.**

- Not a Kubernetes / Helm package.
- Not a multi-region HA design.
- Not a managed-service procurement guide.
- Not a TLS termination guide (operator owns the reverse proxy).
- Not a backup/restore guide (Phase 188).
- Not an alerting / dashboard guide (Phase 189).

Read this runbook in sequence on the first deployment. Subsequent
deployments revisit only §4 (build), §5 (run), §10 (rollback), and
§12 (operational checklist).

---

## 2. Deployment topology

```
   Internet ── 443 ──► HTTPS reverse proxy (operator owned)
                       (nginx / Caddy / Traefik / ALB)
                              │
                              │ 8080 (plain HTTP, docker bridge)
                              ▼
   ┌─────────────────────────────────────────────────────────┐
   │ Docker host                                             │
   │   engops-platform-app  ◄────► engops-postgres           │
   │   (Spring Boot 3.4, JRE 21)   (postgres:17-alpine)      │
   │   non-root uid 1000           named volume engops-pgdata│
   └─────────────────────────────────────────────────────────┘

   App outbound: api.telegram.org (HTTPS 443) and the JWT
                 issuer/JWKS URL (HTTPS 443) if configured.
```

The HTTPS reverse proxy is the only public-facing component. It
terminates TLS and forwards `/api/telegram/webhook` and admin /
intake / workflow paths to the app on port 8080. It must NOT
forward Postgres traffic. PostgreSQL data lives in a named Docker
volume (`engops-pgdata` from the base compose file); it survives
`docker compose down`, not `down -v`.

---

## 3. Prerequisites

Confirm before starting:

- **Docker Engine ≥ 24.x** and **Docker Compose plugin ≥ 2.20.x**
  on the deployment host. Java/Maven on the host are *not* required —
  the multi-stage build runs inside the image.
- **Persistent storage** for the Docker named volume. The engine
  default `/var/lib/docker/volumes` is fine for a single-VM deploy.
- **HTTPS reverse proxy** in front of the app (nginx + certbot,
  Caddy, Traefik, AWS ALB, GCP HTTPS LB). Telegram `setWebhook`
  refuses plain HTTP.
- **Domain name** pointing at the reverse proxy.
- **Telegram Bot API token** from `@BotFather`.
- **Webhook secret** — generate via `openssl rand -hex 32`.
- **JWT decoder strategy** — exactly one of: HMAC secret
  (`openssl rand -base64 48`), OIDC issuer URI, or JWK Set URI.
  Conditional bean activation keeps the other two inactive
  (Phase 137 invariant).
- **Secret plan** — `.env` next to the compose files with
  `chmod 600`, or a managed secret store. Do not commit secrets
  to git.

---

## 4. Build the image

The Dockerfile is multi-stage: build stage uses the in-repo Maven
wrapper; runtime stage is JRE-only Java 21, running as the
unprivileged `engops` user (uid 1000).

```
docker build -t engops-platform:phase187 .
```

Tag with a release version when promoting:

```
docker build -t engops-platform:v0.1.0 .
```

Verify the image: `docker image ls engops-platform`. Expected size
~300–400 MB (JRE + curl + Spring Boot fat jar).

**Why secrets are never passed at build time.** The Dockerfile
accepts no `--build-arg` for any token, password, or JWT secret.
Build args are persisted in image history (`docker history
--no-trunc`) and would leak. Secrets are supplied only at container
start via env vars (see §6).

---

## 5. Run with Docker Compose

The base `docker-compose.yml` defines `postgres` only. The Phase 187
overlay `docker-compose.prod.yml` overrides Postgres credentials to
env-driven, removes the public Postgres port mapping, and adds the
`app` service.

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f app
```

Expected `ps` output:

```
NAME                     STATUS              PORTS
engops-postgres          Up (healthy)        5432/tcp
engops-platform-app      Up (healthy)        0.0.0.0:8080->8080/tcp
```

Health check from the host: `curl -fsS http://127.0.0.1:8080/actuator/health`
returns `200 OK` with `"status":"UP"`. From the public Internet,
this endpoint must be reachable **only via the HTTPS reverse proxy**.

Stop with data preserved: `docker compose ... down`. Destroy with
the Postgres volume (**deletes all data**): `docker compose ...
down -v`. Use the second form only for fresh tear-downs.

---

## 6. Required environment variables

Operator supplies these via a `chmod 600 .env` next to the compose
files or via the deployment platform's secret injection. The
overlay refuses to start when any **required** value is missing
thanks to compose's `${VAR:?error}` enforcement.

### 6.1 Datasource

| Env var            | Required | Notes                                                                  |
| ------------------ | -------- | ---------------------------------------------------------------------- |
| `DATABASE_URL`     | optional | Defaults to `jdbc:postgresql://postgres:5432/engops` on the docker net. Override only when using an external Postgres. |
| `DATABASE_USERNAME`| YES      | Must match `POSTGRES_USER`.                                            |
| `DATABASE_PASSWORD`| YES      | Must match `POSTGRES_PASSWORD`.                                        |
| `POSTGRES_DB`      | optional | Defaults to `engops`.                                                  |
| `POSTGRES_USER`    | YES      | Postgres role name used on first boot of the volume.                   |
| `POSTGRES_PASSWORD`| YES      | Postgres password used on first boot of the volume.                    |

### 6.2 Spring profile

| Env var                  | Required | Notes                                                  |
| ------------------------ | -------- | ------------------------------------------------------ |
| `SPRING_PROFILES_ACTIVE` | wired    | The compose overlay sets `prod` automatically.         |

### 6.3 JWT decoder (set exactly ONE of these three)

| Env var                       | Required | Notes                                                        |
| ----------------------------- | -------- | ------------------------------------------------------------ |
| `APP_SECURITY_JWT_HMAC_SECRET`| one-of   | Symmetric HS256 secret. Simple deployments.                  |
| `APP_SECURITY_JWT_ISSUER_URI` | one-of   | OIDC issuer (e.g. Okta / Auth0 / Keycloak).                  |
| `APP_SECURITY_JWT_JWK_SET_URI`| one-of   | Raw JWKS URL when no full OIDC discovery is available.       |

If all three are blank, the `JwtDecoder` bean is not created and
every `/api/**` request is rejected `401 UNAUTHORIZED` (Phase 137 +
Phase 146 + Phase 148 invariant). This is fail-closed by design.

### 6.4 Telegram outbound gateway

| Env var                            | Required | Notes                                                            |
| ---------------------------------- | -------- | ---------------------------------------------------------------- |
| `TELEGRAM_BOT_TOKEN`               | optional | Real Bot API token. Blank ⇒ `StubTelegramOutboundGateway` mode (Phase 158). |
| `TELEGRAM_API_BASE_URL`            | optional | Defaults to `https://api.telegram.org`. Override for testing.    |
| `TELEGRAM_CONNECT_TIMEOUT_MS`      | optional | Defaults to `5000`.                                              |
| `TELEGRAM_READ_TIMEOUT_MS`         | optional | Defaults to `10000`.                                             |

### 6.5 Telegram inbound webhook

| Env var                          | Required | Notes                                                              |
| -------------------------------- | -------- | ------------------------------------------------------------------ |
| `TELEGRAM_WEBHOOK_SECRET_TOKEN`  | YES (in real mode) | Constant-time-checked against the `X-Telegram-Bot-Api-Secret-Token` header. Blank ⇒ fail-closed: every inbound POST rejected with `401 UNAUTHORIZED`. |

### 6.6 Bootstrap admin (first run only)

| Env var                                  | Required | Notes                                                                              |
| ---------------------------------------- | -------- | ---------------------------------------------------------------------------------- |
| `APP_BOOTSTRAP_ADMIN_ENABLED`            | optional | Defaults to `false`. Set `true` ONLY for the first run.                            |
| `APP_BOOTSTRAP_ADMIN_TENANT_NAME`        | conditional | Required if `_ENABLED=true`. Human-readable tenant name.                        |
| `APP_BOOTSTRAP_ADMIN_TENANT_SLUG`        | conditional | Required if `_ENABLED=true`. Idempotency key. UNIQUE in DB. Pick a final value.|
| `APP_BOOTSTRAP_ADMIN_TENANT_TIMEZONE`    | optional | Defaults to `UTC`.                                                                  |
| `APP_BOOTSTRAP_ADMIN_APP_USER_ID`        | conditional | Required if `_ENABLED=true`. UUID. **Must equal the operator's JWT `sub` exactly.** |
| `APP_BOOTSTRAP_ADMIN_TELEGRAM_USER_ID`   | conditional | Required if `_ENABLED=true`. Long. UNIQUE in DB.                                 |
| `APP_BOOTSTRAP_ADMIN_DISPLAY_NAME`       | conditional | Required if `_ENABLED=true`. Human-readable name.                                |
| `APP_BOOTSTRAP_ADMIN_USERNAME`           | optional | Optional handle.                                                                    |
| `APP_BOOTSTRAP_WORKFLOW_ENABLED`         | optional | Defaults to `false`. Set `true` for the first run to seed MVP Bug Flow.            |

After the first successful run produces the `BOOTSTRAP_COMPLETED`
audit row, set `APP_BOOTSTRAP_ADMIN_ENABLED=false` and redeploy.
Future restarts must not toggle bootstrap on accidentally.

### 6.7 JVM tuning

| Env var      | Required | Notes                                                            |
| ------------ | -------- | ---------------------------------------------------------------- |
| `JAVA_OPTS`  | optional | Defaults to `-XX:MaxRAMPercentage=70.0`. Append extra flags here. |

### 6.8 Host port mapping

| Env var          | Required | Notes                                                       |
| ---------------- | -------- | ----------------------------------------------------------- |
| `APP_HOST_PORT`  | optional | Defaults to `8080`. Change if the host already uses 8080.    |
| `APP_IMAGE_TAG`  | optional | Defaults to `latest`. Useful for blue/green tag swaps.       |

For deeper context on each env var (semantics, validation, audit
implications), refer to
[`first-admin-bootstrap-runbook.md`](first-admin-bootstrap-runbook.md)
and
[`telegram-outbound-gateway-runbook.md`](telegram-outbound-gateway-runbook.md).

---

## 7. First boot sequence

On a fresh host:

1. **`.env` file** next to the compose files, `chmod 600`. At
   minimum: `POSTGRES_USER`, `POSTGRES_PASSWORD`,
   `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
   `TELEGRAM_WEBHOOK_SECRET_TOKEN`, one `APP_SECURITY_JWT_*`, and
   the bootstrap-admin block from §6.6.

2. **`docker compose ... up -d`** — Postgres starts first because
   the app `depends_on: postgres condition: service_healthy`.

3. **Flyway migrations** in app logs: look for `Successfully
   applied 6 migrations` (V1–V6, Phase 141 baseline).

4. **Bootstrap admin** in the same log stream: look for
   `BootstrapAdminInitializer` lines confirming tenant + app user
   + ADMIN role binding + MVP Bug Flow workflow seed.

5. **`BOOTSTRAP_COMPLETED` audit row** via psql exec:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml \
     exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
       -c "SELECT event_type, action_source, new_value_json
             FROM audit_event
            WHERE event_type='BOOTSTRAP_COMPLETED'
         ORDER BY occurred_at DESC LIMIT 5;"
   ```

6. **Health endpoint `UP`:** `curl -fsS http://127.0.0.1:8080/actuator/health`.

7. **Disable bootstrap on the next deploy:**
   `APP_BOOTSTRAP_ADMIN_ENABLED=false` and
   `APP_BOOTSTRAP_WORKFLOW_ENABLED=false`. Re-deploy. Idempotency
   keeps existing rows intact.

---

## 8. Telegram webhook registration

Telegram requires HTTPS for webhook delivery. Register **after**
the HTTPS reverse proxy is live and `/api/telegram/webhook` is
publicly reachable.

Register:

```
curl -sS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
        "url": "https://<your.domain>/api/telegram/webhook",
        "secret_token": "<TELEGRAM_WEBHOOK_SECRET_TOKEN>",
        "allowed_updates": ["callback_query"]
      }'
```

Expected response: `{"ok":true,"result":true}`.

Verify:

```
curl -sS "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

Confirm `url` matches, `has_custom_certificate` is `false`,
`pending_update_count` drops to `0`, and `last_error_message` is
absent or clears after the next callback.

Rollback / delete (e.g. moving to long-poll for debugging):

```
curl -sS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook?drop_pending_updates=true"
```

The Telegram Bot API refuses plain HTTP and refuses self-signed
certs unless the operator uploads them — use a publicly trusted
certificate (Let's Encrypt or your cloud provider).

Webhook response semantics (Phase 171/173/175):

| Inbound condition                                  | Response                                    |
| -------------------------------------------------- | ------------------------------------------- |
| Missing or wrong `X-Telegram-Bot-Api-Secret-Token` | `401 UNAUTHORIZED` + envelope               |
| Valid secret + non-callback update                 | `200 OK`, service not invoked               |
| Valid secret + callback_query (any outcome)        | `200 OK` — outcome encoded in ack + audit   |

`200 OK` for every business outcome is intentional: non-2xx would
make Telegram retry on permanent errors. The Phase 185
`TELEGRAM_CALLBACK_DENIED` audit row is the authoritative
server-side trace of denied attempts.

---

## 9. Smoke verification after deployment

Use the existing runbooks for full smoke coverage. This runbook
does not re-document them.

- For an end-to-end MVP smoke from "container is up" to "card
  refreshes on transition":
  [`mvp-completion-runbook.md`](mvp-completion-runbook.md)
  Path A (real Telegram) or Path B (stub mode).

- For the full live demo flow with `curl` calls and expected
  responses at every step:
  [`demo-smoke-runbook.md`](demo-smoke-runbook.md) §6–§9.

- For the Phase 179 manual smoke checklist that exercises every
  edit-first / send-as-fallback `OutcomeCategory`:
  [`demo-smoke-runbook.md` §13](demo-smoke-runbook.md#13-phase-179-manual-smoke-checklist--real-telegram-mode).

- For Telegram outbound activation contract, retry semantics, and
  troubleshooting:
  [`telegram-outbound-gateway-runbook.md`](telegram-outbound-gateway-runbook.md).

Treat the deployment "ready" only after at least one transition has
produced a `STATUS_TRANSITION` audit row and a successful Telegram
card delivery (or a deliberate `FAILED` row in stub mode).

---

## 10. Rollback procedure

Rollbacks at the container layer are safe. Rollbacks at the
database layer require a backup (see Phase 188 backup/restore
runbook, currently in planning).

**Container rollback (safe):**

1. Identify the previous good image tag:
   ```
   docker image ls engops-platform
   ```
2. Stop the current app container:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml stop app
   ```
3. Set `APP_IMAGE_TAG` in `.env` to the previous good tag.
4. Restart:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d app
   ```
5. Verify `/actuator/health` and re-run the smoke from §9.

**Database rollback (dangerous):**

Flyway migrations V1–V6 are forward-only by design. There is no
`migrate:down`. If a newly deployed image introduced a migration
that broke production:

1. Restore Postgres from a backup **taken before the broken
   migration ran**. Backup/restore details belong to the Phase 188
   runbook (currently planned, not yet shipped).
2. Roll back the container image as above.
3. Re-deploy with the old image so Flyway sees the old schema
   version and does not re-apply the broken migration.

Until Phase 188 ships, operators MUST:
- Take an out-of-band logical dump (`pg_dump`) before any deployment
  that includes a new migration.
- Test the migration in a staging clone first.

> **Note.** As of Phase 187 the schema is stable at V6. No new
> migration has been added since V6 (Phase 141). This makes the
> immediate v1 cutover risk low — most upgrades through Phase 192
> are docs / metrics / properties / code, not migrations.

---

## 11. Security checklist

The deployment is correctly hardened when every line below is true.

- [ ] No real bot token, no DB password, no JWT secret appears in
      any committed file. `.env` is `chmod 600` and not in git.
- [ ] Env vars come from a `.env` file with restricted permissions
      OR from a managed secret store that injects at container
      start.
- [ ] The Telegram bot token is rotated if anyone unauthorized has
      seen the running container's environment, logs, or process
      listing.
- [ ] The Telegram webhook secret is rotated and `setWebhook`
      re-run if the secret was ever shared in cleartext.
- [ ] Application logs contain no bot-token substring. Verify:
      ```
      docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        logs app | grep -i "$TELEGRAM_BOT_TOKEN"
      ```
      should return zero matches.
- [ ] The app is reachable from the Internet only through the
      HTTPS reverse proxy on port 443. Port 8080 is not exposed to
      the public network firewall.
- [ ] PostgreSQL is reachable only on the internal docker network.
      The overlay removes the base `5432:5432` host port mapping.
      Verify:
      ```
      ss -lntp | grep 5432
      ```
      should NOT show a public bind on the host.
- [ ] `flyway` actuator endpoint is currently exposed but
      authenticated (Phase 146). Phase 188 will scope it down to
      `health,info,metrics` in production via prod profile overrides.
- [ ] The leaked password documented in
      [README "Security note (Phase 152)"](../../README.md#local-db-credentials-and-overrides)
      has been rotated outside the repo if it was reused anywhere.

---

## 12. Operational checklist

Day-to-day operator signals:

- **Health endpoint.** `GET /actuator/health` returns `UP`. The
  container HEALTHCHECK polls every 15 s; `docker compose ps`
  shows `(healthy)` in steady state.
- **Application logs.** `docker compose logs -f app` shows
  bounded structured lines:
  - `Telegram callback execute outcome=<...>` for each inbound
    callback.
  - `Telegram card refresh dispatch outcome=<OutcomeCategory>` per
    AFTER_COMMIT dispatch.
  - `Telegram callback denial audit swallowed exceptionType=<...>`
    only if audit write fails (Phase 185 fail-soft path).
- **Delivery observability HTTP.** Query
  `GET /api/admin/delivery-observability/details?...` (admin JWT
  required) to inspect attempt history.
- **Audit event spot check.**
  ```
  docker compose -f docker-compose.yml -f docker-compose.prod.yml \
    exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      -c "SELECT occurred_at, event_type, action_source, entity_id
           FROM audit_event
       ORDER BY occurred_at DESC LIMIT 20;"
  ```
- **Telegram smoke (Path A).** Trigger an intake and a transition
  per [`mvp-completion-runbook.md`](mvp-completion-runbook.md) §3.7.
  Verify a card appears and updates in place.
- **Denial audit smoke (Phase 185).** Drive a deliberately denied
  callback (operator without `WORK_ITEM_TRANSITION` permission
  clicks a button) and verify one `TELEGRAM_CALLBACK_DENIED` row
  appears:
  ```
  ... WHERE event_type = 'TELEGRAM_CALLBACK_DENIED' ORDER BY occurred_at DESC LIMIT 5;
  ```

---

## 13. Known limitations of Phase 187

The Phase 187 deployment baseline is intentionally narrow. The
following are scoped to later bounded phases or stay deferred.

- **No Kubernetes / Helm package.** Single-VM Docker Compose only;
  multi-region HA is out of v1 scope.
- **No built-in reverse proxy.** TLS, HTTP/2, gzip, rate limiting
  are operator-owned (nginx / Caddy / Traefik / ALB).
- **No backup / restore runbook yet.** Phase 188 will add
  `backup-restore-runbook.md` (`pg_dump`, PITR options, restore
  drill). Until then, take an out-of-band logical dump before any
  deploy that ships a new migration.
- **No Micrometer custom counters yet.** Phase 189 will add
  counters for delivery attempts, callback outcomes, and card
  refresh outcomes plus an observability runbook. For now,
  alerting is via log grep (§12) and the delivery observability
  HTTP endpoints.
- **No priority / severity / owner intake or admin write yet.**
  Phase 190 will expose them. WorkItem already models the columns
  at V3; only the HTTP surface is missing.
- **No INCIDENT / TASK workflow seed yet.** Phase 156 seeds only
  the BUG workflow. Phase 190 will add optional bootstrap toggles
  and a workflow templates runbook.
- **No web admin UI.** v1 is API + Telegram.
- **`flyway` actuator still exposed (authenticated).** Phase 188
  will narrow `management.endpoints.web.exposure.include` in prod.

The consolidated operator entry point tying Phase 156 → 185 → 187
together is [`mvp-completion-runbook.md`](mvp-completion-runbook.md).
The next recommended bounded phase is **Phase 188 — Production
security/ops hardening**: prod profile actuator scoping, security
hardening runbook (token / webhook / JWT rotation, log redaction
verification, `getWebhookInfo` step), and backup/restore runbook.
