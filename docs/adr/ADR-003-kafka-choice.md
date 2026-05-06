# ADR-003 — Apache Kafka as Event Bus

**Status:** Accepted
**Date:** 2026-05

## Context

PayFlow needs a message broker for saga commands and domain event propagation. Key requirements: per-account ordering guarantees, durability, and the ability for the projector to replay from offset 0 to rebuild read models.

## Decision

Use **Apache Kafka 3.7** with `accountId` as the partition key for all account-related topics. This guarantees that all events for a given account are processed in sequence by any consumer.

Topics:
| Topic | Partition key | Purpose |
|---|---|---|
| `payflow.account.events` | `accountId` | Domain events from account service |
| `payflow.transfer.events` | `transferId` | Domain events from transfer service |
| `payflow.transfer.commands` | `accountId` | Saga commands to account service |
| `payflow.dlq` | — | Dead letter queue |

## Consequences

**Positive:**
- Per-partition ordering: all events and commands for a given account are processed in sequence
- Log retention: projector can restart from offset 0 and rebuild projections without querying the event store
- High throughput suitable for financial systems with bursty transfer volumes
- Native consumer group semantics for the DLQ pattern

**Negative:**
- Operational complexity compared to managed cloud queues
- Cannot delete individual messages — suitable here since events and commands are immutable

## Alternatives Considered

- **RabbitMQ:** simpler to operate, but no native replay capability. Projector restarts would require a separate event store full-scan.
- **Redis Streams:** lightweight and easy to operate, but less mature ecosystem for financial-grade durability and exactly-once semantics.
