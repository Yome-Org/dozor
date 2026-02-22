# Dozor

Dozor is a deterministic, state-centric system health evaluation engine.

It is designed to:

- aggregate signals over time
- derive component state using temporal windows
- propagate failures through dependency graphs
- detect root cause incidents
- emit actionable alerts
- remain predictable under failure

Dozor is not a traditional monitoring tool.
It is a state evaluation engine.

## Core Principles

- Signals are immutable facts (append-only)
- State is a derived projection
- Alerts are decisions based on state transitions
- Dependencies form a directed acyclic graph (DAG)
- Evaluation is deterministic
- No alert spam
- No hidden heuristics
- No AI in the critical decision path

## Architectural Overview (v0.1)

- Kotlin + Ktor
- Modular monolith
- Single-threaded evaluation loop
- Dirty-set recomputation
- Sliding window temporal model
- Deterministic dependency propagation
- Postgres as source of truth
- Redis as performance layer
- File-based configuration (YAML)

## Project Structure (v0.1)

```text
dozor/
 ├── build.gradle.kts
 ├── settings.gradle.kts
 ├── docs/
 │    └── adr/
 ├── docker/
 ├── src/
 │    ├── main/kotlin/com/yome/dozor/
 │    │    ├── api
 │    │    ├── config
 │    │    ├── domain
 │    │    ├── state
 │    │    ├── propagation
 │    │    ├── incident
 │    │    ├── alert
 │    │    ├── engine
 │    │    ├── persistence
 │    │    ├── cache
 │    │    ├── scheduler
 │    │    └── bootstrap
 │    └── test/kotlin/com/yome/dozor/
 └── .github/
```

See `docs/runtime-architecture.md` for detailed module breakdown.

## Architecture Decisions

See `docs/adr/` for architectural decision records.

## Evaluation Model

For each component:

- signals are collected within a sliding time window
- state is recomputed deterministically
- hysteresis prevents flapping
- upstream failures propagate impact
- incidents open only after threshold duration

State priority:

`Critical > Degraded > Impacted > Healthy > Unknown`

## Incident Philosophy

An incident is opened only if:

- a root component enters Critical
- it remains Critical for a configured threshold
- no upstream component is Critical

Only root causes trigger alerts.

Downstream impact does not generate alert noise.

## Failure Behaviour

Dozor monitors itself.

If persistence becomes unavailable:

- alerting is suspended
- a self-degradation alert is emitted
- signals are temporarily buffered
- state is reconstructed after recovery

No silent failure.

## Configuration

v0.1 uses static YAML configuration:

- components
- dependencies
- thresholds
- temporal windows

Runtime mutation is intentionally not supported in v1.

## Non-Goals (v0.1)

- No multi-tenancy
- No clustering
- No REST-based configuration
- No UI
- No AI-based alert decisions
- No distributed coordination

The focus of v0.1 is deterministic correctness.

## Status

This project is currently in early architectural phase.

The goal of v0.1 is to establish:

- a formally defined state model
- deterministic propagation rules
- a clean evaluation engine
- a reproducible alert lifecycle

## License

Apache 2.0
