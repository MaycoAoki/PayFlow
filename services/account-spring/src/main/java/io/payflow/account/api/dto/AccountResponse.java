package io.payflow.account.api.dto;

public record AccountResponse(
        String accountId,
        String ownerId,
        String balance,
        String currency,
        String status
) {}
