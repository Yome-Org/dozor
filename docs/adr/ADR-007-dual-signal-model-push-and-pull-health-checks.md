# ADR-007: Dual Signal Model (Push + Pull Health Checks)

## Status
Accepted

## Date
2026-02-22

## Context
Push ingestion (`POST /signal`) is flexible, but onboarding existing services can be slow if they do not emit signals yet.

## Decision
Keep push ingestion as the core model and add pull-based health checks as an additional signal source.

Health checks:

- run on configured intervals
- use per-check timeout
- generate signals (`INFO`, `WARNING`, `CRITICAL`)
- never mutate state directly
- submit into the same bounded ingestion queue

## Alternatives Considered
- Push-only model
- Pull-only model
- Direct state mutation from check results

## Trade-Offs
- Pros: faster adoption, supports legacy services, preserves core deterministic engine.
- Cons: more runtime moving parts and additional check scheduling behavior.

## Consequences
- Dozor supports both integration paths without changing state semantics.
- Backpressure behavior is consistent across push and pull sources.
- Critical failures from health checks are gated by `failure_threshold` (consecutive failures), not immediate escalation.
