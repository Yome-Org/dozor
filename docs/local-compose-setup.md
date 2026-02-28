# local-compose-setup.md

## Goal

Run a full local vertical slice:

- `dozor`
- `postgres`
- `redis`
- `mock-health`

This setup validates:

- pull-based health checks
- evaluation loop
- state persistence
- incident creation
- alert delivery recording

The local Docker setup intentionally uses `docker/dozor/dozor.demo.yaml`, which keeps
the dependency graph and thresholds unchanged but shortens `incident_threshold` and
`recovery_window` for faster open/resolve demos.

## Start

Prepare environment:

```bash
cp .env.example .env
```

Edit `.env` before startup.

`.env` controls:

- Dozor runtime settings
- Postgres credentials and exposed port
- Redis enablement and exposed port
- mock health service exposed port
- Telegram delivery

```bash
docker compose --env-file .env -f docker/compose.yaml up --build
```

Equivalent `Makefile` flow:

```bash
make env-init
make compose-up
```

## Scenario

### 1. Initial healthy state

`mock-health` starts in healthy mode.

Health checks emit `INFO` for `postgres`.

### 2. Trigger failure

```bash
curl "http://localhost:18080/toggle?healthy=false"
```

Or:

```bash
make fail
```

Wait at least 3 failed checks.

Current demo config uses:

- `window: 25s`
- `interval: 5s`
- `failure_threshold: 3`
- `incident_threshold: 5s`
- `recovery_window: 15s`

With the current compose config, `postgres` typically reaches `CRITICAL` after roughly 25-30 seconds, not 15 seconds, because both:

- health-check failure escalation
- state-machine critical signal thresholds

must accumulate.

Expected propagation:

- `postgres -> CRITICAL`
- `api -> IMPACTED`
- `worker -> IMPACTED`

### 3. Recover

```bash
curl "http://localhost:18080/toggle?healthy=true"
```

Or:

```bash
make ok
```

Recovery signals will return to `INFO`.

## Inspect Postgres

```bash
docker exec -it $(docker ps -qf name=postgres) psql -U dozor -d dozor
```

Or:

```bash
make psql
```

Useful queries:

```sql
SELECT component_id, state, last_evaluated_at, version
FROM component_state;

SELECT id, root_component_id, started_at, resolved_at, status
FROM incidents
ORDER BY started_at DESC;

SELECT incident_id, type, sent_at, channel, delivery_status, error_message
FROM alerts
ORDER BY sent_at DESC;
```

Quick non-interactive shortcuts:

```bash
make state-list
make incident-open
make incident-history
make alert-list
make alerts-open
```

Demo smoke flows:

```bash
make demo-critical
make demo-recover
make demo-cycle
```

The default `.env.example` uses:

```env
DEMO_CRITICAL_WAIT=30
DEMO_RECOVERY_WAIT=30
```

## Push API Check

Dozor also exposes push ingestion:

```bash
curl -X POST http://localhost:8080/signal \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{"component":"postgres","severity":"CRITICAL","source":"manual","occurredAt":"2026-02-22T12:00:00Z"}'
```

Or:

```bash
make signal
```

## Telegram

Telegram settings can be provided through `.env`:

```env
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=<token>
TELEGRAM_CHAT_ID=<chat_id>
```

## Common Local Overrides

Examples:

```env
API_PORT=8080
POSTGRES_PORT=5432
REDIS_PORT=6379
MOCK_HEALTH_PORT=18080
POSTGRES_JDBC_URL=jdbc:postgresql://postgres:5432/dozor
REDIS_URI=redis://redis:6379
```
