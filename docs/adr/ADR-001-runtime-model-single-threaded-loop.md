# ADR-001: Runtime Model (Single-Threaded Loop)

## Status
Accepted

## Date
2026-02-22

## Context
Dozor v0.1 targets deterministic state evaluation for dozens of components in a single-node deployment.

## Decision
Use a single-threaded evaluation loop for state recomputation, propagation, incident detection, and alert decisioning.

## Alternatives Considered
- Multi-threaded evaluation workers with synchronization
- Actor-model runtime
- Distributed workers per component group

## Trade-Offs
- Pros: deterministic order, simpler correctness model, easier debugging and replay.
- Cons: lower peak throughput than parallel execution.

## Consequences
- Deterministic execution is easier to test and reason about.
- Future scaling can introduce partitioned or parallel runtimes when needed.
