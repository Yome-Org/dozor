# failure-model.md (v0.1)

## 1. Dozor Internal Health State

`DozorHealthState`

States:

- Healthy
- Degraded
- Critical

`DozorHealthState` is independent from monitored component states and represents Dozor service health.

```text
DozorHealthState: Healthy | Degraded | Critical
```

## 2. Persistence Failure (Postgres Unavailable)

### Detection

- failed DB connection
- transaction timeout
- health probe failure

### Behavior

`DozorHealthState -> Degraded`

Suspend:

- state evaluation
- incident creation
- alert decision logic

Continue:

- accepting signals
- buffering in Redis (bounded)

Emit one self-alert:

`DOZOR DEGRADED: Persistence unavailable. Alerting suspended.`

Only one self-alert is emitted per degradation episode.

```text
Mode switch (Postgres unavailable):
  DozorHealthState -> Degraded
  suspend: evaluation, incident creation, alert decisions
  continue: ingestion + bounded Redis buffering
```

## 3. Redis Failure

Redis is a performance layer.

If Redis is unavailable:

- continue ingestion
- continue evaluation
- use Postgres-only window computation
- log warning

Do not self-alert unless degradation persists.

Redis failure maps to internal `Degraded`, not `Critical`.

```text
Mode switch (Redis unavailable):
  keep ingestion and evaluation online
  compute windows from Postgres
  treat as performance degradation
```

## 4. Full Recovery

When Postgres is restored:

- flush Redis buffer
- force re-evaluation of all components
- resolve `DozorHealthState -> Healthy`

Emit one self-recovery alert:

`DOZOR RECOVERED: Persistence restored.`

```text
Recovery sequence:
  flush Redis buffer -> re-evaluate all components -> DozorHealthState = Healthy
```
