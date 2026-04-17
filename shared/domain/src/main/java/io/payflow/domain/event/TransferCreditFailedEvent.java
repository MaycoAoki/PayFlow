package io.payflow.domain.event;

import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when the credit step of a Transfer fails after a successful debit
 * (triggers the compensating reversal saga).
 */
public record TransferCreditFailedEvent(
        TransferId transferId,
        String reason,
        Instant occurredAt
) implements DomainEvent {

    public TransferCreditFailedEvent {
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
