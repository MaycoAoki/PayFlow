# Micronaut Rules — PayFlow

Stack: Micronaut 4.x | Micronaut HTTP Server (Netty) | Micronaut Data JPA | Micronaut Kafka | Java 21 | GraalVM 21

---

## Core Principle: Compile-Time DI

Micronaut resolves dependency injection at **compile time** via annotation processors — no runtime reflection, no proxies.
This means:
- Faster startup and lower memory vs Spring (that's the whole point of the benchmark)
- Errors in DI wiring appear at **compile time**, not runtime
- AOT (GraalVM native) works out of the box without extra reflection config in most cases

---

## Project Structure

```
services/account-micronaut/
└── src/main/java/payflow/account/
    ├── api/          <- HTTP controllers (adapters/in)
    ├── kafka/        <- Kafka producers & consumers (adapters/out and in)
    ├── persistence/  <- Micronaut Data repositories, EventStore impl (adapters/out)
    └── config/       <- @Factory classes and application.yml
```

Same structure as the Spring adapter — makes diff between stacks easy to read.

---

## Dependency Injection

**DO — constructor injection (identical pattern to Spring):**
```java
@Singleton
public class AccountCommandHandler {
    private final EventStore eventStore;
    private final KafkaEventPublisher publisher;

    public AccountCommandHandler(EventStore eventStore, KafkaEventPublisher publisher) {
        this.eventStore = eventStore;
        this.publisher = publisher;
    }
}
```

**DO — explicit scoping:**
```java
@Singleton   // one instance for app lifetime
@Prototype   // new instance per injection point
@RequestScope // one per HTTP request
```

**DON'T:**
```java
// Micronaut default scope is Prototype — always be explicit
// Never rely on implicit scope
public class AccountCommandHandler { ... }  // missing @Singleton — will create new instance per injection
```

**Key difference from Spring:** Micronaut's default scope is **Prototype**, not Singleton.
Always annotate with `@Singleton` for services and handlers that should be shared.

---

## HTTP Controllers

```java
@Controller("/accounts")
public class AccountController {

    private final AccountCommandHandler handler;

    public AccountController(AccountCommandHandler handler) {
        this.handler = handler;
    }

    @Post
    @Status(HttpStatus.ACCEPTED)
    public Publisher<TransferResponse> initiate(
            @Body @Valid InitiateTransferRequest req,
            @Header("Idempotency-Key") String idempotencyKey) {
        return handler.handle(req, idempotencyKey);
    }
}
```

- Use `@Controller`, `@Get`, `@Post`, `@Put`, `@Delete` — NOT Spring annotations
- Use `@Body` for request body, `@Header` for headers, `@PathVariable` for path params
- Return `Publisher<T>` (RxJava/Reactor) for reactive endpoints — Micronaut HTTP is non-blocking by default
- All write endpoints require `Idempotency-Key` header
- Return `202 Accepted` for saga-initiated async operations

---

## Configuration

**application.yml:**
```yaml
payflow:
  kafka:
    topics:
      account-events: payflow.account.events
      transfer-commands: payflow.transfer.commands
  idempotency:
    ttl-hours: 24

micronaut:
  application:
    name: account-micronaut
  server:
    port: 8081
```

**Type-safe config binding:**
```java
@ConfigurationProperties("payflow.kafka.topics")
public record KafkaTopicsConfig(String accountEvents, String transferCommands) {}
```

**Profiles:**
```yaml
# application-dev.yml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/payflow
```

- Activate with `micronaut.environments=dev` or `MICRONAUT_ENVIRONMENTS=dev` env var
- Test profile is activated automatically when running tests
- Never hardcode topic names, connection strings, or credentials in code

---

## Error Handling

```java
@Produces
@Singleton
public class GlobalErrorHandler implements ExceptionHandler<ConcurrencyConflictException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConcurrencyConflictException ex) {
        return HttpResponse.status(HttpStatus.CONFLICT)
                           .body(new ErrorResponse("CONCURRENCY_CONFLICT", ex.getMessage()));
    }
}
```

- Implement `ExceptionHandler<E, R>` for each domain exception type
- Register with `@Produces @Singleton` — Micronaut discovers them at compile time
- Never put try/catch in controllers

---

## Kafka (Micronaut Kafka)

**Producer:**
```java
@KafkaClient
public interface AccountEventProducer {

    @Topic("${payflow.kafka.topics.account-events}")
    void send(@KafkaKey String accountId, String eventPayload);
}
```

**Consumer — manual offset commit is MANDATORY:**
```java
@KafkaListener(groupId = "transfer-service", offsetReset = OffsetReset.EARLIEST)
public class AccountEventConsumer {

    @Topic("${payflow.kafka.topics.account-events}")
    public void receive(ConsumerRecord<String, String> record, Consumer<String, String> kafkaConsumer) {
        try {
            handler.handle(deserialize(record.value()));
            kafkaConsumer.commitSync();
        } catch (NonRetryableException e) {
            sendToDlq(record);
            kafkaConsumer.commitSync();
        }
    }
}
```

- Never use auto-commit — financial systems require explicit offset management
- Non-retryable failures go to `payflow.dlq` — never drop messages
- Micronaut Kafka `@KafkaClient` interfaces are generated at compile time — no runtime proxies

---

## Micronaut Data JPA

```java
@Repository
public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long> {

    List<EventStoreEntry> findByAggregateIdOrderBySequenceNumAsc(UUID aggregateId);

    @Query("SELECT e FROM EventStoreEntry e WHERE e.aggregateId = :id AND e.sequenceNum >= :from ORDER BY e.sequenceNum")
    List<EventStoreEntry> findSince(UUID id, long from);
}
```

**Key differences from Spring Data:**
- Micronaut Data uses **compile-time query generation** — queries are validated at build time, not startup
- Invalid JPQL = compile error, not a startup exception
- No `JpaRepository` — extend `io.micronaut.data.jpa.repository.JpaRepository`
- Use `@Transactional` from `io.micronaut.transaction.annotation.Transactional`, NOT `jakarta.transaction`

**JPA is blocking — wrap in reactive publisher:**
```java
public Publisher<Void> append(AggregateId id, List<DomainEvent> events, long expectedVersion) {
    return Mono.fromCallable(() -> doInsert(id, events, expectedVersion))
               .subscribeOn(Schedulers.boundedElastic())
               .then();
}
```

---

## Transactions

```java
import io.micronaut.transaction.annotation.Transactional;

@Transactional
public void appendAndPublish(AggregateId id, List<DomainEvent> events, long expectedVersion) {
    eventStoreRepository.insertAll(id, events, expectedVersion);
    events.forEach(kafkaProducer::send);
}
```

- Import `@Transactional` from `io.micronaut.transaction.annotation` — NOT Jakarta or Spring
- Keep transactions short — never call external services inside
- Use `@Transactional(readOnly = true)` on query methods

---

## GraalVM Native Image (Micronaut advantage)

Micronaut is designed for GraalVM native — most features work without extra config.
When native image fails:

**DO:**
- Add `@Introspected` to POJOs that need reflection (DTOs, config classes)
- Use `@Serdeable` for JSON serialization instead of Jackson defaults
- Check `reflect-config.json` for third-party libraries that use reflection

**DON'T:**
- Use dynamic proxies or runtime reflection in adapter code
- Use Spring-style `BeanFactory.getBean()` patterns
- Rely on classpath scanning at runtime

```java
@Introspected  // enables compile-time introspection for native image
@Serdeable     // enables compile-time JSON serialization
public record AccountResponse(UUID accountId, BigDecimal balance, String currency) {}
```

---

## Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(AccountCommandHandler.class);

log.info("Account created accountId={}", accountId);   // DO
log.info("Account created: " + accountId);              // DON'T
```

Same rules as Spring stack — SLF4J, parameterized messages, no sensitive data.

---

## Testing

**Unit tests — same as Spring, pure Java, no framework:**
```java
class AccountTest {
    @Test
    void should_debit_account() {
        var account = Account.reconstitute(List.of(...));
        var events = account.debit(Money.of("50.00", "BRL"), transferId);
        assertThat(events).hasSize(1);
    }
}
```

**Controller tests:**
```java
@MicronautTest
class AccountControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(AccountCommandHandler.class)
    AccountCommandHandler mockHandler() {
        return mock(AccountCommandHandler.class);
    }
}
```

**Integration tests with Testcontainers:**
```java
@MicronautTest
@Testcontainers
class TransferSagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
}
```

Use `@MicronautTest` — not `@SpringBootTest`. Context starts once per test class by default (fast).

---

## Anti-Patterns — Never Do These

| Anti-pattern | Why it's banned |
|---|---|
| Missing `@Singleton` on services | Micronaut default is Prototype — creates new instance per injection |
| Spring annotations in Micronaut code | `@Autowired`, `@Component`, `@Service` do nothing — silently ignored |
| `@Transactional` from Jakarta/Spring in Micronaut | Must use `io.micronaut.transaction.annotation.Transactional` |
| Runtime reflection in adapter code | Breaks GraalVM native image compilation |
| Auto-commit Kafka consumers | Data loss on consumer crash |
| `@Entity` in `shared/domain` | Couples domain to JPA, breaks hexagonal contract |
| `double`/`float` for monetary amounts | Floating point errors in financial calculations |
| Raw `UUID` in domain method signatures | Use typed wrappers `AccountId`, `TransferId` |
| Blocking calls in Netty thread | Wrap JPA calls in `Schedulers.boundedElastic()` |
| Mocking the database in integration tests | Masks real integration bugs |
