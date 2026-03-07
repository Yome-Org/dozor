import { describe, expect, it, vi } from "vitest";
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

  it("retries transport errors and succeeds", async () => {
    const sendSignal = vi
      .fn<DozorTransport["sendSignal"]>()
      .mockRejectedValueOnce(new DozorTransportError("temporary timeout"))
      .mockResolvedValueOnce({
        status: 202,
        headers: { "content-type": "application/json" },
        bodyText: JSON.stringify({ status: "accepted", queueUtilization: 0.01 }),
      });

    const client = createDozorClient({
      transport: { sendSignal },
      retryPolicy: { maxAttempts: 2, baseDelayMs: 1, maxDelayMs: 1 },
    });

    const result = await client.sendSignal({
      component: "catalog",
      severity: "INFO",
      source: "runtime-error:test",
      occurredAt: "2026-03-07T10:00:00.000Z",
    });

    expect(result.ok).toBe(true);
    expect(sendSignal).toHaveBeenCalledTimes(2);
  });

  it("supports per-call retry override via options", async () => {
    const sendSignal = vi
      .fn<DozorTransport["sendSignal"]>()
      .mockRejectedValue(new Error("temporary timeout"));

    const client = createDozorClient({
      transport: { sendSignal },
      retryPolicy: {
        maxAttempts: 1,
        shouldRetry: () => true,
        baseDelayMs: 1,
        maxDelayMs: 1,
      },
    });

    await expect(
      client.sendSignal(
        {
          component: "catalog",
          severity: "INFO",
          source: "runtime-error:test",
          occurredAt: "2026-03-07T10:00:00.000Z",
        },
        {
          retryPolicy: {
            maxAttempts: 3,
            shouldRetry: () => true,
            baseDelayMs: 1,
            maxDelayMs: 1,
          },
        },
      ),
    ).rejects.toThrow();

    expect(sendSignal).toHaveBeenCalledTimes(3);
  });

  it("normalizes generic transport errors to DozorTransportError", async () => {
    const transport: DozorTransport = {
      sendSignal: async () => {
        throw new Error("socket hang up");
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

  it("calls hooks and redacts configured fields", async () => {
    const onRequest = vi.fn();
    const onResponse = vi.fn();
    const onError = vi.fn();

    const client = createDozorClient({
      transport: makeTransport({
        status: 202,
        bodyText: JSON.stringify({ status: "accepted", queueUtilization: 0.12 }),
      }),
      hooks: { onRequest, onResponse, onError },
      redaction: { fields: ["idempotencyKey", "source"] },
    });

    await client.sendSignal({
      component: "catalog",
      severity: "WARNING",
      source: "runtime-error:read-model",
      occurredAt: "2026-03-07T10:00:00.000Z",
      idempotencyKey: "catalog:runtime:warning:123",
    });

    expect(onRequest).toHaveBeenCalledTimes(1);
    const requestPayload = onRequest.mock.calls[0]?.[0];
    expect(requestPayload.redactedRequest.idempotencyKey).toBe("[REDACTED]");
    expect(requestPayload.redactedRequest.source).toBe("[REDACTED]");

    expect(onResponse).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(0);
  });

  it("propagates transport errors and emits onError hook", async () => {
    const onError = vi.fn();

    const transport: DozorTransport = {
      sendSignal: async () => {
        throw new DozorTransportError("network timeout");
      },
    };
    const client = createDozorClient({ transport, hooks: { onError } });

    await expect(
      client.sendSignal({
        component: "catalog",
        severity: "INFO",
        source: "runtime-error:test",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorTransportError);

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0]?.[0].willRetry).toBe(false);
  });

  it("logs retry attempts when logger is provided", async () => {
    const sendSignal = vi
      .fn<DozorTransport["sendSignal"]>()
      .mockRejectedValueOnce(new DozorTransportError("temporary timeout"))
      .mockResolvedValueOnce({
        status: 202,
        headers: { "content-type": "application/json" },
        bodyText: JSON.stringify({ status: "accepted", queueUtilization: 0.01 }),
      });
    const warn = vi.fn();

    const client = createDozorClient({
      transport: { sendSignal },
      retryPolicy: { maxAttempts: 2, baseDelayMs: 1, maxDelayMs: 1 },
      logger: { warn },
    });

    await client.sendSignal({
      component: "catalog",
      severity: "INFO",
      source: "runtime-error:test",
      occurredAt: "2026-03-07T10:00:00.000Z",
      idempotencyKey: "secret-value",
    });

    expect(warn).toHaveBeenCalledTimes(1);
    expect(warn.mock.calls[0]?.[1]?.request?.idempotencyKey).toBe("[REDACTED]");
  });
});
