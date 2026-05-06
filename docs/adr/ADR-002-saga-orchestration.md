# ADR-002 — Saga Orchestration over Choreography

**Status:** Accepted
**Date:** 2026-05

## Context

A fund transfer spans two accounts (debit source, credit target) which are independent aggregates managed by the same service. Partial failures must trigger compensating transactions (reverse the debit if credit fails). We need a coordination strategy.

## Decision

Use **orchestration**: `TransferSagaOrchestrator` is the single source of truth for saga state. It emits commands to the account service, listens for result events, and drives state transitions explicitly through a defined state machine:

```
INITIATED → DEBITING → CREDITING → COMPLETED  (happy path)
INITIATED → FAILED                             (debit failure)
DEBITING  → REVERSING → REVERSED              (credit failure)
```

## Consequences

**Positive:**
- Saga state machine is visible in one class — easy to audit and test all transition paths
- Adding new steps (e.g., fraud check) is a local change to the orchestrator, not a cross-service coordination problem
- Debugging: one place to look when a transfer is stuck in a non-terminal state
- Clear separation: orchestrator decides what to do; account service decides how to do it

**Negative:**
- Orchestrator is a coupling point — the transfer service must know what commands account service understands
- If orchestrator instance fails mid-saga, state is recoverable from `event_store` replay (the `Transfer` aggregate's event history encodes every state transition)

## Alternatives Considered

- **Choreography:** each service reacts to events autonomously and emits new events. More decoupled, but saga state is implicit — distributed across multiple services, hard to observe and test all compensation paths end-to-end.
