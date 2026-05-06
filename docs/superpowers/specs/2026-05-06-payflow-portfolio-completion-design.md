# PayFlow — Portfolio Completion Design

**Date:** 2026-05-06
**Status:** Approved
**Goal:** Complete the PayFlow portfolio project to 100%, prioritized by impact on portfolio readability for a mixed audience (Brazilian companies + international/big tech).

---

## Context

PayFlow is a Java 21 portfolio project demonstrating senior-level architecture: Event Sourcing + CQRS + Saga Orchestration + Hexagonal Architecture, implemented in two JVM stacks (Spring Boot 3 / WebFlux and Micronaut 4 / Netty) with the same pure-Java domain (`shared/domain`).

### Current State

| Group | Status |
|---|---|
| Infrastructure (Docker Compose, Kafka, Grafana) | ✅ Complete |
| Shared domain (aggregates, events, value objects, tests) | ✅ Complete |
| Event Store (PostgresEventStore, upcasting, Testcontainers) | ✅ Complete |
| Account Service — Spring Boot | ✅ Complete (missing: 4.6 OpenTelemetry spans) |
| Transfer Service — Spring Boot | ✅ Complete (missing: 5.5 W3C Trace Context on Kafka) |
| Event Projector | ❌ Not started |
| Micronaut port | ❌ Not started (zero code) |
| Benchmarks + Spring Cloud Contract | ❌ Not started |
| ADRs + C4 diagrams | ❌ Not started |
| CI/CD + README | ❌ Not started |

---

## Execution Order (by portfolio impact)

### Phase 1 — README + CI (high impact, low effort)

**README.md** at repo root:
- Badges: CI Spring, CI Micronaut, Codecov, Java 21
- Architecture section with ASCII diagram (simplified C4) linking to `docs/architecture/`
- How to run locally: `docker compose up -d` + `./mvnw test`
- Technical highlights: Event Sourcing, CQRS, Saga, Spring vs Micronaut side-by-side
- Links to ADRs, benchmarks, and SPEC

**GitHub Actions:**
- `.github/workflows/ci-spring.yml`: compile, test (account-spring + transfer-spring + shared) with Testcontainers via Docker-in-Docker, Jacoco report
- `.github/workflows/ci-micronaut.yml`: same pipeline for Micronaut modules (stub until Phase 4 is done)

**Outcome:** Portfolio is presentable and passes CI badge check within the first 30 seconds of review.

---

### Phase 2 — Close OpenTelemetry gaps (4.6 + 5.5)

**4.6 — account-spring:**
- Add OpenTelemetry spans on each API entry point (`POST /accounts`, `POST /accounts/{id}/deposits`)
- Add span on each `EventStore.append()` call
- No new dependencies needed — `micrometer-tracing` already available via Spring Boot Actuator

**5.5 — transfer-spring:**
- Propagate W3C Trace Context (`traceparent` header) in Kafka messages published by `TransferCommandPublisher`
- Extract and restore context in `AccountEventConsumer`

**Outcome:** End-to-end trace visible in Jaeger across HTTP → Kafka → EventStore.

---

### Phase 3 — Event Projector

Consumer Kafka in `account-spring` consuming `payflow.account.events`:

- `AccountProjector`: processes each `DomainEvent`, updates `account_projections`, inserts into `transaction_history`
- Idempotency: checks `sequence_num` already processed before updating (safe for reprocessing)
- Gauge `payflow.projector.lag.events` via Micrometer
- Restartable from offset 0: truncate projections → restart consumer → rebuild identical state
- Integration test (Testcontainers): truncate → restart consumer from offset 0 → assert rebuilt state == original state

**Outcome:** Full CQRS flow operational — write side (event store) and read side (projections) fully decoupled and event-driven.

---

### Phase 4 — Micronaut Port

Mirror Spring services using the same `shared/domain` — different infrastructure, identical behavior.

