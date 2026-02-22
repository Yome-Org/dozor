# ADR-005: Append-Only Signal Model

## Status
Accepted

## Date
2026-02-22

## Context
Signal history must remain auditable and replayable for deterministic evaluation and incident analysis.

## Decision
Store incoming signals as append-only records with optional idempotency keys for deduplication.

## Alternatives Considered
- Mutable latest-signal rows per component
- Destructive compaction of historical data
- Event replacement on duplicate ingestion

## Trade-Offs
- Pros: full audit trail, reproducibility, replay support.
- Cons: storage growth over time and retention planning requirements.

## Consequences
- Historical replay and debugging are straightforward.
- Retention and archival policy becomes an explicit operational concern.
