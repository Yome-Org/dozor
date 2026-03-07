import { describe, expect, it, vi } from "vitest";
import { DozorTransportError, HttpDozorTransport } from "../src/index.js";

describe("HttpDozorTransport", () => {
  it("sends POST request with expected URL, headers and body", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ status: "accepted", queueUtilization: 0.1 }), {
        status: 202,
        headers: {
          "x-request-id": "abc123",
        },
      }),
    );

    const transport = new HttpDozorTransport({
      baseUrl: "http://dozor:3008/",
      fetchImpl: fetchMock,
      defaultHeaders: { "x-service": "catalog" },
    });

    const response = await transport.sendSignal({
      component: "catalog",
      severity: "WARNING",
      source: "runtime-error:read-model",
      occurredAt: "2026-03-07T10:00:00.000Z",
      idempotencyKey: "catalog:runtime:warning:123",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://dozor:3008/signal");

    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.method).toBe("POST");
    expect((init?.headers as Record<string, string>)["content-type"]).toBe("application/json");
    expect((init?.headers as Record<string, string>)["x-service"]).toBe("catalog");
    expect((init?.headers as Record<string, string>)["idempotency-key"]).toBe(
      "catalog:runtime:warning:123",
    );

    const body = JSON.parse(String(init?.body));
    expect(body).toEqual({
      component: "catalog",
      severity: "WARNING",
      source: "runtime-error:read-model",
      occurredAt: "2026-03-07T10:00:00.000Z",
      idempotencyKey: "catalog:runtime:warning:123",
    });

    expect(response.status).toBe(202);
    expect(response.headers["x-request-id"]).toBe("abc123");
    expect(response.bodyText).toContain("accepted");
  });

  it("omits idempotency header/body when idempotencyKey is not provided", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response("{}", { status: 200 }));

    const transport = new HttpDozorTransport({
      baseUrl: "http://dozor:3008",
      fetchImpl: fetchMock,
    });

    await transport.sendSignal({
      component: "catalog",
      severity: "INFO",
      source: "heartbeat",
      occurredAt: "2026-03-07T10:00:00.000Z",
    });

    const init = fetchMock.mock.calls[0]?.[1];
    const headers = init?.headers as Record<string, string>;
    expect(headers["idempotency-key"]).toBeUndefined();

    const body = JSON.parse(String(init?.body));
    expect(body.idempotencyKey).toBeUndefined();
  });

  it("wraps transport-level failures into DozorTransportError", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(new Error("socket hang up"));
    const transport = new HttpDozorTransport({
      baseUrl: "http://dozor:3008",
      fetchImpl: fetchMock,
    });

    await expect(
      transport.sendSignal({
        component: "catalog",
        severity: "CRITICAL",
        source: "bootstrap-failure",
        occurredAt: "2026-03-07T10:00:00.000Z",
      }),
    ).rejects.toBeInstanceOf(DozorTransportError);
  });
});
