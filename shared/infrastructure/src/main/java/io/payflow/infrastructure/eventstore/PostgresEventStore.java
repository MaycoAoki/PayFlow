package io.payflow.infrastructure.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.port.EventStore;
import io.payflow.domain.port.OptimisticLockException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL-backed implementation of {@link EventStore} using plain JDBC.
 *
 * <p>No Spring, no Micronaut — only {@link DataSource} and Jackson.
 * Safe to use from any JVM framework that can provide a {@code DataSource}.
 */
public class PostgresEventStore implements EventStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_store
                (aggregate_id, aggregate_type, event_type, event_version, sequence_num, payload, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT event_type, event_version, payload
            FROM event_store
            WHERE aggregate_id = ?
            ORDER BY sequence_num ASC
            """;

    private static final String SELECT_SINCE_SQL = """
            SELECT event_type, event_version, payload
            FROM event_store
            WHERE aggregate_id = ? AND sequence_num >= ?
            ORDER BY sequence_num ASC
            """;

    // SQL state code for unique constraint violation (PostgreSQL)
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final EventTypeRegistry registry;
    private final EventUpcasterChain upcasterChain;

    public PostgresEventStore(DataSource dataSource, ObjectMapper objectMapper, EventTypeRegistry registry) {
        this(dataSource, objectMapper, registry, new EventUpcasterChain(List.of(new NoOpEventUpcaster())));
    }

    public PostgresEventStore(DataSource dataSource, ObjectMapper objectMapper,
                               EventTypeRegistry registry, EventUpcasterChain upcasterChain) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.upcasterChain = upcasterChain;
    }

    @Override
    public void append(String aggregateId, String aggregateType, long expectedVersion, List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

                for (int i = 0; i < events.size(); i++) {
                    DomainEvent event = events.get(i);
                    long sequenceNum = expectedVersion + i + 1;

                    // Build payload: serialize event + inject _eventVersion
                    ObjectNode payloadNode = objectMapper.valueToTree(event);
                    payloadNode.put("_eventVersion", event.eventVersion());
                    String payloadJson = objectMapper.writeValueAsString(payloadNode);

                    ps.setString(1, aggregateId);
                    ps.setString(2, aggregateType);
                    ps.setString(3, event.getClass().getSimpleName());
                    ps.setShort(4, (short) event.eventVersion());
                    ps.setLong(5, sequenceNum);
                    ps.setString(6, payloadJson);
                    ps.setTimestamp(7, Timestamp.from(event.occurredAt()));
                    ps.addBatch();
                }

                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                if (isUniqueViolation(e)) {
                    throw new OptimisticLockException(aggregateId, expectedVersion, expectedVersion + 1);
                }
                throw new RuntimeException("Failed to append events for aggregate: " + aggregateId, e);
            }
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize events for aggregate " + aggregateId, e);
        }
    }

    @Override
    public List<DomainEvent> loadEvents(String aggregateId) {
        return loadEventsWithQuery(SELECT_ALL_SQL, aggregateId, null);
    }

    @Override
    public List<DomainEvent> loadEventsSince(String aggregateId, long fromSequence) {
        return loadEventsWithQuery(SELECT_SINCE_SQL, aggregateId, fromSequence);
    }

    private List<DomainEvent> loadEventsWithQuery(String sql, String aggregateId, Long fromSequence) {
        List<DomainEvent> events = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, aggregateId);
            if (fromSequence != null) {
                ps.setLong(2, fromSequence);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String eventType = rs.getString("event_type");
                    int eventVersion = rs.getShort("event_version");
                    String payloadJson = rs.getString("payload");

                    DomainEvent event = deserializeEvent(eventType, eventVersion, payloadJson);
                    events.add(event);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load events for aggregate " + aggregateId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize events for aggregate " + aggregateId, e);
        }

        return events;
    }

    private DomainEvent deserializeEvent(String eventType, int storedVersion, String payloadJson) throws Exception {
        ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(payloadJson);
        Class<? extends DomainEvent> eventClass = registry.get(eventType);
        if (eventClass == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }

        // Always run upcaster chain; upcasters self-select by (eventType, fromVersion)
        DomainEvent upcasted = upcasterChain.apply(eventType, storedVersion, payloadNode.deepCopy());
        if (upcasted != null) {
            return upcasted;
        }

        payloadNode.remove("_eventVersion");
        return objectMapper.treeToValue(payloadNode, eventClass);
    }

    /**
     * Checks if the SQLException (or any chained next exception) represents a unique violation.
     * {@code BatchUpdateException} wraps the real cause in {@code getNextException()}.
     */
    private boolean isUniqueViolation(SQLException e) {
        SQLException current = e;
        while (current != null) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(current.getSQLState())) {
                return true;
            }
            current = current.getNextException();
        }
        // Also check Java cause chain
        Throwable cause = e.getCause();
        if (cause instanceof SQLException sqlCause) {
            return isUniqueViolation(sqlCause);
        }
        return false;
    }
}
