import { describe, expect, it } from "vitest";
import {
  DozorProtocolError,
  type DozorTransport,
  DozorTransportError,
  DozorValidationError,
  createDozorClient,
} from "../src/index.js";

const makeTransport = (response: { status: number; bodyText: string }): DozorTransport => ({
  sendSignal: async () => ({
    status: response.status,
    headers: { "content-type": "application/json" },
    bodyText: response.bodyText,
  }),
});

describe("DozorClient", () => {
  it("returns accepted result for 202 response", async () => {
    const client = createDozorClient({
      transport: makeTransport({
        status: 202,
        bodyText: JSON.stringify({ status: "accepted", queueUtilization: 0.12 }),
      }),
    });

    const result = await client.sendSignal({
      component: "catalog",
      severity: "WARNING",
      source: "runtime-error:read-model",
      occurredAt: "2026-03-07T10:00:00.000Z",
      idempotencyKey: "catalog:runtime:warning:123",
    });

    expect(result.ok).toBe(true);
    expect(result.httpStatus).toBe(202);
    if (result.ok) {
      expect(result.body.status).toBe("accepted");
    }
  });

  it("returns rejected result for 429 response", async () => {
    const client = createDozorClient({
      transport: makeTransport({
        status: 429,
        bodyText: JSON.stringify({ code: "backpressure", message: "queue is full" }),
      }),
    });

    const result = await client.sendSignal({
      component: "catalog",
      severity: "CRITICAL",
      source: "bootstrap-failure",
      occurredAt: "2026-03-07T10:00:00.000Z",
    });

    expect(result.ok).toBe(false);
    expect(result.httpStatus).toBe(429);
    if (!result.ok) {
      expect(result.body.code).toBe("backpressure");
    }
  });

  it("throws validation error for malformed payload", async () => {
    const client = createDozorClient({ transport: makeTransport({ status: 202, bodyText: "{}" }) });

    await expect(
      client.sendSignal({
        component: "",
        severity: "BAD",
        source: "x",
        occurredAt: "not-an-iso-date",
      }),
    ).rejects.toBeInstanceOf(DozorValidationError);
  });

  it("throws protocol error for unexpected status code", async () => {
    const client = createDozorClient({
      transport: makeTransport({
        status: 503,
        bodyText: JSON.stringify({ message: "unavailable" }),
      }),
    });

    await expect(
      client.sendSignal({
        component: "catalog",
        severity: "INFO",
        source: "runtime-error:test",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorProtocolError);
  });

  it("throws protocol error for invalid JSON response body", async () => {
    const client = createDozorClient({
      transport: makeTransport({ status: 202, bodyText: "not-json" }),
    });

    await expect(
      client.sendSignal({
        component: "catalog",
        severity: "INFO",
        source: "runtime-error:test",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorProtocolError);
  });

  it("throws protocol error for invalid schema in accepted response", async () => {
    const client = createDozorClient({
      transport: makeTransport({ status: 200, bodyText: JSON.stringify({ status: "accepted" }) }),
    });

    await expect(
      client.sendSignal({
        component: "catalog",
        severity: "INFO",
        source: "runtime-error:test",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorProtocolError);
  });

  it("propagates transport errors", async () => {
    const transport: DozorTransport = {
      sendSignal: async () => {
        throw new DozorTransportError("network timeout");
      },
    };
    const client = createDozorClient({ transport });

    await expect(
      client.sendSignal({
        component: "catalog",
        severity: "INFO",
        source: "runtime-error:test",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorTransportError);
  });
});
