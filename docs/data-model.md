# data-model.md (v0.1)

## 1. `components`

```sql
CREATE TABLE components (
  id UUID PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT,
  created_at TIMESTAMP NOT NULL
);
```

## 2. `dependencies`

```sql
CREATE TABLE dependencies (
  upstream_component_id UUID NOT NULL REFERENCES components(id),
  downstream_component_id UUID NOT NULL REFERENCES components(id),
  PRIMARY KEY (upstream_component_id, downstream_component_id)
);

CREATE INDEX idx_dependencies_downstream
  ON dependencies (downstream_component_id);

CREATE INDEX idx_dependencies_upstream
  ON dependencies (upstream_component_id);
```

DAG validation is performed at the service layer.

## 3. `signals` (append-only)

```sql
CREATE TABLE signals (
  id UUID PRIMARY KEY,
  component_id UUID NOT NULL REFERENCES components(id),
  severity SMALLINT NOT NULL, -- 0=info, 1=warning, 2=critical
  source TEXT NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  ingested_at TIMESTAMP NOT NULL,
  idempotency_key TEXT NULL
);

CREATE INDEX idx_signals_component_occurred_at_desc
  ON signals (component_id, occurred_at DESC);

CREATE UNIQUE INDEX idx_signals_idempotency_key_unique
  ON signals (idempotency_key)
  WHERE idempotency_key IS NOT NULL;
```

This enables efficient window reads and duplicate protection.

## 4. `component_state` (snapshot)

```sql
CREATE TABLE component_state (
  component_id UUID PRIMARY KEY REFERENCES components(id),
  state SMALLINT NOT NULL,
  last_evaluated_at TIMESTAMP NOT NULL,
  last_state_change_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL
);
```

`version` is reserved for optimistic locking, including future concurrent modes.

## 5. `incidents`

```sql
CREATE TABLE incidents (
  id UUID PRIMARY KEY,
  root_component_id UUID NOT NULL REFERENCES components(id),
  started_at TIMESTAMP NOT NULL,
  resolved_at TIMESTAMP NULL,
  status SMALLINT NOT NULL -- 0=open, 1=resolved
);

CREATE INDEX idx_incidents_root_status
  ON incidents (root_component_id, status);
```

## 6. `alerts`

```sql
CREATE TABLE alerts (
  id UUID PRIMARY KEY,
  incident_id UUID NOT NULL REFERENCES incidents(id),
  type SMALLINT NOT NULL, -- 0=open, 1=resolved
  sent_at TIMESTAMP NOT NULL,
  channel TEXT NOT NULL,
  delivery_status SMALLINT NOT NULL, -- 0=sent, 1=failed
  error_message TEXT NULL
);
```

For `v0.1`, `alerts` stores alert delivery records.
`channel` identifies the delivery target or sink, for example `internal` or `telegram`.
`delivery_status` captures whether the record was persisted or the external delivery attempt succeeded.
`error_message` stores transport-level failure details when available.

## Temporal Window Reconstruction

```sql
SELECT *
FROM signals
WHERE component_id = ?
  AND occurred_at >= now() - interval '5 minutes'
ORDER BY occurred_at DESC;
```

For v1 scale (dozens of components on a single node), this model is expected to provide low-latency evaluation windows.
