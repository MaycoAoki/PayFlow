package io.payflow.infrastructure.eventstore;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.payflow.domain.event.DomainEvent;

/**
 * SPI for migrating an event payload from one schema version to the next.
 *
 * <p>Implementations must be pure functions — they receive an immutable
 * {@link ObjectNode} copy and return a (possibly new) node with the payload
 * updated to the next version.
 */
public interface EventUpcaster {

    /**
     * Upcasts an event payload from {@code fromVersion} to the next version.
     *
     * @param eventType   the simple class name of the event
     * @param fromVersion the schema version of the stored payload
     * @param payload     the raw JSON payload as an {@link ObjectNode}
     * @return the upcasted domain event, or {@code null} if this upcaster does not
     *         handle the given event type / version combination
     */
    DomainEvent upcast(String eventType, int fromVersion, ObjectNode payload);
}
