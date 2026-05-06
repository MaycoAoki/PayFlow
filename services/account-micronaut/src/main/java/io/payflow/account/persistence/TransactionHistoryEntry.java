package io.payflow.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_history")
public class TransactionHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "transfer_id", length = 36)
    private String transferId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TransactionHistoryEntry() {}

    public TransactionHistoryEntry(String accountId, String eventType, BigDecimal amount,
                                    String currency, String transferId, Instant occurredAt) {
        this.accountId = accountId;
        this.eventType = eventType;
        this.amount = amount;
        this.currency = currency;
        this.transferId = transferId;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getEventType() { return eventType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getTransferId() { return transferId; }
    public Instant getOccurredAt() { return occurredAt; }
}
