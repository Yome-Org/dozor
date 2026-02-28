# signal-ingestion-contract.md (v0.1)

## Endpoint

`POST /signal`

## Request

```json
{
  "component": "database",
  "severity": "CRITICAL",
  "source": "health-check",
  "occurredAt": "2025-01-01T12:00:00Z",
  "idempotencyKey": "optional-key"
}
```

Notes:

- `idempotencyKey` is optional.
- `Idempotency-Key` HTTP header is also supported and has higher priority than body field.
- `severity` must be one of: `INFO`, `WARNING`, `CRITICAL`.
- `occurredAt` must be ISO-8601 instant.

## Responses

- `202 Accepted`: signal persisted and queued for evaluation.
- `200 OK`: duplicate signal detected (idempotent no-op).
- `400 Bad Request`: validation error or malformed JSON.
- `404 Not Found`: component is not registered.
- `429 Too Many Requests`: backpressure (ingestion queue full).

## Backpressure

When queue capacity is exhausted, ingestion returns `429` and does not persist the signal.
