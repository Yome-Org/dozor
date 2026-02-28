# health-check-model.md (v0.1)

## Overview

Dozor supports a dual signal model:

- push: services send `POST /signal`
- pull: Dozor executes configured health checks and converts results into signals

The state engine remains unchanged. Health checks are signal producers only.

## Configuration

```yaml
context:
  project: demo
  environment: dev
  # stack: core  # optional

checks:
  - component: postgres
    type: http
    url: http://db:8080/health
    interval: 30s
    timeout: 3s
    failure_threshold: 3
```

## Signal Mapping

For each check execution:

- healthy result -> `INFO`
- failed result with consecutive failures `< failure_threshold` -> `WARNING`
- failed result with consecutive failures `>= failure_threshold` -> `CRITICAL`

## Runtime Constraints

- checks run outside the evaluation loop
- each check call has a timeout
- produced signals go through the same bounded queue and backpressure rules
- no direct state mutation from health checks

## Source of Truth

State transitions are always computed by the evaluation engine from persisted signals.
