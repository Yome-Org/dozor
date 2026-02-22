# runtime-architecture.md (v0.1)

## 1. Architectural Overview

- modular monolith
- Kotlin + Ktor
- single-node deployment
- no clustering
- single-tenant

```text
Runtime profile: single process, single tenant, deterministic execution.
```

## 2. Package Structure (v0.1)

```text
dozor/
 └── src/main/kotlin/com/yome/dozor/
      ├── api/          # Ktor HTTP ingestion layer
      ├── config/       # YAML parsing and validation
      ├── domain/       # Pure domain models and value objects
      ├── state/        # Temporal evaluation logic
      ├── propagation/  # Dependency graph and impact propagation
      ├── incident/     # Incident lifecycle logic
      ├── alert/        # Alert decision logic
      ├── engine/       # Evaluation loop orchestration
      ├── persistence/  # Postgres repositories
      ├── cache/        # Redis temporal buckets
      ├── scheduler/    # Periodic evaluation ticks
      └── bootstrap/    # Application wiring
```

Layer responsibilities:

- `domain` contains business concepts and must stay framework-agnostic.
- `state`, `propagation`, `incident`, and `alert` implement deterministic core logic.
- `persistence`, `cache`, and `config` are adapters.
- `api`, `scheduler`, and `bootstrap` are runtime entrypoints and wiring.

## 3. Execution Model

- bounded internal queue
- debounced evaluation
- single-threaded evaluation loop
- dirty-set recomputation
- topological propagation
- deterministic evaluation order

Domain and core logic constraints:

- domain layer does not depend on Ktor or infrastructure
- engine does not embed storage implementations; it consumes interfaces
- persistence, cache, alerting, and config are adapter modules

## 4. Evaluation Pipeline

`Signal -> Persist -> Mark Dirty -> Debounce -> Evaluation Pass`

Evaluation pass steps:

1. Recompute dirty components
2. Propagate changes
3. Persist new states
4. Detect incidents
5. Emit alerts

```text
Signal
  -> Persist
  -> Mark Dirty
  -> Debounce
  -> Evaluation Pass
       1) Recompute dirty components
       2) Propagate changes
       3) Persist new states
       4) Detect incidents
       5) Emit alerts
```

## 5. Backpressure Strategy

- bounded queue
- `429`/`503` on overload
- internal metric for queue utilization
- optional self-alert on sustained overload

## 6. Failure Handling

- Postgres unavailable -> self-degraded
- Redis unavailable -> performance degradation
- queue overflow -> backpressure
- restart -> reconstruct state from Postgres

## 7. Determinism Guarantee

Given identical:

- signals
- configuration
- graph
- timestamps

The system must produce identical:

- states
- incidents
- alerts

This guarantee is testable.

## 8. Dependency Boundaries

Allowed dependencies:

```text
api -> engine

engine -> state
engine -> propagation
engine -> incident
engine -> alert
engine -> persistence
engine -> cache
engine -> scheduler

state -> domain
propagation -> domain
incident -> domain
alert -> domain
persistence -> domain
cache -> domain
config -> domain

domain -> (no dependencies)
```

Forbidden direction:

```text
No module imports `api`.
```
