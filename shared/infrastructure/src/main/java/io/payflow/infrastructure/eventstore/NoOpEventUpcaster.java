package io.payflow.infrastructure.eventstore;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.payflow.domain.event.DomainEvent;

/**
 * Default no-op upcaster that always returns {@code null}, signalling that it
 * does not handle any event type / version combination.
 *
 * <p>The {@link EventUpcasterChain} falls back to direct Jackson deserialization
 * when no upcaster returns a non-null result.
 */
public class NoOpEventUpcaster implements EventUpcaster {

    @Override
    public DomainEvent upcast(String eventType, int fromVersion, ObjectNode payload) {
        return null;
    }
}
