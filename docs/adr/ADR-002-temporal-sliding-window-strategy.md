# ADR-002: Temporal Sliding Window Strategy

## Status
Accepted

## Date
2026-02-22

## Context
State must be derived from recent signal history and remain stable under noise.

## Decision
Use sliding time windows with explicit thresholds and recovery windows to derive component state.

## Alternatives Considered
- Fixed tumbling windows
- Exponential moving averages only
- Event-driven immediate transitions without windows

## Trade-Offs
- Pros: predictable semantics, controlled hysteresis, straightforward replay.
- Cons: requires time-window queries and threshold tuning.

## Consequences
- State transitions are stable and less prone to flapping.
- Evaluation remains deterministic for identical inputs and timestamps.
