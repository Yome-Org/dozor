# service-health-model.md (v0.1)

## Overview

Service health endpoints and Dozor dependency propagation solve different problems.

- service health describes whether a single service is currently operational
- Dozor dependency modeling explains cross-service causality across the stack

Both are useful. Neither fully replaces the other.

## Service-Level Health

Services may expose health endpoints such as:

- liveness
- readiness
- optional detailed diagnostics

Service health endpoints may check local dependencies required for operation, for example:

- database connectivity
- cache availability
- upstream API reachability
- queue broker availability

This is useful for:

- local readiness decisions
- container orchestration
- fast operational diagnostics

## System-Level Causality

Dozor remains the source of truth for cross-service causality.

Dozor is responsible for:

- dependency graph evaluation
- root cause detection
- impact propagation
- incident deduplication
- alert noise reduction

Without explicit dependency modeling, the same upstream failure may produce multiple independent service failures and multiple noisy incidents.

## Recommended Model

Recommended behavior:

- services expose useful dependency-aware health endpoints
- Dozor ingests those results as signals
- Dozor uses the dependency graph to derive root cause and downstream impact

```text
service health -> signal
signal -> Dozor state evaluation
state evaluation + dependency graph -> root cause + impacted components
```

## Example

Given:

- `mailer -> api`
- `mailer -> worker`
- `mailer -> admin`

If `mailer` fails:

- service health endpoints for `api`, `worker`, and `admin` may all report degraded readiness
- Dozor should still identify `mailer` as the root cause
- downstream services should resolve as impacted rather than independent root incidents

## Design Guidance

Service health endpoints should:

- be deterministic
- be cheap enough to run regularly
- reflect real service usability
- expose dependency failures clearly

Service health endpoints should not:

- attempt cross-service incident correlation
- replace explicit dependency modeling
- become the sole mechanism for system-wide root cause analysis

## Source of Truth Boundary

Service-level truth:

- whether one service is alive or ready
- which local dependency checks currently fail

Dozor truth:

- which component is the root cause
- which components are impacted
- whether an incident should open
- whether an alert should be emitted
