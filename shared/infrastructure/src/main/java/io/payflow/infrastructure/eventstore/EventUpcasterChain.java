package io.payflow.infrastructure.eventstore;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.payflow.domain.event.DomainEvent;

import java.util.List;

/**
 * Applies a list of {@link EventUpcaster}s in order until one returns a non-null result.
 *
 * <p>If no upcaster handles the event the chain returns {@code null}, which tells
 * the caller to fall back to direct Jackson deserialization.
 */
public class EventUpcasterChain {

    private final List<EventUpcaster> upcasters;

    public EventUpcasterChain(List<EventUpcaster> upcasters) {
        this.upcasters = List.copyOf(upcasters);
    }

    /**
     * Runs every registered upcaster in order.
     *
     * @return the first non-null result, or {@code null} if no upcaster handled the event
     */
    public DomainEvent apply(String eventType, int fromVersion, ObjectNode payload) {
        for (EventUpcaster upcaster : upcasters) {
            DomainEvent result = upcaster.upcast(eventType, fromVersion, payload);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
