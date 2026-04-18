package io.payflow.infrastructure.eventstore;

import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.port.OptimisticLockException;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresEventStoreOptimisticLockTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private javax.sql.DataSource dataSource;
    private ObjectMapper objectMapper;

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
        this.dataSource = ds;

        objectMapper = PayFlowJacksonModule.createObjectMapper();
    }

    @Test
    void concurrentAppend_onlyOneSucceeds_otherThrowsOptimisticLockException() throws Exception {
        String aggregateId = UUID.randomUUID().toString();
        AccountId accountId = AccountId.of(UUID.fromString(aggregateId));

        DomainEvent event1 = new AccountCreatedEvent(
                accountId,
                "owner-1",
                new Money(new BigDecimal("100.00"), Currency.getInstance("USD")),
                Instant.now()
        );
        DomainEvent event2 = new AccountCreatedEvent(
                accountId,
                "owner-2",
                new Money(new BigDecimal("200.00"), Currency.getInstance("USD")),
                Instant.now()
        );

        PostgresEventStore store = new PostgresEventStore(dataSource, objectMapper, EventTypeRegistry.defaultRegistry());

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                store.append(aggregateId, "Account", 0, List.of(event1));
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                failCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedError.set(t);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                store.append(aggregateId, "Account", 0, List.of(event2));
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                failCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedError.set(t);
            }
        });
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        assertThat(unexpectedError.get()).isNull();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // Verify only 1 event in the store
        List<DomainEvent> events = store.loadEvents(aggregateId);
        assertThat(events).hasSize(1);
    }

    @Test
    void sequentialAppend_wrongExpectedVersion_throwsOptimisticLockException() throws Exception {
        String aggregateId = UUID.randomUUID().toString();
        AccountId accountId = AccountId.of(UUID.fromString(aggregateId));

        DomainEvent event = new AccountCreatedEvent(
                accountId,
                "owner-1",
                new Money(new BigDecimal("100.00"), Currency.getInstance("USD")),
                Instant.now()
        );

        PostgresEventStore store = new PostgresEventStore(dataSource, objectMapper, EventTypeRegistry.defaultRegistry());
        store.append(aggregateId, "Account", 0, List.of(event));

        // Try to append again with expectedVersion=0 (wrong — should be 1)
        DomainEvent event2 = new AccountCreatedEvent(
                accountId,
                "owner-duplicate",
                new Money(new BigDecimal("50.00"), Currency.getInstance("USD")),
                Instant.now()
        );

        assertThatThrownBy(() -> store.append(aggregateId, "Account", 0, List.of(event2)))
                .isInstanceOf(OptimisticLockException.class);
    }
}
