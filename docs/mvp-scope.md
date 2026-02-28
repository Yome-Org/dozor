# mvp-scope.md (v0.1)

## Scope Boundary

This document defines the hard scope boundary for MVP `v0.1`.

## Included in v0.1

- YAML configuration
- component registration from file
- DAG validation
- signal ingestion API
- sliding window evaluation
- dirty-set recomputation
- dependency propagation
- root cause detection
- incident lifecycle
- Telegram notifier
- self-health alerts
- bounded queue + backpressure
- Redis temporal buckets
- Postgres persistence
- restart-safe reconstruction

## Excluded from v0.1

- REST configuration API
- UI
- LLM integration
- auto-remediation
- multi-tenancy
- clustering
- horizontal scaling
- plugin framework
- Prometheus metrics exporter
- complex scoring model
- dynamic threshold tuning

## Configuration File Shape

```yaml
context:
  project: demo
  environment: dev
  # stack: core  # optional

evaluation:
  window: 5m
  recovery_window: 2m
  incident_threshold: 2m
  debounce: 250ms

components:
  - name: postgres
  - name: api
  - name: worker

dependencies:
  - upstream: postgres
    downstream: api
  - upstream: api
    downstream: worker

thresholds:
  postgres:
    critical: 3
    degraded: 3
```

The configuration should remain simple, readable, and predictable.

## Configuration Runtime Contract

Configuration is:

- fully loaded at startup
- immutable during runtime
- stored in memory
- consumed by the evaluation engine without runtime I/O

```text
v0.1 policy: no runtime config reload.
```
