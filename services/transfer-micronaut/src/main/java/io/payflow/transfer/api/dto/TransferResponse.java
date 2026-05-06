package io.payflow.transfer.api.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Introspected
@Serdeable
public record TransferResponse(
        String transferId,
        String sourceAccountId,
        String targetAccountId,
        String amount,
        String currency,
        String status,
        Instant createdAt
) {}
