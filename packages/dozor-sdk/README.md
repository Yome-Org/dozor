# @yome-network/dozor-sdk

Typed SDK for Dozor signal ingestion API (`POST /signal`) with runtime validation and pluggable transports.

## Install

```bash
npm install @yome-network/dozor-sdk
```

## Usage

```ts
import { createDozorClient, HttpDozorTransport } from '@yome-network/dozor-sdk'

const client = createDozorClient({
  transport: new HttpDozorTransport({ baseUrl: 'http://your-dozor-host:3008' }),
})

const result = await client.sendSignal({
  component: 'catalog',
  severity: 'WARNING',
  source: 'runtime-error:read-model',
  occurredAt: new Date().toISOString(),
  idempotencyKey: 'catalog:runtime:warning:12345',
})

if (!result.ok) {
  console.warn('signal rejected', result.httpStatus, result.body)
}
```

## API

- `DozorClient#sendSignal(payload)`
  - validates request payload
  - sends signal through configured transport
  - validates response payload
  - returns typed result for statuses `200`, `202`, `400`, `404`, `429`
  - throws `DozorProtocolError` for unexpected status/schema
  - throws `DozorTransportError` for transport-level failures
  - throws `DozorValidationError` for invalid request payload

## Transport extensibility

The client accepts a generic `DozorTransport` implementation.

Current implementation:
- `HttpDozorTransport`

Planned implementations:
- MQ transport
- WebSocket transport

## Integration Example

1. Create a singleton SDK client:

```ts
const dozorClient = createDozorClient({
  transport: new HttpDozorTransport({ baseUrl: config.dozorApiUrl, timeoutMs: 1500 }),
})
```

2. Send a signal:

```ts
const result = await dozorClient.sendSignal({
  component: 'your-component-name',
  severity,
  source,
  occurredAt,
  idempotencyKey,
})
```

3. Keep domain-specific behavior in your service layer:
- feature flags (`DOZOR_ENABLED`)
- custom metrics
- cooldown/rate window logic
- local logging around accepted/rejected statuses
