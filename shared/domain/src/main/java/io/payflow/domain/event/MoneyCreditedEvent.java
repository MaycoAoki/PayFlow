package io.payflow.domain.event;

import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when money is credited to an Account as part of a transfer.
 */
public record MoneyCreditedEvent(
        AccountId accountId,
        TransferId transferId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {

    public MoneyCreditedEvent {
        if (accountId == null) throw new IllegalArgumentException("accountId must not be null");
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
