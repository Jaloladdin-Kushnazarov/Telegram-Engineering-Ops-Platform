# Project: Telegram-based Engineering Operations Platform

## Product Positioning
This is not a simple Telegram bot.

This project is a Telegram-native, multi-tenant, configurable Engineering Operations Platform for engineering and operations teams.

Its goal is to transform messy chat-based coordination into structured operational workflow inside Telegram.

## Core Product Goals
The platform should support:
- Incident Management
- Bug Workflow
- Team Collaboration
- Automation-ready operational flows
- Manager Analytics
- Tenant Configuration
- Identity / Role / Permission Management
- Telegram-first execution

## Frozen Architecture Decisions
- Architecture style: Modular Monolith
- Language: Java 21
- Framework: Spring Boot
- Build tool: Maven
- Database: PostgreSQL
- DB migrations: Flyway
- Connection pool: HikariCP
- Telegram Bot API for Telegram integration
- Backend + Database = source of truth
- Telegram = interaction surface + rendered projection
- Web admin panel = configuration and management surface
- Workflow = strict domain-controlled state machine
- Discussion and workflow are separate concerns
- Start with domain-specific service layer, not generic workflow DSL
- Multi-tenant by design
- Secure-by-default
- Auditability is first-class
- Observability from the beginning

## Core Domain Model
Use a common WorkItem model as the main operational entity.

WorkItem types:
- BUG
- INCIDENT
- TASK

Main concepts:
- Tenant
- User
- Membership
- Role
- Permission
- WorkItem
- WorkflowDefinition
- WorkflowTransitionRule
- Signal
- TelegramProjection
- AuditEvent
- RoutingRule

## UX Decisions
- Engineers work primarily in Telegram
- Tenant admins and configuration users work in web admin panel
- Telegram cards are compact operational projections
- One item has one active operational place at a time
- Topic change usually creates a new projection and supersedes the old one
- Use context-specific actions instead of generic overloaded actions

## Main MVP Workflow
Primary MVP bug flow:
- BUGS -> PROCESSING -> TESTING -> FIXED
- TESTING can return to BUGS
- FIXED can be reopened

## Important Security Rules
- Use telegram_user_id as the main Telegram identity
- Do not use Telegram username as authoritative identity
- Roles are tenant-scoped through Membership
- All resource access must be tenant-scoped
- Never query sensitive operational resources without tenant filter
- Telegram UI visibility does not replace server-side authorization
- Intake endpoints must be authenticated
- Sensitive business actions must be audited

## Important Code Organization Rules
Use business-capability-based modules, not global controller/service/repository folders.

Top-level modules:
- shared-kernel
- identity
- tenant-config
- intake
- routing
- workitem
- workflow
- telegram
- audit
- analytics
- admin
- infrastructure

Each module should own its own business truth and internal implementation.

Cross-module interaction must happen through public APIs, not by directly using another module's internal repositories.

## Things You Must NOT Do
- Do not switch to Gradle
- Do not generate microservices
- Do not invent a generic BPM/workflow engine
- Do not place business rules inside Telegram handlers
- Do not use Telegram as source of truth
- Do not put all logic into god services
- Do not create cross-module tight coupling
- Do not hardcode tenant-specific routing in code
- Do not skip audit for business-significant mutations
- Do not ignore optimistic locking and tenant isolation

## Implementation Approach
Always work in bounded increments.

Before generating code:
1. Explain the implementation plan
2. List files to create/modify
3. State assumptions
4. Check architecture boundary impact

After generating code:
1. Summarize what was added
2. Mention boundary compliance
3. Mention security implications
4. Mention concurrency implications
5. Mention observability implications
6. Mention what is intentionally left out

## Quality Expectations
- Production-grade Java/Spring Boot code
- Clear package structure
- Meaningful naming
- Proper transaction boundaries
- Strong validation
- Secure defaults
- Maintainable code
- Tests where appropriate
- No unnecessary complexity

## Current Working Style
The project is being built phase-by-phase.

Do not jump ahead.
Do not implement future phases unless explicitly requested.

Current implementation should follow the agreed roadmap and frozen architecture decisions.