# ADR-003: Dependency DAG and Propagation

## Status
Accepted

## Date
2026-02-22

## Context
Dozor must represent upstream/downstream dependencies and propagate impact without ambiguous cycles.

## Decision
Model dependencies as a directed acyclic graph (DAG) and run propagation in topological order.

## Alternatives Considered
- Arbitrary directed graph with cycle handling at runtime
- Manual static impact lists
- Service-mesh-only external dependency mapping

## Trade-Offs
- Pros: deterministic propagation, explainable root cause, simpler runtime.
- Cons: graph registration must reject cycles.

## Consequences
- Root cause selection is clear and auditable.
- Impacted state handling remains predictable and bounded.
