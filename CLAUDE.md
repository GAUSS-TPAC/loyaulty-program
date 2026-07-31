# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A SaaS multi-tenant loyalty platform by Yowyob, consisting of:
- **Backend**: Java 21 + Spring Boot 3.4 (WebFlux, R2DBC, Kafka, Keycloak)
- **Frontend**: Next.js 16 + TypeScript (App Router, shadcn/ui, next-intl)

---

## Backend Commands

```bash
# Start infrastructure (Postgres, Redis, Kafka, Keycloak)
docker-compose up -d

# Run the application (default port 8082 in dev, 8081 in prod)
./mvnw spring-boot:run

# Run with dev profile (verbose logging, relaxed security)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run all tests
./mvnw clean verify

# Run a single test class
./mvnw test -Dtest=RuleEngineTest

# Run a single test method
./mvnw test -Dtest=RuleEngineTest#methodName

# Build without tests
./mvnw clean package -DskipTests
```

API: `http://localhost:8081` (prod) / `http://localhost:8082` (dev)  
Swagger UI: `http://localhost:8081/swagger-ui.html`

---

## Frontend Commands

```bash
cd loyalty-program-frontend

npm install
npm run dev       # http://localhost:3000
npm run build
npm run lint
```

All API calls are proxied via Next.js rewrites: `/backend/*` → `http://localhost:8081/*` (configured via `NEXT_PUBLIC_API_URL` in `.env.local`).

---

## Architecture

### Backend: Hexagonal Architecture

The backend enforces strict layering via ArchUnit tests (`HexagonalArchitectureTest`). The dependency rule: `domain` must never import from `infrastructure` or `api`.

```
com.yowyob.loyalty/
├── api/           # Inbound adapters: WebFlux controllers, DTOs, mappers
├── application/   # Use-case handlers (orchestrate domain + ports)
├── domain/        # Pure business logic, ports (interfaces), models
│   ├── loyalty/   # Rule engine, points, tiers, events
│   ├── wallet/    # Wallet balances, transactions
│   ├── bonification/  # External bonification partner integration
│   └── tenant/    # Multi-tenancy model
├── infrastructure/ # Outbound adapters: R2DBC repos, Redis, Kafka, security
│   ├── persistence/ # R2DBC entity/mapper/repository per domain
│   ├── redis/     # RuleCacheAdapter, IdempotencyRedisAdapter
│   ├── kafka/     # Event publisher adapters
│   ├── security/  # JWT filter, tenant security filter
│   └── stub/      # In-memory stubs for local/test use
└── shared/        # Cross-cutting: multi-tenancy context, exceptions, logging
```

**Data flow**: `Controller` → `Handler` (application) → `UseCase` port (domain) → `Repository/Cache` port → infrastructure adapter.

### Multi-tenancy

Every request carries a `tenantId` extracted from the JWT claim `organization_id`. `TenantContextHolder` stores it reactively (Reactor `Context`). In dev mode, `DevTenantResolutionFilter` injects a default tenant UUID (`app.dev.default-tenant-id`).

### Rule Engine

Loyalty rules (in `domain/loyalty/`) define a `Trigger` → `Conditions` → `Effects` pipeline. `ProcessEventHandler` evaluates incoming events against active rules cached in Redis. Condition evaluators are in `domain/loyalty/service/evaluator/` (cumulative amount/count, first-event, points balance, tier).

### Reactive Stack

The entire backend is non-blocking: WebFlux for HTTP, R2DBC for PostgreSQL, Reactor Kafka, and reactive Redis. Use `Mono`/`Flux` throughout — never block the reactive thread.

### Testing Strategy

- **Unit tests**: domain logic (`RuleEngineTest`, `PointsAccountTest`, `TierPolicyTest`) — no Spring context, no I/O
- **Integration tests**: use Testcontainers (PostgreSQL + Redis via `TestContainersConfig`) with `@ActiveProfiles("test")`
- **Architecture tests**: `HexagonalArchitectureTest` enforces layer boundaries at build time
- Test profile (`application-test.yml`) uses H2 in-memory R2DBC and disables Liquibase, Kafka, and Bonification

### Frontend: Next.js App Router

```
loyalty-program-frontend/src/
├── app/[locale]/    # i18n-aware routes (en/fr via next-intl)
│   └── portal/      # Admin portal pages: members, rules, wallet, events, bonification
├── components/
│   ├── ui/          # shadcn/ui primitives (do not edit directly)
│   └── layout/      # Header, Sidebar
├── lib/api.ts       # Single source of truth for all backend API calls
├── hooks/           # React hooks wrapping api.ts
└── i18n/            # next-intl routing and request config
```

All backend communication goes through `src/lib/api.ts`. The JWT token is stored in `sessionStorage` under the key `loyalty_jwt_token` and injected as a `Bearer` token on every request.

Translations live in `messages/en.json` and `messages/fr.json`.

---

## Key Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST/DB_PORT/DB_NAME` | `localhost/5432/loyalty_db` | PostgreSQL |
| `REDIS_HOST/REDIS_PORT` | `localhost/6379` | Redis |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka (KRaft mode, no ZooKeeper) |
| `JWT_ISSUER_URI` | `http://localhost:8080/realms/loyalty` | Keycloak |
| `KERNEL_CORE_URL` | `http://localhost:8090` | Internal Kernel Core service |
| `BONIFICATION_API_BASE_URL` | `https://bonusapi.onrender.com` | External bonification partner |
| `SERVER_PORT` | `8081` (prod) / `8082` (dev) | Backend port |

Copy `.env.example` to `.env` before first run. Frontend uses `loyalty-program-frontend/.env.local`.

## Database Migrations

Liquibase changelogs are in `src/main/resources/db/changelog/`, using the formatted-SQL changelog format (plain `.sql` files with `--liquibase formatted sql` / `--changeset` header comments), included in order from `db.changelog-master.yaml`. Liquibase runs on startup via synchronous JDBC even though the app is reactive (Spring JDBC bridge). New migrations must follow the `{next}-description.sql` naming convention and be added to the master changelog. Changesets containing dollar-quoted PL/pgSQL blocks (`DO $$ ... END $$;`, `CREATE FUNCTION ... LANGUAGE plpgsql`) must set `splitStatements:false` so Liquibase doesn't split on the semicolons inside the block.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
