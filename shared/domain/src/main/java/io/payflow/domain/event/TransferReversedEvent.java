package io.payflow.domain.event;

import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when a Transfer has been fully reversed (debit compensation completed).
 */
public record TransferReversedEvent(
        TransferId transferId,
        Instant occurredAt
) implements DomainEvent {

    public TransferReversedEvent {
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
