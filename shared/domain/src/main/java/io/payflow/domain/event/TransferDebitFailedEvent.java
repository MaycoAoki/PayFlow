package io.payflow.domain.event;

import io.payflow.domain.model.TransferId;

import java.time.Instant;

/**
 * Raised when the debit step of a Transfer fails (e.g. insufficient funds).
 */
public record TransferDebitFailedEvent(
        TransferId transferId,
        String reason,
        Instant occurredAt
) implements DomainEvent {

    public TransferDebitFailedEvent {
        if (transferId == null) throw new IllegalArgumentException("transferId must not be null");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
    }

    @Override
    public int eventVersion() {
        return 1;
    }
}
