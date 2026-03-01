# error-signal-model.md (v0.1)

## Overview

Runtime errors and system health are related, but they are not the same thing.

- a runtime error is an observed event
- component state is a derived projection
- incidents remain state-based decisions

Dozor may ingest runtime errors as signals, but a runtime error does not automatically
imply a Critical incident.

## Error Signals

Examples of runtime error signals:

- unhandled exceptions
- failed jobs
- repeated request failures
- dependency timeouts
- circuit breaker open events

These signals may be submitted through the same ingestion path as other signals.

```text
runtime error -> signal ingestion -> state evaluation -> incident decision
```

## Severity Mapping

Runtime errors should be mapped to signal severity based on operational meaning.

Typical guidance:

- `INFO` for expected or harmless events
- `WARNING` for recoverable errors or isolated failures
- `CRITICAL` for errors that strongly indicate service unavailability or severe degradation

No global rule should treat all exceptions as `CRITICAL`.

## Incident Boundary

Incidents remain derived from component state, not from individual error events.

This means:

- a single exception does not automatically open an incident
- repeated warnings may still drive a component into `Degraded`
- repeated critical error signals may still drive a component into `Critical`

```text
event != incident
incident = f(signals over time, thresholds, dependency graph)
```

## Recommended Usage

Recommended behavior:

- send health-check failures as health signals
- send important runtime errors as event signals
- reserve `CRITICAL` for errors with clear operational impact
- let the state engine aggregate repeated failures over time

This keeps alerting meaningful and prevents incident inflation.

## Non-Goals for v0.1

Dozor `v0.1` does not define a separate event-alert subsystem for non-incident alerts.

`v0.1` focuses on:

- signal ingestion
- deterministic state evaluation
- incident lifecycle
- incident-based alerting

Future versions may add alert types derived directly from event patterns without opening
incidents.
