# ADR-005 — Spring Boot vs Micronaut: Post-Implementation Analysis

**Status:** Accepted
**Date:** 2026-05

## Context

PayFlow implements the same domain logic in two JVM stacks — Spring Boot 3 (WebFlux) and Micronaut 4 (Netty) — to measure real-world differences in developer experience, startup time, memory footprint, and request latency. The `shared/domain` module contains all business logic as pure Java; both stacks are purely infrastructure adapters.

## Results

> Run benchmarks from `benchmarks/k6/transfer-load.js` and fill in the numbers below. See `benchmarks/results/README.md` for instructions.

| Metric | Spring Boot 3 (WebFlux) | Micronaut 4 (Netty) |
|---|---|---|
| Startup time | <!-- ms --> | <!-- ms --> |
| RSS memory (idle) | <!-- MB --> | <!-- MB --> |
| RSS memory (under load) | <!-- MB --> | <!-- MB --> |
| p50 latency | <!-- ms --> | <!-- ms --> |
| p95 latency | <!-- ms --> | <!-- ms --> |
| p99 latency | <!-- ms --> | <!-- ms --> |
| Throughput | <!-- req/s --> | <!-- req/s --> |

Hardware: <!-- CPU, RAM, JVM, flags — from benchmarks/results/README.md -->

## Decision

Both stacks are production-viable for this workload. The choice depends on constraints:

- **Choose Spring Boot** when: the team already knows Spring, ecosystem breadth (Spring Security, Spring Cloud, Spring Data) matters, and build-time CI speed is less critical than runtime flexibility.
- **Choose Micronaut** when: startup time and memory footprint are critical (Lambda/serverless, many replicas, resource-constrained environments), GraalVM native image is a target, or compile-time DI error detection is valued.

## Key Observations

### Developer Experience
- Port from Spring to Micronaut took approximately **X hours** — hexagonal architecture in `shared/domain` made this straightforward. The domain logic required zero changes; only the infrastructure wiring changed.
- Main friction points during the port: <!-- fill in after porting, e.g. "Micronaut's default Prototype scope vs Spring's Singleton", "different @Transactional import", "missing @Singleton on services caused unexpected behavior" -->
- Micronaut's compile-time DI caught **X wiring errors** at build time that Spring would only surface at runtime — this was a notable advantage during development.

### Runtime Behavior
- Spring WebFlux and Micronaut Netty both use non-blocking I/O; the reactive plumbing in both stacks is similar. The main difference is in startup cost and memory baseline.
- GraalVM native image: <!-- attempted/skipped and why — Micronaut supports it natively; Spring requires extra AOT config -->

### Architecture Validation
- The hexagonal architecture decision (ADR-001) was validated: porting the full Transfer saga from Spring to Micronaut required no changes to `shared/domain`. The port time is a direct measure of how well the boundaries held.
- If porting had been slow, it would indicate domain coupling — the spec explicitly flags this as a smell to fix before continuing.

## Alternatives Considered

- **Quarkus:** similar compile-time DI model and GraalVM support to Micronaut, but a smaller ecosystem and less widespread adoption in the Brazilian Java community at time of writing.
- **Vert.x:** more low-level, requires more boilerplate for HTTP routing and Kafka integration — not the right comparison point for senior Spring developers evaluating a framework migration.
