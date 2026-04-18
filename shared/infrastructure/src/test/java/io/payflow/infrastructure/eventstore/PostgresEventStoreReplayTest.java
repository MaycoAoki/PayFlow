package io.payflow.infrastructure.eventstore;

import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;
import io.payflow.domain.model.Account;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.AccountStatus;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresEventStoreReplayTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresEventStore eventStore;

    @BeforeAll
    static void runMigrations() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            String v1 = Files.readString(Paths.get("src/main/resources/db/migration/V1__create_event_store.sql"));
            String v2 = Files.readString(Paths.get("src/main/resources/db/migration/V2__create_idempotency_keys.sql"));
            stmt.execute(v1);
            stmt.execute(v2);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());

        ObjectMapper objectMapper = PayFlowJacksonModule.createObjectMapper();

        eventStore = new PostgresEventStore(ds, objectMapper, EventTypeRegistry.defaultRegistry());
    }

    @Test
    void appendAndReplay_reconstructsAccountState() throws Exception {
        String aggregateId = UUID.randomUUID().toString();
        AccountId accountId = AccountId.of(UUID.fromString(aggregateId));
        Currency usd = Currency.getInstance("USD");

        // Event 1: AccountCreatedEvent (initialBalance = 0)
        AccountCreatedEvent created = new AccountCreatedEvent(
                accountId,
                "owner-replay",
                new Money(BigDecimal.ZERO, usd),
                Instant.now()
        );

        // Events 2,3,4: MoneyDepositedEvent x3 — +100 each = 300 total
        MoneyDepositedEvent deposit1 = new MoneyDepositedEvent(accountId, new Money(new BigDecimal("100.00"), usd), Instant.now());
        MoneyDepositedEvent deposit2 = new MoneyDepositedEvent(accountId, new Money(new BigDecimal("100.00"), usd), Instant.now());
        MoneyDepositedEvent deposit3 = new MoneyDepositedEvent(accountId, new Money(new BigDecimal("100.00"), usd), Instant.now());

        // Event 5: MoneyDebitedEvent — -50
        TransferId transferId = TransferId.generate();
        MoneyDebitedEvent debited = new MoneyDebitedEvent(accountId, transferId, new Money(new BigDecimal("50.00"), usd), Instant.now());

        // Append all events in sequence
        eventStore.append(aggregateId, "Account", 0, List.of(created));
        eventStore.append(aggregateId, "Account", 1, List.of(deposit1));
        eventStore.append(aggregateId, "Account", 2, List.of(deposit2));
        eventStore.append(aggregateId, "Account", 3, List.of(deposit3));
        eventStore.append(aggregateId, "Account", 4, List.of(debited));

        // Load all events
        List<DomainEvent> events = eventStore.loadEvents(aggregateId);
        assertThat(events).hasSize(5);

        // Reconstruct Account by replaying
        Account account = Account.empty();
        for (DomainEvent event : events) {
            account.apply(event);
        }

        // Assert final state
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.version()).isEqualTo(5);
        assertThat(account.balance()).isEqualTo(new Money(new BigDecimal("250.00"), usd));
        assertThat(account.ownerId()).isEqualTo("owner-replay");
    }

    @Test
    void loadEventsSince_returnsEventsFromGivenSequence() throws Exception {
        String aggregateId = UUID.randomUUID().toString();
        AccountId accountId = AccountId.of(UUID.fromString(aggregateId));
        Currency usd = Currency.getInstance("USD");

        AccountCreatedEvent created = new AccountCreatedEvent(
                accountId,
                "owner-since",
                new Money(BigDecimal.ZERO, usd),
                Instant.now()
        );
        MoneyDepositedEvent deposit1 = new MoneyDepositedEvent(accountId, new Money(new BigDecimal("100.00"), usd), Instant.now());
        MoneyDepositedEvent deposit2 = new MoneyDepositedEvent(accountId, new Money(new BigDecimal("200.00"), usd), Instant.now());

        eventStore.append(aggregateId, "Account", 0, List.of(created));
        eventStore.append(aggregateId, "Account", 1, List.of(deposit1));
        eventStore.append(aggregateId, "Account", 2, List.of(deposit2));

        // Load since sequence 2 — should return deposit1 and deposit2
        List<DomainEvent> events = eventStore.loadEventsSince(aggregateId, 2);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(MoneyDepositedEvent.class);
        assertThat(events.get(1)).isInstanceOf(MoneyDepositedEvent.class);
    }

    @Test
    void loadEvents_nonExistentAggregate_returnsEmptyList() {
        List<DomainEvent> events = eventStore.loadEvents(UUID.randomUUID().toString());
        assertThat(events).isEmpty();
    }
}
