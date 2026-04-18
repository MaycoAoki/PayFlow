package io.payflow.transfer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "status_code", nullable = false)
    private short statusCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String idempotencyKey, String aggregateId, String responseBody,
                              short statusCode) {
        this.idempotencyKey = idempotencyKey;
        this.aggregateId = aggregateId;
        this.responseBody = responseBody;
        this.statusCode = statusCode;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(24 * 3600);
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getAggregateId() { return aggregateId; }
    public String getResponseBody() { return responseBody; }
    public short getStatusCode() { return statusCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
