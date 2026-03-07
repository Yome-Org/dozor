# @yome-network/dozor-sdk

[![npm version](https://img.shields.io/npm/v/@yome-network/dozor-sdk.svg)](https://www.npmjs.com/package/@yome-network/dozor-sdk)

Typed SDK for Dozor signal ingestion API (`POST /signal`) with runtime validation, normalized error model, and pluggable transports.

## Install

```bash
npm install @yome-network/dozor-sdk
```

## Quick Start

```ts
import { createDozorClient, HttpDozorTransport } from "@yome-network/dozor-sdk";

const client = createDozorClient({
  transport: new HttpDozorTransport({ baseUrl: "http://your-dozor-host:3008" }),
});

const result = await client.sendSignal({
  component: "catalog",
  severity: "WARNING",
  source: "runtime-error:read-model",
  occurredAt: new Date().toISOString(),
  idempotencyKey: "catalog:runtime:warning:12345",
});

if (!result.ok) {
  console.warn("signal rejected", result.httpStatus, result.body);
}
```

## What `sendSignal` Guarantees

`DozorClient#sendSignal(payload, options?)`:
- validates request payload before sending
- sends through configured transport
- validates response schema after receiving
- returns typed result for `200`, `202`, `400`, `404`, `429`
- throws normalized typed errors:
  - `DozorValidationError`
  - `DozorTransportError`
  - `DozorProtocolError`

## Retry Policy

Global retry policy:

```ts
const client = createDozorClient({
  transport: new HttpDozorTransport({ baseUrl: "http://localhost:3008" }),
  retryPolicy: {
    maxAttempts: 3,
    baseDelayMs: 100,
    maxDelayMs: 1_000,
    backoffMultiplier: 2,
  },
});
```

Per-call override:

```ts
await client.sendSignal(payload, {
  retryPolicy: { maxAttempts: 5 },
});
```

## Observability Hooks and Logger

```ts
const client = createDozorClient({
  transport: new HttpDozorTransport({ baseUrl: "http://localhost:3008" }),
  hooks: {
    onRequest: ({ redactedRequest, attempt }) => {
      console.info("dozor request", { redactedRequest, attempt });
    },
    onResponse: ({ responseStatus, result }) => {
      console.info("dozor response", { responseStatus, ok: result.ok });
    },
    onError: ({ error, willRetry }) => {
      console.error("dozor error", { error, willRetry });
    },
  },
  logger: {
    warn: (message, context) => console.warn(message, context),
    debug: (message, context) => console.debug(message, context),
  },
  redaction: {
    fields: ["idempotencyKey"],
    replacement: "[REDACTED]",
  },
});
```

## Transport Extensibility

The client works with any transport implementing `DozorTransport`.

Included now:
- `HttpDozorTransport`

Planned extensions:
- `MqTransport`
- `WsTransport`

## Local Integration Tests

```bash
DOZOR_INTEGRATION_BASE_URL=http://localhost:3008 npm run test:integration
```

Optional known component check:

```bash
DOZOR_INTEGRATION_BASE_URL=http://localhost:3008 \
DOZOR_INTEGRATION_COMPONENT=api \
npm run test:integration
```

## Release

- Published to npm as `@yome-network/dozor-sdk`.
- Releases are created from git tags matching `sdk-v*`.
- Versioning follows Semantic Versioning.
