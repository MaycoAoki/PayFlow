package io.payflow.domain.port;

import io.payflow.domain.event.DomainEvent;

import java.util.List;

/**
 * Port (output) for persisting and loading domain events.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Optimistic concurrency via {@code expectedVersion} — if the current stream version
 *       does not match, an exception must be thrown.</li>
 *   <li>Assigning monotonically increasing sequence numbers to appended events.</li>
 *   <li>Guaranteeing that {@link #loadEvents} returns events in append order.</li>
 * </ul>
 *
 * <p>This interface has ZERO framework imports.
 */
public interface EventStore {

    /**
     * Appends {@code events} to the stream identified by {@code aggregateId}.
     *
     * @param aggregateId      the unique aggregate identifier
     * @param aggregateType    the logical type name of the aggregate (e.g. "Account")
     * @param expectedVersion  the stream version the caller expects to find; used for
     *                         optimistic concurrency control
     * @param events           the ordered list of events to append (must not be empty)
     * @throws OptimisticLockException if the current version does not match
     *                                 {@code expectedVersion}
     */
    void append(String aggregateId, String aggregateType, long expectedVersion, List<DomainEvent> events);

    /**
     * Loads all events for the given aggregate stream in sequence order.
     *
     * @param aggregateId the unique aggregate identifier
     * @return ordered list of events; empty list if the stream does not exist
     */
    List<DomainEvent> loadEvents(String aggregateId);

    /**
     * Loads events for the given aggregate stream starting from (inclusive) the given
     * sequence number.
     *
     * @param aggregateId    the unique aggregate identifier
     * @param fromSequence   the sequence number to start from (1-based)
     * @return ordered list of events from the given sequence; empty list if none found
     */
    List<DomainEvent> loadEventsSince(String aggregateId, long fromSequence);
}
