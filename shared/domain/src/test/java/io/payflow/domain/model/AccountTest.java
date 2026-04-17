package io.payflow.domain.model;

import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.MoneyCreditReversedEvent;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    private static final AccountId ACCOUNT_ID = AccountId.generate();
    private static final TransferId TRANSFER_ID = TransferId.generate();
    private static final String OWNER_ID = "owner-1";
    private static final Money INITIAL_BALANCE = Money.of("500.00", "USD");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private AccountCreatedEvent createdEvent;

    @BeforeEach
    void setUp() {
        createdEvent = new AccountCreatedEvent(ACCOUNT_ID, OWNER_ID, INITIAL_BALANCE, NOW);
    }

    // -------------------------------------------------------------------------
    // Creation
    // -------------------------------------------------------------------------

    @Test
    void accountIsActiveAfterCreation() {
        Account account = Account.empty();
        account.apply(createdEvent);

        assertThat(account.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(account.ownerId()).isEqualTo(OWNER_ID);
        assertThat(account.balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.version()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Deposit
    // -------------------------------------------------------------------------

    @Test
    void balanceIncreasesOnMoneyDepositedEvent() {
        Account account = Account.empty();
        account.apply(createdEvent);

        Money deposit = Money.of("100.00", "USD");
        account.apply(new MoneyDepositedEvent(ACCOUNT_ID, deposit, NOW));

        assertThat(account.balance()).isEqualTo(Money.of("600.00", "USD"));
        assertThat(account.version()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Debit
    // -------------------------------------------------------------------------

    @Test
    void balanceDecreasesOnMoneyDebitedEvent() {
        Account account = Account.empty();
        account.apply(createdEvent);

        Money debit = Money.of("200.00", "USD");
        account.apply(new MoneyDebitedEvent(ACCOUNT_ID, TRANSFER_ID, debit, NOW));

        assertThat(account.balance()).isEqualTo(Money.of("300.00", "USD"));
        assertThat(account.version()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Debit reversal
    // -------------------------------------------------------------------------

    @Test
    void balanceRestoredOnMoneyDebitReversedEvent() {
        Account account = Account.empty();
        account.apply(createdEvent);

        Money debit = Money.of("200.00", "USD");
        account.apply(new MoneyDebitedEvent(ACCOUNT_ID, TRANSFER_ID, debit, NOW));
        account.apply(new MoneyDebitReversedEvent(ACCOUNT_ID, TRANSFER_ID, debit, NOW));

        assertThat(account.balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(account.version()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------------
    // Credit
    // -------------------------------------------------------------------------

    @Test
    void balanceIncreasesOnMoneyCreditedEvent() {
        Account account = Account.empty();
        account.apply(createdEvent);

        Money credit = Money.of("150.00", "USD");
        account.apply(new MoneyCreditedEvent(ACCOUNT_ID, TRANSFER_ID, credit, NOW));

        assertThat(account.balance()).isEqualTo(Money.of("650.00", "USD"));
        assertThat(account.version()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Credit reversal
    // -------------------------------------------------------------------------

    @Test
    void balanceDecreasesOnMoneyCreditReversedEvent() {
        Account account = Account.empty();
        account.apply(createdEvent);

        Money credit = Money.of("150.00", "USD");
        account.apply(new MoneyCreditedEvent(ACCOUNT_ID, TRANSFER_ID, credit, NOW));
        account.apply(new MoneyCreditReversedEvent(ACCOUNT_ID, TRANSFER_ID, credit, NOW));

        assertThat(account.balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(account.version()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------------
    // Replay idempotency
    // -------------------------------------------------------------------------

    @Test
    void replayingEventsShouldProduceSameState() {
        List<io.payflow.domain.event.DomainEvent> events = List.of(
                createdEvent,
                new MoneyDepositedEvent(ACCOUNT_ID, Money.of("100.00", "USD"), NOW),
                new MoneyDebitedEvent(ACCOUNT_ID, TRANSFER_ID, Money.of("50.00", "USD"), NOW),
                new MoneyCreditedEvent(ACCOUNT_ID, TRANSFER_ID, Money.of("25.00", "USD"), NOW)
        );

        Account first = Account.empty();
        Account second = Account.empty();

        events.forEach(first::apply);
        events.forEach(second::apply);

        assertThat(first.balance()).isEqualTo(second.balance());
        assertThat(first.version()).isEqualTo(second.version());
        assertThat(first.status()).isEqualTo(second.status());
        // Expected: 500 + 100 - 50 + 25 = 575
        assertThat(first.balance()).isEqualTo(Money.of("575.00", "USD"));
        assertThat(first.version()).isEqualTo(4L);
    }
}
