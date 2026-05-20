# Telegram Engineering Ops Platform

Telegram-native, multi-tenant Engineering Operations Platform for engineering and operations teams.

## Tech Stack

- Java 21
- Spring Boot 3.4
- PostgreSQL 17
- Flyway (DB migrations)
- HikariCP (connection pool)
- Maven

## Architecture

Modular monolith with business-capability-based package structure:

```
com.engops.platform
├── sharedkernel      # Cross-cutting value objects and base types
├── identity          # Users, roles, permissions, membership
├── tenantconfig      # Tenant onboarding and settings
├── intake            # Work item ingestion
├── routing           # Assignment and dispatch rules
├── workitem          # Core domain: bugs, incidents, tasks
├── workflow          # State machine and transition rules
├── telegram          # Telegram Bot API integration
├── audit             # Audit event recording
├── analytics         # Manager-facing analytics
├── admin             # Web admin panel backend
└── infrastructure    # Cross-cutting infra concerns
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

## Getting Started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify

- Application: http://localhost:8080
- Health check: http://localhost:8080/actuator/health

## Local DB credentials and overrides

`docker compose up -d` provisions PostgreSQL with intentionally non-secret
placeholder credentials:

- database: `engops`
- username: `engops`
- password: `engops_local`

`application.properties` uses those same values as default fallbacks for the
`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` environment
variables, so local startup with the `local` profile works out-of-the-box.
Override any of them by exporting the env vars before running.

Production deployments must set `DATABASE_URL`, `DATABASE_USERNAME`, and
`DATABASE_PASSWORD` to real values via the deployment platform — local
defaults are not for production use.

> **Security note (Phase 152):** a real-looking personal password was
> previously committed in `application.properties` and
> `application-local.properties`. The current HEAD removes it, but git
> history still contains it. If that value was reused elsewhere, treat it
> as compromised and rotate it outside the repo. Git history rewriting is
> out of scope of this change.

## Production deployment

Run the application with the `prod` profile (`--profiles=prod` or
`SPRING_PROFILES_ACTIVE=prod`). Production datasource values must come from
the deployment environment (`DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`); local defaults are not used.

The full operator contract — JWT decoder env vars, first-admin bootstrap
env vars, HTTP auth failure semantics, audit expectations, and rollback
scenarios — lives in the
[First-Admin Bootstrap Runbook](docs/operations/first-admin-bootstrap-runbook.md).

Telegram outbound activation — `app.telegram.bot-token` /
`TELEGRAM_BOT_TOKEN`, real-vs-stub gateway selection, delivery outcome
semantics, observability endpoints, and current limitations — is covered
in the
[Telegram Outbound Gateway Runbook](docs/operations/telegram-outbound-gateway-runbook.md).

For an end-to-end MVP smoke run (env → bootstrap → JWT → tenant config →
intake → workflow transition → delivery observability), follow the
[Demo Smoke Runbook](docs/operations/demo-smoke-runbook.md).

For a single consolidated operator entry point ("repo cloned" → "MVP demo passed" + production-readiness MVP checklist), follow the [MVP Completion Runbook](docs/operations/mvp-completion-runbook.md).

For packaging the app as a Docker image and running it on a single VM behind an operator-owned HTTPS reverse proxy, follow the [Production Deployment Runbook](docs/operations/production-deployment-runbook.md).

## Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development with Docker PostgreSQL |
| `prod`  | Production with environment variable configuration |