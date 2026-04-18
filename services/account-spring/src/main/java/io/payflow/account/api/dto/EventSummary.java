package io.payflow.account.api.dto;

import java.time.Instant;

public record EventSummary(
        String eventType,
        Instant occurredAt
) {}
