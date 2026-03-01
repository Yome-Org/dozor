# alert-decision-model.md (v0.1)

## 1. Incident Lifecycle

```text
Open condition:
  root_component && state == Critical && critical_duration >= incidentThreshold

Close condition:
  previous_state == Critical &&
  current_state != Critical &&
  non_critical_duration >= recoveryWindow
```

### Incident Open

Incident opens when:

- component transitions into Critical
- AND
- remains Critical for >= incidentThreshold
- AND
- component is root cause

`incidentThreshold` default = 2 minutes.

### Incident Close

Incident closes when:

- component transitions from Critical to non-Critical
- AND
- remains non-Critical for >= recoveryWindow

## 2. Alert Rules

Alert is sent only on:

- Incident Open
- Incident Close

No alerts for:

- Degraded
- Impacted
- transient Critical (below threshold)

Runtime errors may still be ingested as signals, but they do not bypass incident
thresholds. See `docs/error-signal-model.md`.

## 3. Alert Grouping

Within a single incident:

- only one `Incident Open` alert
- only one `Incident Resolved` alert

Subsequent signals do not trigger new alerts.

```text
Per incident ID:
  allow once: OPEN alert
  allow once: RESOLVED alert
```

## 4. Alert Content (v0.1)

For `Incident Open`:

- root component name
- time detected
- severity
- impacted components list
- duration so far

For `Incident Resolved`:

- root component
- total incident duration

For `v0.1`, each external delivery attempt may be persisted with:

- channel
- delivery status
- transport-level error details when available

## 5. Deterministic Alerting Principle

Alert decision must depend only on:

- persisted state
- deterministic thresholds
- dependency graph

- No randomness.
- No external APIs.
- No heuristic suppression.

```text
alert_decision = f(persisted_state, thresholds, dependency_graph)
```

## 6. Wake-Up Alert Criteria

- only root Critical incidents
- only after `incidentThreshold`
- only once per incident lifecycle
