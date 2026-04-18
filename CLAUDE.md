# PayFlow — Claude Code Rules

## Project Overview

PayFlow is a portfolio project demonstrating senior-level Java architecture:
- Event Sourcing + CQRS applied to a financial transfers domain
- Same business logic implemented in two JVM stacks: Spring Boot 3 (WebFlux) and Micronaut 4 (Netty)
- Hexagonal Architecture (Ports & Adapters) with `shared/domain` as the pure Java core
- Saga orchestration with compensating transactions and full idempotency

**Java 21 | Maven multi-module | PostgreSQL 16 | Apache Kafka 3.7**

---

## Architecture Rules

### Hexagonal Architecture (Non-Negotiable)

- `shared/domain` MUST have ZERO framework dependencies — no Spring, no Micronaut, no Jakarta annotations
- Aggregates, Domain Events, Value Objects, Saga state machine, and `EventStore` interface live in `shared/domain`
- Services in `services/*` are adapters only — they wire infrastructure (DB, Kafka, HTTP) to the domain ports
- Never let a JPA `@Entity` annotation leak into `shared/domain`
- If porting from Spring to Micronaut is slow, the domain is too coupled — fix the domain first

### Event Store (Append-Only)

- Events are written with `INSERT` only — NEVER `UPDATE` or `DELETE` in `event_store`
- `sequence_num` conflict = optimistic concurrency violation → throw domain exception, do NOT retry silently
- Every event payload MUST include `_eventVersion` for upcasting support
- The `EventStore` interface is defined in `shared/domain` — adapters implement it per stack
- Read via `loadEvents(aggregateId)` for full replay; `loadEventsSince(aggregateId, fromSequence)` for incremental

### Saga Orchestration

- `TransferService` is the explicit orchestrator — all saga state transitions happen here
- State machine: `INITIATED → DEBITING → CREDITING → COMPLETED` (happy path)
- Compensating paths: `DEBITING → FAILED`, `CREDITING → REVERSING_DEBIT → REVERSED`
- Every saga step MUST be idempotent — applying the same event twice must not change state
- Never embed saga logic in Kafka consumers — consumers dispatch to the orchestrator

### CQRS / Projections

- Read models (`account_projections`, `transaction_history`) are rebuilt from Kafka events by the Projector
- Projector is stateless — it MUST be restartable from offset 0 to rebuild projections from scratch
- Never read from `event_store` for API read responses — use projections
- Projections are eventually consistent; document this in API contracts

### Idempotency

- All write endpoints MUST check `Idempotency-Key` header before processing
- Store `idempotency_keys` with TTL 24h and return original response on duplicate key
- Implement idempotency at the API layer, BEFORE business logic executes

### Kafka Topics

| Topic | Partitioned by | Purpose |
|---|---|---|
| `payflow.account.events` | `accountId` | Domain events from account service |
| `payflow.transfer.events` | `transferId` | Domain events from transfer service |
| `payflow.transfer.commands` | `accountId` | Saga commands to account service |
| `payflow.dlq` | — | Dead letter queue for all consumers |

- Always use `accountId` as partition key for account-related messages (ordering guarantee)
- Failed messages after max retries go to `payflow.dlq` — never swallow errors silently

### Observability

- Every API entry point MUST create an OpenTelemetry span
- Every Kafka publish/consume MUST propagate W3C Trace Context headers
- Every `EventStore.append()` call MUST be wrapped in a span
- Business metrics (counters, histograms) are defined in SPEC section 9.2 — implement all of them

---

## Domain Model Rules

### Value Objects

- `Money`: always use `BigDecimal` — NEVER `double` or `float` for financial amounts
- `Money` is immutable; arithmetic returns new instances
- `AccountId` and `TransferId` are typed UUID wrappers — never use raw `UUID` in method signatures
- Validate `Money` at construction: amount >= 0, currency is valid ISO 4217

### Aggregates

- Aggregates emit events — they do NOT call repositories or send messages
- State changes happen only by applying events via `apply(DomainEvent)` methods
- `version` field is used for optimistic locking — increment on every event applied
- Aggregate root constructor takes events and replays them to rebuild state

---

## Testing Rules

- **Domain unit tests**: pure Java, no Spring context, no mocking frameworks needed — aggregates are plain objects
- **Saga tests**: all state machine paths including compensation and idempotency
- **Integration tests**: Testcontainers with real Kafka and PostgreSQL — no mocking of infrastructure
- **Never mock the database** in integration tests — Testcontainers gives you the real thing
- **Test slices**: `@WebMvcTest`/`@WebFluxTest` for controllers, `@DataJpaTest` for repositories (Spring stack)
- **Coverage target**: 100% of saga state transitions must have explicit tests

---

## What Belongs Where

```
shared/domain/          → Aggregates, Domain Events, Value Objects, Saga, EventStore interface
services/account-spring/ → Spring adapter: REST controllers, JPA impl of EventStore, Kafka producer
services/transfer-spring/ → Spring adapter: REST controllers, Saga orchestrator wired to Spring
services/account-micronaut/ → Micronaut adapter: same domain, different infrastructure
services/transfer-micronaut/ → Micronaut adapter: same domain, different infrastructure
infra/                  → Docker Compose only — no application code here
benchmarks/             → k6 scripts and documented results
```

---

## See Also

- [Spring Boot Rules](.claude/rules/spring-boot.md)
- [Micronaut Rules](.claude/rules/micronaut.md)
- [Architecture Rules](.claude/rules/architecture.md)
- [SPEC](docs/SPEC.md)
- [Design Decisions](openspec/changes/payflow-event-driven-platform/design.md)
