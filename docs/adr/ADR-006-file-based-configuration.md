# ADR-006: File-Based Configuration

## Status
Accepted

## Date
2026-02-22

## Context
Dozor v0.1 prioritizes deterministic startup and predictable runtime behavior over dynamic configuration changes.

## Decision
Use static YAML configuration loaded at startup, kept in memory, and treated as immutable during runtime.

## Alternatives Considered
- Runtime REST configuration API
- Database-backed mutable configuration
- Hot-reload file watcher

## Trade-Offs
- Pros: simpler runtime model, no reload race conditions, deterministic behavior.
- Cons: configuration changes require restart/redeploy.

## Consequences
- Operational workflow uses versioned config files and controlled rollouts.
- Runtime I/O is removed from the evaluation decision path.
