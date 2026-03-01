# health-check-model.md (v0.1)

## Overview

Dozor supports a dual signal model:

- push: services send `POST /signal`
- pull: Dozor executes configured health checks and converts results into signals

The state engine remains unchanged. Health checks are signal producers only.

For the relationship between service-local health endpoints and cross-service dependency
modeling, see `docs/service-health-model.md`.

## Configuration

### Examples

The examples below show the same `http` check type used for:

- JSON health endpoints
- HTML pages
- XML resources such as `sitemap.xml`

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
    expected_status: 200

  - component: homepage
    type: http
    url: https://example.com/
    interval: 30s
    timeout: 5s
    failure_threshold: 2
    expected_status: 200
    body_contains: "<title>Example</title>"

  - component: sitemap
    type: http
    url: https://example.com/sitemap.xml
    interval: 5m
    timeout: 10s
    failure_threshold: 2
    expected_status: 200
    content_type_contains: "xml"
    body_contains: "<urlset"
```

Supported HTTP expectations:

- `expected_status`
- `body_contains`
- `content_type_contains`

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
- JSON, HTML, and XML endpoints can use the same `http` check type

## Source of Truth

State transitions are always computed by the evaluation engine from persisted signals.
