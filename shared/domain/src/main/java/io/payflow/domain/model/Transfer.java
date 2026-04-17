package io.payflow.domain.model;

import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.event.TransferCompletedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferCreditedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.domain.event.TransferDebitedEvent;
import io.payflow.domain.event.TransferInitiatedEvent;
import io.payflow.domain.event.TransferReversedEvent;

/**
 * Transfer aggregate root — models a point-to-point money transfer saga.
 *
 * <p>State machine:
 * <pre>
 *   INITIATED → DEBITING → CREDITING → COMPLETED
 *   DEBITING  → FAILED
 *   CREDITING → REVERSING → REVERSED
 * </pre>
 *
 * <p>The {@code sagaState} string mirrors the current status as a human-readable label
 * that can be stored in a saga orchestration table without coupling to the enum.
 *
 * <p><b>Idempotency:</b> once a terminal state ({@code COMPLETED}, {@code FAILED},
 * {@code REVERSED}) is reached, subsequent applications of the same terminal event are
 * silently ignored (the version is NOT incremented for a no-op guard). This is sufficient
 * for event-store-driven replay — the store never emits the same sequence position twice.
 * Tests that verify idempotency apply a terminal event twice and assert that the version
 * and status remain unchanged after the second application.
 */
public final class Transfer {

    private TransferId transferId;
    private AccountId sourceAccountId;
    private AccountId targetAccountId;
    private Money amount;
    private TransferStatus status;
    private String sagaState;
    private long version;

    private Transfer() {}

    /**
     * Returns an uninitialised shell with version 0, suitable as the starting point
     * for event replay.
     */
    public static Transfer empty() {
        return new Transfer();
    }

    /**
     * Applies a domain event, updating aggregate state.
     * Version is only incremented when the event causes a real state change.
     *
     * @param event the domain event to apply
     * @throws IllegalArgumentException if the event type is unrecognised
     */
    public void apply(DomainEvent event) {
        if (event instanceof TransferInitiatedEvent e) {
            handle(e);
        } else if (event instanceof TransferDebitedEvent e) {
            handle(e);
        } else if (event instanceof TransferDebitFailedEvent e) {
            handle(e);
        } else if (event instanceof TransferCreditedEvent e) {
            handle(e);
        } else if (event instanceof TransferCreditFailedEvent e) {
            handle(e);
        } else if (event instanceof TransferCompletedEvent e) {
            handle(e);
        } else if (event instanceof TransferReversedEvent e) {
            handle(e);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
        }
    }

    // -------------------------------------------------------------------------
    // Private event handlers
    // -------------------------------------------------------------------------

    private void handle(TransferInitiatedEvent event) {
        this.transferId = event.transferId();
        this.sourceAccountId = event.sourceAccountId();
        this.targetAccountId = event.targetAccountId();
        this.amount = event.amount();
        this.status = TransferStatus.INITIATED;
        this.sagaState = TransferStatus.INITIATED.name();
        version++;
    }

    private void handle(TransferDebitedEvent event) {
        if (this.status != TransferStatus.INITIATED) return; // guard — no version bump
        this.status = TransferStatus.DEBITING;
        this.sagaState = TransferStatus.DEBITING.name();
        version++;
    }

    private void handle(TransferDebitFailedEvent event) {
        if (this.status != TransferStatus.DEBITING) return;
        this.status = TransferStatus.FAILED;
        this.sagaState = TransferStatus.FAILED.name();
        version++;
    }

    private void handle(TransferCreditedEvent event) {
        if (this.status != TransferStatus.DEBITING) return;
        this.status = TransferStatus.CREDITING;
        this.sagaState = TransferStatus.CREDITING.name();
        version++;
    }

    private void handle(TransferCreditFailedEvent event) {
        if (this.status != TransferStatus.CREDITING) return;
        this.status = TransferStatus.REVERSING;
        this.sagaState = TransferStatus.REVERSING.name();
        version++;
    }

    private void handle(TransferCompletedEvent event) {
        // Idempotency guard: already terminal → ignore
        if (this.status == TransferStatus.COMPLETED) return;
        if (this.status != TransferStatus.CREDITING) return;
        this.status = TransferStatus.COMPLETED;
        this.sagaState = TransferStatus.COMPLETED.name();
        version++;
    }

    private void handle(TransferReversedEvent event) {
        // Idempotency guard: already reversed → ignore (no version bump)
        if (this.status == TransferStatus.REVERSED) return;
        if (this.status != TransferStatus.REVERSING) return;
        this.status = TransferStatus.REVERSED;
        this.sagaState = TransferStatus.REVERSED.name();
        version++;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TransferId transferId() {
        return transferId;
    }

    public AccountId sourceAccountId() {
        return sourceAccountId;
    }

    public AccountId targetAccountId() {
        return targetAccountId;
    }

    public Money amount() {
        return amount;
    }

    public TransferStatus status() {
        return status;
    }

    public String sagaState() {
        return sagaState;
    }

    public long version() {
        return version;
    }
}
