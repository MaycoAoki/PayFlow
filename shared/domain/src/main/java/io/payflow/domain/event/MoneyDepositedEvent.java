package io.payflow.domain.event;

import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;

import java.time.Instant;

/**
 * Raised when money is deposited into an Account.
 */
public record MoneyDepositedEvent(
        AccountId accountId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {

    public MoneyDepositedEvent {
        if (accountId == null) throw new IllegalArgumentException("accountId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
