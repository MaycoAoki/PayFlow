# Architecture Rules — PayFlow

Domain: Financial transfers | Pattern: Event Sourcing + CQRS + Saga Orchestration + Hexagonal Architecture

---

## The Golden Rule: Domain Independence

`shared/domain` is framework-free. Zero. None. Not even `jakarta.persistence`.

```
shared/domain/src/main/java/payflow/domain/
├── account/      <- Account aggregate, AccountId, AccountStatus
├── transfer/     <- Transfer aggregate, SagaState, TransferStatus
└── events/       <- All DomainEvent subtypes (immutable records)
```

If you find yourself adding a Spring or Micronaut import to `shared/domain`, stop and reconsider.
If the port to Micronaut is slow, the domain is too coupled — fix it before continuing.

---

## Hexagonal Architecture (Ports & Adapters)

```
[HTTP Client] --> [API Adapter (in)] --> [Domain Port] --> [Domain Logic]
                                                               |
[Kafka Consumer] -> [Kafka Adapter (in)] ---^          [EventStore Port]
                                                               |
                                         [JPA Adapter (out)] --> [PostgreSQL]
                                         [Kafka Adapter (out)] --> [Kafka]
```

**Ports** (interfaces defined in `shared/domain`):
- `EventStore` — append and load events
- `AccountRepository` (optional read model port)

**Adapters** (implementations in `services/*`):
- `JpaEventStore implements EventStore` — in Spring and Micronaut packages
- `MicronautDataEventStore implements EventStore` — Micronaut variant

Rule: adapters depend on domain, domain never depends on adapters.

---

## Event Store Rules

- Write: `INSERT` only — never `UPDATE` or `DELETE` in `event_store` table
- Optimistic concurrency: `UNIQUE(aggregate_id, sequence_num)` — constraint violation = concurrency conflict
- Every event payload includes `_eventVersion` field — required for upcasting
- `EventStore` interface lives in `shared/domain` — implementations live in adapter packages

```java
// Domain port — no framework imports
public interface EventStore {
    void append(AggregateId aggregateId, List<DomainEvent> events, long expectedVersion);
    List<DomainEvent> loadEvents(AggregateId aggregateId);
    List<DomainEvent> loadEventsSince(AggregateId aggregateId, long fromSequence);
}
```

---

## Aggregate Rules

- Aggregates emit events — they do NOT call repositories, send Kafka messages, or call HTTP
- State changes happen ONLY by applying events via `apply(DomainEvent)` methods
- `version` field increments on every event applied — used for optimistic locking
- Reconstruct state via event replay: `Account.reconstitute(List<DomainEvent> history)`
- Applying the same event twice MUST NOT change state (idempotency at domain level)

```java
// Pattern for all aggregates
public class Account {
    private AccountId id;
    private Money balance;
    private long version;

    public static Account reconstitute(List<DomainEvent> history) {
        var account = new Account();
        history.forEach(account::apply);
        return account;
    }

    public List<DomainEvent> debit(Money amount, TransferId transferId) {
        // validate, then return events — do NOT apply them here
        return List.of(new MoneyDebitedEvent(id, transferId, amount, version + 1));
    }

    private void apply(MoneyDebitedEvent e) {
        this.balance = balance.subtract(e.amount());
        this.version = e.sequenceNum();
    }
}
```

---

## Saga Rules

- `TransferService` is the explicit orchestrator — all saga state transitions are here
- State machine (must be fully tested):

```
INITIATED -> DEBITING -> CREDITING -> COMPLETED   (happy path)
DEBITING  -> FAILED                               (debit failure)
CREDITING -> REVERSING_DEBIT -> REVERSED          (credit failure)
```

- Every saga step is idempotent — re-processing the same event must produce the same outcome
- Never embed saga logic in Kafka consumers — consumers call the orchestrator
- Saga state is persisted in `event_store` as events — reconstructed by replaying `Transfer` aggregate

---

## CQRS / Projections

- `account_projections` and `transaction_history` are rebuilt from Kafka events by the Projector
- Projector is stateless — restartable from Kafka offset 0 to fully rebuild read models
- Never query `event_store` for API read responses — use projections
- Projections are eventually consistent — document this in API contracts and error messages

---

## Idempotency

All write operations check `Idempotency-Key` before processing:

1. Look up `idempotency_keys` table for the key
2. If found and not expired: return stored response — no processing
3. If not found: process, then store `{key, response, status_code, expires_at}`
4. TTL: 24 hours

Idempotency check happens at the HTTP adapter layer, BEFORE domain logic.

---

## Kafka Topics

| Topic | Partition Key | Produced by | Consumed by |
|---|---|---|---|
| `payflow.account.events` | `accountId` | account-service | transfer-service, projector |
| `payflow.transfer.events` | `transferId` | transfer-service | projector |
| `payflow.transfer.commands` | `accountId` | transfer-service | account-service |
| `payflow.dlq` | — | any consumer | monitoring only |

- `accountId` as partition key guarantees ordering per account
- Failed messages after max retries go to `payflow.dlq` — never drop silently
- Manual offset commit only — never auto-commit

---

## Observability Requirements

Every deployed service MUST instrument:

**Traces (OpenTelemetry):**
- Span on every HTTP request (in)
- Span on every Kafka publish (out)
- Span on every Kafka consume (in)
- Span on every `EventStore.append()` call
- W3C Trace Context headers propagated across all Kafka messages

**Metrics (Micrometer):**
```
payflow.transfers.initiated.total     counter
payflow.transfers.completed.total     counter
payflow.transfers.failed.total        counter  tag: reason
payflow.transfers.reversed.total      counter
payflow.transfer.duration.seconds     histogram
payflow.event_store.append.duration   histogram
payflow.projector.lag.events          gauge
```

**Health:**
- `GET /health/liveness` — app is running
- `GET /health/readiness` — Kafka and Postgres are reachable

---

## Value Objects

- `Money`: `BigDecimal` + ISO 4217 currency code — NEVER `double` or `float`
- `Money` is immutable — arithmetic returns new instances
- `AccountId` and `TransferId` wrap `UUID` — never use raw `UUID` in domain method signatures
- Validate at construction — invalid state must not be representable

---

## Testing Requirements by Layer

| Layer | What | Tools |
|---|---|---|
| Domain unit | Every aggregate state transition | JUnit 5, pure Java |
| Domain unit | Every `Money` edge case (negative, overflow, invalid currency) | JUnit 5 |
| Saga integration | All state machine paths + compensation + idempotency | Testcontainers |
| API integration | Idempotency-Key, concurrency conflict, projection replay | Testcontainers |
| E2E / benchmark | Startup time, p99 latency, memory RSS — Spring vs Micronaut | k6 |

- Domain tests have NO framework dependency — they run in milliseconds
- Integration tests use REAL Postgres and Kafka via Testcontainers — no mocks for infrastructure
- Saga idempotency test: apply the same event twice, assert state unchanged

---

## What Does NOT Belong in This Project

- Frontend or UI
- Real user authentication (mock JWT is fine)
- Integration with real banks or PSPs
- Multi-tenancy
- LGPD compliance implementation
- GraalVM native for Spring (documented as out of scope in design.md)
