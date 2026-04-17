package io.payflow.domain.model;

import io.payflow.domain.event.TransferCompletedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferCreditedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.domain.event.TransferDebitedEvent;
import io.payflow.domain.event.TransferInitiatedEvent;
import io.payflow.domain.event.TransferReversedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TransferTest {

    private static final TransferId TRANSFER_ID = TransferId.generate();
    private static final AccountId SOURCE = AccountId.generate();
    private static final AccountId TARGET = AccountId.generate();
    private static final Money AMOUNT = Money.of("250.00", "USD");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private TransferInitiatedEvent initiatedEvent;

    @BeforeEach
    void setUp() {
        initiatedEvent = new TransferInitiatedEvent(TRANSFER_ID, SOURCE, TARGET, AMOUNT, NOW);
    }

    // -------------------------------------------------------------------------
    // Happy path: INITIATED → DEBITING → CREDITING → COMPLETED
    // -------------------------------------------------------------------------

    @Test
    void happyPath() {
        Transfer transfer = Transfer.empty();

        transfer.apply(initiatedEvent);
        assertThat(transfer.status()).isEqualTo(TransferStatus.INITIATED);
        assertThat(transfer.version()).isEqualTo(1L);

        transfer.apply(new TransferDebitedEvent(TRANSFER_ID, SOURCE, AMOUNT, NOW));
        assertThat(transfer.status()).isEqualTo(TransferStatus.DEBITING);
        assertThat(transfer.version()).isEqualTo(2L);

        transfer.apply(new TransferCreditedEvent(TRANSFER_ID, TARGET, AMOUNT, NOW));
        assertThat(transfer.status()).isEqualTo(TransferStatus.CREDITING);
        assertThat(transfer.version()).isEqualTo(3L);

        transfer.apply(new TransferCompletedEvent(TRANSFER_ID, NOW));
        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.version()).isEqualTo(4L);

        assertThat(transfer.transferId()).isEqualTo(TRANSFER_ID);
        assertThat(transfer.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(transfer.targetAccountId()).isEqualTo(TARGET);
        assertThat(transfer.amount()).isEqualTo(AMOUNT);
        assertThat(transfer.sagaState()).isEqualTo("COMPLETED");
    }

    // -------------------------------------------------------------------------
    // Debit failure: INITIATED → DEBITING → FAILED
    // -------------------------------------------------------------------------

    @Test
    void debitFailurePath() {
        Transfer transfer = Transfer.empty();

        transfer.apply(initiatedEvent);
        transfer.apply(new TransferDebitedEvent(TRANSFER_ID, SOURCE, AMOUNT, NOW));

        assertThat(transfer.status()).isEqualTo(TransferStatus.DEBITING);

        transfer.apply(new TransferDebitFailedEvent(TRANSFER_ID, "Insufficient funds", NOW));

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.sagaState()).isEqualTo("FAILED");
        assertThat(transfer.version()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------------
    // Credit failure: INITIATED → DEBITING → CREDITING → REVERSING → REVERSED
    // -------------------------------------------------------------------------

    @Test
    void creditFailurePath() {
        Transfer transfer = Transfer.empty();

        transfer.apply(initiatedEvent);
        transfer.apply(new TransferDebitedEvent(TRANSFER_ID, SOURCE, AMOUNT, NOW));
        transfer.apply(new TransferCreditedEvent(TRANSFER_ID, TARGET, AMOUNT, NOW));

        assertThat(transfer.status()).isEqualTo(TransferStatus.CREDITING);

        transfer.apply(new TransferCreditFailedEvent(TRANSFER_ID, "Target account closed", NOW));
        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSING);

        transfer.apply(new TransferReversedEvent(TRANSFER_ID, NOW));
        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
        assertThat(transfer.sagaState()).isEqualTo("REVERSED");
        assertThat(transfer.version()).isEqualTo(5L);
    }

    // -------------------------------------------------------------------------
    // Reversal idempotency: applying TransferReversedEvent twice doesn't change state
    // -------------------------------------------------------------------------

    @Test
    void reversalIdempotency() {
        Transfer transfer = Transfer.empty();

        transfer.apply(initiatedEvent);
        transfer.apply(new TransferDebitedEvent(TRANSFER_ID, SOURCE, AMOUNT, NOW));
        transfer.apply(new TransferCreditedEvent(TRANSFER_ID, TARGET, AMOUNT, NOW));
        transfer.apply(new TransferCreditFailedEvent(TRANSFER_ID, "Target account closed", NOW));
        transfer.apply(new TransferReversedEvent(TRANSFER_ID, NOW));

        long versionAfterFirst = transfer.version();
        TransferStatus statusAfterFirst = transfer.status();

        // Apply TransferReversedEvent a second time — should be a no-op
        transfer.apply(new TransferReversedEvent(TRANSFER_ID, NOW));

        assertThat(transfer.version()).isEqualTo(versionAfterFirst);
        assertThat(transfer.status()).isEqualTo(statusAfterFirst);
        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
    }

    // -------------------------------------------------------------------------
    // Field accessors sanity
    // -------------------------------------------------------------------------

    @Test
    void emptyTransferHasNullFields() {
        Transfer transfer = Transfer.empty();
        assertThat(transfer.transferId()).isNull();
        assertThat(transfer.status()).isNull();
        assertThat(transfer.version()).isZero();
    }
}
