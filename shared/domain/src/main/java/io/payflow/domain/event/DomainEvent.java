package io.payflow.domain.event;

import java.time.Instant;

/**
 * Marker interface for all domain events.
 *
 * <p>All domain events are immutable and carry a schema version ({@code eventVersion})
 * for forward compatibility.
 */
public interface DomainEvent {

    /**
     * The wall-clock time at which the event occurred.
     */
    Instant occurredAt();

    /**
     * Schema version of this event. Starts at 1.
     */
    default int eventVersion() {
        return 1;
    }
}
