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

## Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development with Docker PostgreSQL |
| `prod`  | Production with environment variable configuration |