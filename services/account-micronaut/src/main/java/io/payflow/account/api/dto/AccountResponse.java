package io.payflow.account.api.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable
public record AccountResponse(
        String accountId,
        String ownerId,
        String balance,
        String currency,
        String status
) {}
