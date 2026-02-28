CREATE TABLE components (
  id UUID PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE dependencies (
  upstream_component_id UUID NOT NULL REFERENCES components(id),
  downstream_component_id UUID NOT NULL REFERENCES components(id),
  PRIMARY KEY (upstream_component_id, downstream_component_id)
);

CREATE INDEX idx_dependencies_downstream ON dependencies (downstream_component_id);
CREATE INDEX idx_dependencies_upstream ON dependencies (upstream_component_id);

CREATE TABLE signals (
  id UUID PRIMARY KEY,
  component_id UUID NOT NULL REFERENCES components(id),
  severity SMALLINT NOT NULL,
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

CREATE TABLE component_state (
  component_id UUID PRIMARY KEY REFERENCES components(id),
  state SMALLINT NOT NULL,
  last_evaluated_at TIMESTAMP NOT NULL,
  last_state_change_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL
);

CREATE TABLE incidents (
  id UUID PRIMARY KEY,
  root_component_id UUID NOT NULL REFERENCES components(id),
  started_at TIMESTAMP NOT NULL,
  resolved_at TIMESTAMP NULL,
  status SMALLINT NOT NULL
);

CREATE INDEX idx_incidents_root_status
  ON incidents (root_component_id, status);

CREATE TABLE alerts (
  id UUID PRIMARY KEY,
  incident_id UUID NOT NULL REFERENCES incidents(id),
  type SMALLINT NOT NULL,
  sent_at TIMESTAMP NOT NULL,
  channel TEXT NOT NULL
);
