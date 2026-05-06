![CI Spring](https://github.com/maycoaoki/PayFlow/actions/workflows/ci-spring.yml/badge.svg)
![CI Micronaut](https://github.com/maycoaoki/PayFlow/actions/workflows/ci-micronaut.yml/badge.svg)
![Java 21](https://img.shields.io/badge/Java-21-blue)

# PayFlow

A portfolio project demonstrating senior-level Java architecture: Event Sourcing + CQRS + Saga Orchestration + Hexagonal Architecture, with the same pure-Java domain implemented in two JVM stacks — Spring Boot 3 (WebFlux) and Micronaut 4 (Netty).

---

## Architecture

```
                        HTTP Clients
                             |
          +------------------+------------------+
          |                                     |
   account-spring :8080              account-micronaut :8082
   transfer-spring :8081             transfer-micronaut :8083
          |                                     |
          +------------------+------------------+
                             |
                      shared/domain
               (Aggregates, Events, Saga, Ports)
                             |
                  shared/infrastructure
               (EventStore, Idempotency, Kafka utils)
                             |
               +-------------+-------------+
               |                           |
          PostgreSQL 16               Apache Kafka 3.7
                                           |
                        +------------------+------------------+
                        |                                     |
               account-spring / account-micronaut   transfer-spring / transfer-micronaut
               (consume payflow.account.events)      (consume payflow.transfer.commands)
```

**Key structural rule:** `shared/domain` has zero framework dependencies. Spring and Micronaut are adapters that wire infrastructure to domain ports — the domain never depends on adapters.

---

## Technical Highlights

| Concept | Implementation |
|---|---|
| Event Sourcing | All state changes are persisted as immutable events in `event_store`. Aggregates reconstruct state by replaying events. `INSERT`-only writes with `sequence_num` unique constraint for optimistic concurrency. |
| CQRS | Write model: event store. Read model: `account_projections` and `transaction_history` tables rebuilt by a stateless Projector consuming Kafka events. API reads never touch `event_store`. |
| Saga Orchestration | `TransferService` is the explicit orchestrator. State machine: `INITIATED → DEBITING → CREDITING → COMPLETED`. Compensation paths: `DEBITING → FAILED`, `CREDITING → REVERSING_DEBIT → REVERSED`. |
| Hexagonal Architecture | `shared/domain` defines ports (`EventStore` interface). Services implement adapters (JPA `EventStore`, Kafka producers/consumers, REST controllers). Domain has zero framework imports. |
| Dual Stack | Identical business logic runs on Spring Boot 3 (WebFlux + Reactor) and Micronaut 4 (Netty + compile-time DI). Benchmarks compare startup time, p99 latency, and memory RSS. |
| Idempotency | All write endpoints require `Idempotency-Key` header. Keys are checked before domain logic executes, stored with 24h TTL. Re-submission returns the original response with no side effects. |
| Observability | OpenTelemetry traces on every HTTP request, Kafka publish/consume, and `EventStore.append()`. W3C Trace Context propagated across Kafka messages. Micrometer business metrics for transfers initiated, completed, failed, and reversed. |

---

## How to Run

### 1. Start infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d
```

This starts PostgreSQL 16, Apache Kafka 3.7, Zookeeper, Prometheus, Grafana, and Jaeger.

### 2. Run all tests

```bash
./mvnw test
```

Integration tests use Testcontainers and require Docker. Domain unit tests have no infrastructure dependency.

### 3. Start services

**Spring stack:**
```bash
./mvnw -pl services/account-spring spring-boot:run   # http://localhost:8080
./mvnw -pl services/transfer-spring spring-boot:run  # http://localhost:8081
```

**Micronaut stack:**
```bash
./mvnw -pl services/account-micronaut mn:run   # http://localhost:8082
./mvnw -pl services/transfer-micronaut mn:run  # http://localhost:8083
```

---

## Example Flow

The following commands use the Spring stack (replace ports 8080/8081 with 8082/8083 for Micronaut).

**Create a source account:**
```bash
curl -s -X POST http://localhost:8080/accounts \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key-create-source" \
  -d '{"currency": "BRL", "initialDeposit": "1000.00"}' | jq .
```

**Create a destination account:**
```bash
curl -s -X POST http://localhost:8080/accounts \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key-create-dest" \
  -d '{"currency": "BRL", "initialDeposit": "0.00"}' | jq .
```

**Initiate a transfer (saga start):**
```bash
curl -s -X POST http://localhost:8081/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key-transfer-001" \
  -d '{
    "sourceAccountId": "<source-account-id>",
    "destinationAccountId": "<dest-account-id>",
    "amount": "250.00",
    "currency": "BRL"
  }' | jq .
```

Returns `202 Accepted`. The saga runs asynchronously through Kafka: `INITIATED → DEBITING → CREDITING → COMPLETED`.

**Check transfer status:**
```bash
curl -s http://localhost:8081/transfers/<transfer-id> | jq .
```

---

## Observability

| Tool | URL | Purpose |
|---|---|---|
| Grafana | http://localhost:3000 | Dashboards for business metrics and infrastructure |
| Jaeger | http://localhost:16686 | Distributed traces across services and Kafka |
| Prometheus | http://localhost:9090 | Raw metrics scraping and alerting rules |

Business metrics tracked: `payflow.transfers.initiated.total`, `payflow.transfers.completed.total`, `payflow.transfers.failed.total`, `payflow.transfers.reversed.total`, `payflow.transfer.duration.seconds`, `payflow.event_store.append.duration`, `payflow.projector.lag.events`.

---

## Benchmarks

Spring Boot vs Micronaut comparison results (startup time, p99 latency under load, memory RSS) are documented in [`benchmarks/results/README.md`](benchmarks/results/README.md).

k6 load test scripts are located in [`benchmarks/`](benchmarks/).

---

## Architecture Decision Records

| ADR | Title |
|---|---|
| [ADR-001](docs/adr/ADR-001.md) | Event Sourcing as the persistence model |
| [ADR-002](docs/adr/ADR-002.md) | Hexagonal Architecture with shared/domain |
| [ADR-003](docs/adr/ADR-003.md) | Saga Orchestration via explicit TransferService |
| [ADR-004](docs/adr/ADR-004.md) | Dual JVM stack — Spring Boot 3 and Micronaut 4 |
| [ADR-005](docs/adr/ADR-005.md) | Idempotency at the API adapter layer |

---

## Out of Scope

- Frontend or UI
- Real user authentication (mock JWT only)
- Integration with real banks or payment service providers
- Multi-tenancy
- LGPD / GDPR compliance implementation
- GraalVM native image for the Spring stack