**account-micronaut:**
- `AccountController` with `@Controller`, `@Get`, `@Post`, reactive `Publisher<T>`
- `MicronautDataEventStore implements EventStore` — reuses `PostgresEventStore` from `shared/infrastructure`
- `AccountProjector` equivalent via Micronaut Kafka `@KafkaListener`
- `@ConfigurationProperties` for topics and TTL
- `ExceptionHandler<ConcurrencyConflictException>` for error mapping
- `@Introspected` + `@Serdeable` on DTOs for GraalVM compatibility

**transfer-micronaut:**
- `TransferSagaOrchestrator` equivalent to Spring
- `@KafkaClient` interface for publishing commands
- Same business metrics (Micrometer)
- W3C Trace Context propagated in Kafka headers

**Test reuse:** Integration tests from `shared/domain` are reused without modification to validate identical behavior across stacks.

**Outcome:** The core portfolio differentiator is complete — same domain, two frameworks, measurable side-by-side comparison.

---

### Phase 5 — Benchmarks + ADRs + C4

**Benchmarks (`benchmarks/`):**
- `benchmarks/k6/transfer-load.js`: full transfer flow (create accounts → initiate transfer → poll status), 50 VUs, 2 minutes
- Run against Spring (port 8080) and Micronaut (port 8081) separately
- Collect: startup time (`docker stats`), RSS memory, p50/p95/p99 latency
- Document in `benchmarks/results/README.md` with hardware context and JVM flags

**ADRs (`docs/adr/`):**
- `ADR-001-event-sourcing.md`: justification for Event Sourcing in financial domain (audit trail, replay, temporal queries)
- `ADR-002-saga-orchestration.md`: orchestration vs choreography — state explicitness trade-offs
- `ADR-003-kafka-choice.md`: Kafka vs RabbitMQ vs Redis Streams — per-partition ordering guarantee
- `ADR-004-event-versioning.md`: versioning strategy with `_eventVersion` and upcaster chain
- `ADR-005-spring-vs-micronaut.md`: post-implementation analysis with real benchmark data (written last)

**C4 Diagrams (`docs/architecture/`):**
- `c4-context.md`, `c4-container.md`, `c4-component-transfer.md` in Mermaid

**Note:** ADR-005 is deliberately the last document — written with real data, not speculation.

**Outcome:** Architectural reasoning is visible and backed by evidence. Portfolio demonstrates senior-level decision-making, not just implementation.

---

### Phase 6 — Spring Cloud Contract (nice-to-have)

Contracts between Transfer Service (consumer) and Account Service (provider) for Kafka events:

- Contracts for: `MoneyDebitedEvent`, `TransferDebitFailedEvent`, `MoneyCreditedEvent`, `MoneyCreditReversedEvent`
- Provider test generated in `account-spring` — validates published schema matches contract
- Consumer test generated in `transfer-spring` — validates consumer handles schema correctly
- Executed in CI — schema divergences detected automatically before merge

**Outcome:** Schema safety net between services, demonstrates awareness of contract testing in event-driven systems.

---

## Dependencies Between Phases

```
Phase 1 (README/CI) → independent, do first
Phase 2 (OTel gaps) → independent, close quickly
Phase 3 (Projector) → independent of Micronaut
Phase 4 (Micronaut) → independent of Projector
Phase 5 (Benchmarks) → requires Phase 4 (needs both stacks running)
Phase 5 (ADR-005)   → requires Phase 5 benchmarks (data-driven)
Phase 6 (Contracts) → requires Phases 3 + 4 complete
```

Phases 1–4 can be executed in any order relative to each other. Phase 5 must come after Phase 4. Phase 6 is last and optional.

---

## Success Criteria

- `docker compose up -d` + `./mvnw test` passes in under 5 minutes on a clean machine
- CI badges (Spring + Micronaut) are green on `main`
- Full transfer flow works end-to-end: REST → Kafka → EventStore → Projector → read model
- Micronaut and Spring serve identical responses for identical inputs
- Benchmark results documented with real numbers (not placeholders)
- ADR-005 references actual p99 latency and RSS memory from benchmarks
- README tells the architectural story in under 2 minutes of reading
