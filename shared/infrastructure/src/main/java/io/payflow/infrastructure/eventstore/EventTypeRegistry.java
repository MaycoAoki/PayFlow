package io.payflow.infrastructure.eventstore;

import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.event.MoneyCreditReversedEvent;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;
import io.payflow.domain.event.TransferCompletedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferCreditedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.domain.event.TransferDebitedEvent;
import io.payflow.domain.event.TransferInitiatedEvent;
import io.payflow.domain.event.TransferReversedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps event type names (simple class names) to their concrete {@link DomainEvent} classes.
 *
 * <p>Used during event deserialization to determine the target type for Jackson.
 */
public class EventTypeRegistry {

    private final Map<String, Class<? extends DomainEvent>> registry = new HashMap<>();

    public void register(String eventType, Class<? extends DomainEvent> clazz) {
        registry.put(eventType, clazz);
    }

    /**
     * Returns the class registered for the given event type name.
     *
     * @throws IllegalArgumentException if the type is not registered
     */
    public Class<? extends DomainEvent> get(String eventType) {
        Class<? extends DomainEvent> clazz = registry.get(eventType);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        return clazz;
    }

    /**
     * Creates and returns a registry pre-populated with all 13 domain events
     * (6 Account + 7 Transfer).
     */
    public static EventTypeRegistry defaultRegistry() {
        EventTypeRegistry reg = new EventTypeRegistry();

        // Account events (6)
        reg.register("AccountCreatedEvent", AccountCreatedEvent.class);
        reg.register("MoneyDepositedEvent", MoneyDepositedEvent.class);
        reg.register("MoneyDebitedEvent", MoneyDebitedEvent.class);
        reg.register("MoneyDebitReversedEvent", MoneyDebitReversedEvent.class);
        reg.register("MoneyCreditedEvent", MoneyCreditedEvent.class);
        reg.register("MoneyCreditReversedEvent", MoneyCreditReversedEvent.class);

        // Transfer events (7)
        reg.register("TransferInitiatedEvent", TransferInitiatedEvent.class);
        reg.register("TransferDebitedEvent", TransferDebitedEvent.class);
        reg.register("TransferCreditedEvent", TransferCreditedEvent.class);
        reg.register("TransferCompletedEvent", TransferCompletedEvent.class);
        reg.register("TransferDebitFailedEvent", TransferDebitFailedEvent.class);
        reg.register("TransferCreditFailedEvent", TransferCreditFailedEvent.class);
        reg.register("TransferReversedEvent", TransferReversedEvent.class);

        return reg;
    }
}
