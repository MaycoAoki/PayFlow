package io.payflow.domain.event;

import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when a Transfer has completed successfully (both debit and credit confirmed).
 */
public record TransferCompletedEvent(
        TransferId transferId,
        Instant occurredAt
) implements DomainEvent {

    public TransferCompletedEvent {
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
