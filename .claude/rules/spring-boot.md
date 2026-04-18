# Spring Boot Rules — PayFlow

Stack: Spring Boot 3.3 | Spring WebFlux (reactive) | Spring Data JPA | Spring Kafka | Java 21

---

## Project Structure

```
services/account-spring/
└── src/main/java/payflow/account/
    ├── api/          ← REST controllers (adapters/in)
    ├── kafka/        ← Kafka producers & consumers (adapters/out and in)
    ├── persistence/  ← JPA entities, repositories, EventStore impl (adapters/out)
    └── config/       ← Spring @Configuration classes
```

- Organize by adapter type, NOT by layer (no `controller/`, `service/`, `repository/` folders at root)
- Never put domain logic in the Spring layer — delegate to `shared/domain`

---

## Dependency Injection

**DO — constructor injection:**
```java
@Service
public class AccountCommandHandler {
    private final EventStore eventStore;
    private final KafkaEventPublisher publisher;

    public AccountCommandHandler(EventStore eventStore, KafkaEventPublisher publisher) {
        this.eventStore = eventStore;
        this.publisher = publisher;
    }
}
```

**DON'T — field injection:**
```java
@Autowired  // hides dependencies, untestable without Spring context
private EventStore eventStore;
```

- Always `private final` on injected fields
- `@Autowired` on single constructors is redundant since Spring 4.3 — omit it

---

## Reactive (WebFlux) — MANDATORY

This project uses WebFlux, NOT Spring MVC. Never mix blocking and non-blocking code.

**DO:**
```java
@GetMapping("/accounts/{id}")
public Mono<ResponseEntity<AccountResponse>> getAccount(@PathVariable UUID id) {
    return accountQueryService.findById(id)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

**DON'T:**
- Call `.block()` anywhere in production code
- Use `void` return types in reactive endpoints
- Call JPA directly from a reactive chain without offloading to `Schedulers.boundedElastic()`

**JPA inside WebFlux (JPA is blocking):**
```java
public Mono<Void> append(AggregateId id, List<DomainEvent> events, long expectedVersion) {
    return Mono.fromCallable(() -> doInsert(id, events, expectedVersion))
               .subscribeOn(Schedulers.boundedElastic())
               .then();
}
```

---

## Configuration

```yaml
# application.yml
payflow:
  kafka:
    topics:
      account-events: payflow.account.events
      transfer-commands: payflow.transfer.commands
  idempotency:
    ttl-hours: 24
```

```java
@ConfigurationProperties("payflow.kafka")
public record KafkaProperties(Topics topics) {
    public record Topics(String accountEvents, String transferCommands) {}
}
```

- Never hardcode topic names, TTLs, or connection strings in code
- Use `@ConfigurationProperties` for nested config — avoid `@Value` for complex structures
- Profiles: `application.yml` (defaults), `application-dev.yml` (local), `application-test.yml` (Testcontainers)
- Never commit `application-prod.yml` with real credentials

---

## Controllers (REST Adapters)

- Controllers do ONE thing: deserialize -> call handler -> serialize response
- Always use DTOs — NEVER expose JPA entities directly
- Validate with `@Valid` + Bean Validation on all request DTOs
- All write endpoints require `Idempotency-Key` header
- Return `202 Accepted` for saga-initiated async operations

```java
@RestController
@RequestMapping("/transfers")
@Validated
public class TransferController {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<TransferResponse> initiate(
            @RequestBody @Valid InitiateTransferRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return handler.handle(req, idempotencyKey);
    }
}
```

---

## Global Error Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConcurrencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<ErrorResponse> handleConcurrencyConflict(ConcurrencyConflictException ex) {
        return Mono.just(new ErrorResponse("CONCURRENCY_CONFLICT", ex.getMessage()));
    }
}
```

- All exception mapping lives in ONE `@RestControllerAdvice` — no try/catch in controllers
- Map domain exceptions to HTTP codes here, never in the domain itself

---

## Kafka (Spring Kafka)

**Producer — partition key is always accountId:**
```java
kafkaTemplate.send(topic, accountId.toString(), serialize(event));
```

**Consumer — manual acknowledgment is MANDATORY:**
```java
@KafkaListener(topics = "${payflow.kafka.topics.account-events}", groupId = "...")
public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
        handler.handle(deserialize(record.value()));
        ack.acknowledge();
    } catch (NonRetryableException e) {
        sendToDlq(record);
        ack.acknowledge();
    }
    // retryable exceptions: do NOT ack — let Kafka retry
}
```

- Never use auto-commit — financial systems require manual acknowledgment
- Non-retryable failures go to `payflow.dlq` — never drop messages silently
- Configure `max.poll.records` and `max.poll.interval.ms` explicitly

---

## Transactions

- `@Transactional` belongs in the persistence adapter, NOT in the domain
- Keep transactions short — never call external services inside `@Transactional`
- Use `@Transactional(readOnly = true)` on all query methods

```java
@Transactional
public void appendAndPublish(AggregateId id, List<DomainEvent> events, long expectedVersion) {
    eventStore.append(id, events, expectedVersion);
    events.forEach(kafkaPublisher::publishSync);
}
```

---

## Logging

```java
private static final Logger log = LoggerFactory.getLogger(AccountCommandHandler.class);

log.info("Transfer initiated transferId={} amount={}", transferId, amount); // DO
log.info("Transfer initiated: " + transferId);                              // DON'T
```

- SLF4J only — never `System.out.println`
- Parameterized messages always — string concatenation is evaluated even when log level is off
- `INFO` for business events, `DEBUG` for internal state — never log sensitive financial data

---

## Testing

| Layer | Tool | Rule |
|---|---|---|
| Domain unit | JUnit 5, no mocks | Pure Java — no Spring context needed |
| Controller | `@WebFluxTest` | Mock only command/query handlers |
| Integration | `@SpringBootTest` + Testcontainers | Real Postgres and Kafka — no DB mocks |
| Saga | `@SpringBootTest` + Testcontainers | All state machine paths including compensation |

- Never mock `EventStore` in saga integration tests — that is the component under test
- Saga tests MUST cover: happy path, debit failure, credit failure -> reversal, reversal retry (idempotency)

---

## Anti-Patterns — Never Do These

| Anti-pattern | Why it's banned |
|---|---|
| `@Entity` in `shared/domain` | Couples domain to JPA, breaks hexagonal contract |
| `.block()` in reactive chain | Blocks Netty thread — kills throughput under load |
| `@Autowired` field injection | Hides dependencies, breaks unit tests without Spring |
| try/catch in controllers | Bypasses `@RestControllerAdvice` — inconsistent error responses |
| Mocking the database in integration tests | Masks real integration bugs |
| String concatenation in log calls | Evaluated at runtime even when log level is OFF |
| Raw `UUID` in domain method signatures | Use typed wrappers `AccountId`, `TransferId` |
| `@Transactional` in `shared/domain` | Domain has no framework dependency |
| Auto-commit Kafka consumers | Data loss on consumer crash |
| `double` or `float` for monetary amounts | Floating point errors in financial calculations |
