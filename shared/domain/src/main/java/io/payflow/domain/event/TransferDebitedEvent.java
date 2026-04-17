package io.payflow.domain.event;

import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when the source account has been successfully debited for a transfer.
 */
public record TransferDebitedEvent(
        TransferId transferId,
        AccountId sourceAccountId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {

    public TransferDebitedEvent {
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (sourceAccountId == null) throw new IllegalArgumentException("sourceAccountId must not be null");
        if (amount == null) throw new IllegalArgumentException("amount must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
