package io.payflow.domain.model;

import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.DomainEvent;
import io.payflow.domain.event.MoneyCreditReversedEvent;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;

/**
 * Account aggregate root.
 *
 * <p>Reconstituted purely by replaying {@link DomainEvent}s from an {@link #empty()} shell.
 * Each call to {@link #apply(DomainEvent)} increments the {@code version} counter, which
 * maps to the event sequence number stored in the event store and is used for optimistic
 * locking (expected version checks on append).
 *
 * <p><b>Idempotency note:</b> the domain aggregate itself does not deduplicate events.
 * Deduplication is guaranteed by the event store — events are written once and replayed
 * in strict sequence order. The {@code version} field therefore reflects the count of
 * events that have been applied. Tests that verify reconstruction simply replay the
 * canonical sequence and assert the final state.
 */
public final class Account {

    private AccountId accountId;
    private String ownerId;
    private Money balance;
    private AccountStatus status;
    private long version;

    // Private constructor — use empty() + apply() to build state
    private Account() {}

    /**
     * Returns an uninitialised shell with version 0, suitable as the starting point
     * for event replay.
     */
    public static Account empty() {
        return new Account();
    }

    /**
     * Applies a domain event, updating aggregate state and incrementing version.
     *
     * @param event the domain event to apply
     * @throws IllegalArgumentException if the event type is unrecognised
     */
    public void apply(DomainEvent event) {
        if (event instanceof AccountCreatedEvent e) {
            handle(e);
        } else if (event instanceof MoneyDepositedEvent e) {
            handle(e);
        } else if (event instanceof MoneyDebitedEvent e) {
            handle(e);
        } else if (event instanceof MoneyDebitReversedEvent e) {
            handle(e);
        } else if (event instanceof MoneyCreditedEvent e) {
            handle(e);
        } else if (event instanceof MoneyCreditReversedEvent e) {
            handle(e);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
        }
        version++;
    }

    // -------------------------------------------------------------------------
    // Private event handlers
    // -------------------------------------------------------------------------

    private void handle(AccountCreatedEvent event) {
        this.accountId = event.accountId();
        this.ownerId = event.ownerId();
        this.balance = event.initialBalance();
        this.status = AccountStatus.ACTIVE;
    }

    private void handle(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.amount());
    }

    private void handle(MoneyDebitedEvent event) {
        this.balance = this.balance.subtract(event.amount());
    }

    private void handle(MoneyDebitReversedEvent event) {
        this.balance = this.balance.add(event.amount());
    }

    private void handle(MoneyCreditedEvent event) {
        this.balance = this.balance.add(event.amount());
    }

    private void handle(MoneyCreditReversedEvent event) {
        this.balance = this.balance.subtract(event.amount());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public AccountId accountId() {
        return accountId;
    }

    public String ownerId() {
        return ownerId;
    }

    public Money balance() {
        return balance;
    }

    public AccountStatus status() {
        return status;
    }

    public long version() {
        return version;
    }
}
