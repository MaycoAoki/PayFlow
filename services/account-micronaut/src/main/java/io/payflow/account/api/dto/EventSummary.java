package io.payflow.account.api.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Introspected
@Serdeable
public record EventSummary(String eventType, Instant occurredAt) {}
