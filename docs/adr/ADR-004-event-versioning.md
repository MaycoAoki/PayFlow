# ADR-004 — Event Versioning and Upcasting

**Status:** Accepted
**Date:** 2026-05

## Context

Domain events stored in `event_store` are immutable — they cannot be modified after writing. But schemas evolve: fields are added, renamed, or removed. We need a strategy that allows reading old events with new code without migrating stored data.

## Decision

Each event payload includes `"_eventVersion": <int>` in the JSONB column. Upcasting is applied at read time via `EventUpcasterChain` in `shared/infrastructure`.

When a field is added, renamed, or removed from an event:
1. Increment `_eventVersion` for that event type
2. Implement an `EventUpcaster` that transforms the old JSON shape to the new shape
3. Register it in `EventTypeRegistry`

The chain applies upcasters in sequence, so a v1 event can be promoted to v3 by passing through the v1→v2 and v2→v3 upcasters.

## Consequences

**Positive:**
- Old events remain readable after schema evolution — zero data migration needed
- Upcasting is applied transparently on `loadEvents()` — consumers always see the current shape
- Versioning is explicit and reviewable in the `EventTypeRegistry`

**Negative:**
- Upcasters must be maintained across the lifetime of the system
- The upcaster chain can grow long for frequently-changed event types — mitigated by collapsing old upcasters into a single v1→vN upcaster after major releases
