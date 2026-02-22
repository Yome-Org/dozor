# ADR-004: Hybrid Redis + Postgres Storage

## Status
Accepted

## Date
2026-02-22

## Context
Dozor requires durable persistence and low-latency temporal reads for evaluation windows.

## Decision
Use Postgres as the system of record and Redis as a performance/cache layer for temporal buckets.

## Alternatives Considered
- Postgres-only storage
- Redis-only storage
- Time-series database as primary storage

## Trade-Offs
- Pros: durability from Postgres, speed from Redis.
- Cons: dual storage complexity and recovery choreography.

## Consequences
- Persistence outages trigger degraded internal mode.
- Redis loss degrades performance but preserves correctness.
