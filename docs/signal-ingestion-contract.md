# signal-ingestion-contract.md (v0.1)

## Endpoint

`POST /signal`

## Request

```json
{
  "component": "postgres",
  "severity": "CRITICAL",
  "source": "health-check",
  "occurredAt": "2025-01-01T12:00:00Z",
  "idempotencyKey": "optional-key"
}
```

Runtime error signals may use the same contract:

```json
{
  "component": "worker",
  "severity": "WARNING",
  "source": "runtime-error",
  "occurredAt": "2025-01-01T12:00:00Z",
  "idempotencyKey": "worker:sync-job:timeout:2025-01-01T12:00:00Z"
}
```

Notes:

- `idempotencyKey` is optional.
- `Idempotency-Key` HTTP header is also supported and has higher priority than body field.
- `severity` must be one of: `INFO`, `WARNING`, `CRITICAL`.
- `occurredAt` must be ISO-8601 instant.
- runtime errors use the same ingestion path as health-derived signals.
- the API intentionally accepts a minimal payload; detailed stack traces and rich debugging
  context belong in dedicated error analysis tooling.

## Responses

- `202 Accepted`: signal persisted and queued for evaluation.
- `200 OK`: duplicate signal detected (idempotent no-op).
- `400 Bad Request`: validation error or malformed JSON.
- `404 Not Found`: component is not registered.
- `429 Too Many Requests`: backpressure (ingestion queue full).

## Backpressure

When queue capacity is exhausted, ingestion returns `429` and does not persist the signal.

## Runtime Error Usage

Recommended initial mapping:

- isolated recoverable error -> `WARNING`
- repeated job failure -> `WARNING`
- hard startup failure or clear service outage symptom -> `CRITICAL`

Example:

```bash
curl -X POST http://localhost:8080/signal \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: worker-sync-timeout-2025-01-01T12:00:00Z" \
  -d '{
    "component": "worker",
    "severity": "WARNING",
    "source": "runtime-error",
    "occurredAt": "2025-01-01T12:00:00Z"
  }'
```
