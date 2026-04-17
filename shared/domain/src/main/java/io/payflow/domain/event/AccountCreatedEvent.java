package io.payflow.domain.event;

import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;

import java.time.Instant;

/**
 * Raised when a new Account is created.
 */
public record AccountCreatedEvent(
        AccountId accountId,
        String ownerId,
        Money initialBalance,
        Instant occurredAt
) implements DomainEvent {

    public AccountCreatedEvent {
        if (accountId == null) throw new IllegalArgumentException("accountId must not be null");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (initialBalance == null) throw new IllegalArgumentException("initialBalance must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
