# Dozor

[![CI](https://github.com/Yome-Org/dozor/actions/workflows/ci.yml/badge.svg)](https://github.com/Yome-Org/dozor/actions/workflows/ci.yml)

Dozor is a deterministic, state-centric system health evaluation engine.

It is designed to:

- aggregate signals over time
- derive component state using temporal windows
- propagate failures through dependency graphs
- detect root cause incidents
- emit actionable alerts
- remain predictable under failure

Dozor is not a traditional monitoring tool.
It is a state evaluation engine.

## Core Principles

- Signals are immutable facts (append-only)
- State is a derived projection
- Alerts are decisions based on state transitions
- Dependencies form a directed acyclic graph (DAG)
- Evaluation is deterministic
- No alert spam
- No hidden heuristics
- No AI in the critical decision path

## Architectural Overview (v0.1)

- Kotlin + Ktor
- Modular monolith
- Single-threaded evaluation loop
- Dirty-set recomputation
- Sliding window temporal model
- Deterministic dependency propagation
- Postgres as source of truth
- Redis as performance layer
- File-based configuration (YAML)

## Project Structure (v0.1)

```text
dozor/
 ├── build.gradle.kts
 ├── settings.gradle.kts
 ├── docs/
 │    └── adr/
 ├── docker/
 ├── src/
 │    ├── main/kotlin/com/yome/dozor/
 │    │    ├── api
 │    │    ├── config
 │    │    ├── domain
 │    │    ├── state
 │    │    ├── propagation
 │    │    ├── incident
 │    │    ├── alert
 │    │    ├── engine
 │    │    ├── persistence
 │    │    ├── cache
 │    │    ├── scheduler
 │    │    └── bootstrap
 │    └── test/kotlin/com/yome/dozor/
 └── .github/
```

See `docs/runtime-architecture.md` for detailed module breakdown.

## Architecture Decisions

See `docs/adr/` for architectural decision records.

## Evaluation Model

For each component:

- signals are collected within a sliding time window
- state is recomputed deterministically
- hysteresis prevents flapping
- upstream failures propagate impact
- incidents open only after threshold duration

State priority:

`Critical > Degraded > Impacted > Healthy > Unknown`

## Incident Philosophy

An incident is opened only if:

- a root component enters Critical
- it remains Critical for a configured threshold
- no upstream component is Critical

Only root causes trigger alerts.

Downstream impact does not generate alert noise.

## Failure Behaviour

Dozor monitors itself.

If persistence becomes unavailable:

- alerting is suspended
- a self-degradation alert is emitted
- signals are temporarily buffered
- state is reconstructed after recovery

No silent failure.

## Configuration

v0.1 uses static YAML configuration for system behavior:

- installation context
- components
- dependencies
- thresholds
- temporal windows
- health checks

Runtime mutation is intentionally not supported in v1.

Deployment-specific settings are loaded from process environment variables.

For local development, `make` and `docker compose --env-file .env ...` populate those
environment variables from `.env`.

The Kotlin process does not read `.env` directly.

Required environment variables:

- `API_HOST`
- `API_PORT`
- `POSTGRES_JDBC_URL`
- `POSTGRES_USERNAME`
- `POSTGRES_PASSWORD`
- `REDIS_ENABLED`
- `REDIS_URI`
- `TELEGRAM_ENABLED`

Additional variables used by the local compose setup:

- `POSTGRES_DB`
- `POSTGRES_PORT`
- `REDIS_PORT`
- `MOCK_HEALTH_PORT`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`

## Signal Ingestion API

Dozor exposes a minimal ingestion contract:

`POST /signal`

```json
{
  "component": "postgres",
  "severity": "CRITICAL",
  "source": "health-check",
  "occurredAt": "2025-01-01T12:00:00Z"
}
```

Response behavior:

- `202` accepted and queued for evaluation
- `200` duplicate signal (idempotent no-op)
- `400` validation error
- `404` unknown component
- `429` backpressure (queue full)

See `docs/signal-ingestion-contract.md`.

Dozor also supports pull-based health checks as an additional signal source.
See `docs/health-check-model.md`.

## Local Compose Setup

For a full local vertical slice with `dozor`, `postgres`, `redis`, and a mock health service:

```bash
cp .env.example .env
docker compose --env-file .env -f docker/compose.yaml up --build
```

Or via `Makefile`:

```bash
make env-init
make compose-up
```

`.env` is the single source of local environment configuration for:

- Dozor runtime
- Postgres container
- Redis container
- exposed local ports
- Telegram delivery

The local compose stack uses a demo runtime config with shortened `incident_threshold`
and `recovery_window` so `make demo-cycle` can show both open and resolved transitions
without waiting multiple minutes.

See `docs/local-compose-setup.md`.

## Code Style

Formatting is enforced with `spotless` and `ktfmt`.

Useful commands:

```bash
make fmt
make fmt-check
```

## Telegram Setup

To receive alerts in Telegram:

1. Open Telegram and start a chat with `@BotFather`.
2. Run `/newbot`.
3. Choose a bot name and a unique bot username.
4. Copy the bot token returned by BotFather.
5. Start a chat with your new bot and send any message.
6. Open:
   `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
7. Find the `chat` object in the response and copy `id`.

Configure `.env`:

```env
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=<your_bot_token>
TELEGRAM_CHAT_ID=<your_chat_id>
```

Then restart Dozor.

Note:
- Telegram messages use component names and formatted UTC timestamps.
- `alerts.channel = internal` is the persisted internal alert record
- `alerts.channel = telegram` means the delivery attempt targeted Telegram
- `alerts.delivery_status` records whether that attempt succeeded or failed
- `alerts.error_message` stores transport-level error details when delivery fails

## Non-Goals (v0.1)

- No multi-tenancy
- No clustering
- No REST-based configuration
- No UI
- No AI-based alert decisions
- No distributed coordination

The focus of v0.1 is deterministic correctness.

## Status

This project is currently in early architectural phase.

The goal of v0.1 is to establish:

- a formally defined state model
- deterministic propagation rules
- a clean evaluation engine
- a reproducible alert lifecycle

## License

Apache 2.0
