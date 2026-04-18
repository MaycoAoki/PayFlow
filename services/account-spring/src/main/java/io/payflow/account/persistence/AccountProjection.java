package io.payflow.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_projections")
public class AccountProjection {

    @Id
    @Column(name = "account_id", length = 36)
    private String accountId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountProjection() {}

    public AccountProjection(String accountId, String ownerId, BigDecimal balance,
                              String currency, String status, long version) {
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.version = version;
        this.updatedAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public String getOwnerId() { return ownerId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void applyDeposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.version++;
        this.updatedAt = Instant.now();
    }
}
