# ADR-001 — Event Sourcing for Financial Domain

**Status:** Accepted
**Date:** 2026-05

## Context

PayFlow handles financial transfers where audit trail, regulatory compliance, and the ability to reconstruct state at any point in time are first-class requirements. Traditional CRUD approaches lose history — once a balance is updated, the previous state is gone.

## Decision

Use Event Sourcing as the primary persistence strategy. State is never stored directly — only immutable domain events are appended to `event_store`. Current state is derived by replaying events.

## Consequences

**Positive:**
- Complete audit log for free — every state change is recorded with timestamp and causality
- Temporal queries: reconstruct account balance at any past moment by replaying events up to that point
- Enables event-driven projections (CQRS) without additional synchronization mechanisms
- Debugging: replay events in a test environment to reproduce any production incident exactly
- Natural fit for Kafka-driven saga coordination — events are the source of truth for both write and async read paths

**Negative:**
- Read performance requires a projection layer (mitigated by CQRS read models in `account_projections`)
- Schema evolution requires explicit upcasting (mitigated by ADR-004 versioning strategy)
- Higher conceptual complexity for developers unfamiliar with the pattern

## Alternatives Considered

- **Snapshot + current state table:** simpler to implement, but loses audit trail and makes temporal queries impossible without additional tooling
- **Change Data Capture (CDC):** captures changes at DB level, but couples audit to infrastructure rather than domain and is harder to test in isolation
