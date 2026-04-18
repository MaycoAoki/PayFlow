package io.payflow.transfer.api.dto;

import java.time.Instant;

public record TransferResponse(
        String transferId,
        String sourceAccountId,
        String targetAccountId,
        String amount,
        String currency,
        String status,
        Instant createdAt
) {}
