import { describe, expect, it } from "vitest";
import { HttpDozorTransport, createDozorClient } from "../src/index.js";

const baseUrl = process.env.DOZOR_INTEGRATION_BASE_URL;
const knownComponent = process.env.DOZOR_INTEGRATION_COMPONENT;

const hasIntegrationEnv = Boolean(baseUrl);

describe.skipIf(!hasIntegrationEnv)("Dozor SDK integration", () => {
  it("returns typed 404 response for unknown component against real Dozor", async () => {
    const client = createDozorClient({
      transport: new HttpDozorTransport({ baseUrl: String(baseUrl), timeoutMs: 2_000 }),
    });

    const result = await client.sendSignal({
      component: "sdk-integration-unknown-component",
      severity: "WARNING",
      source: "integration-test:unknown-component",
      occurredAt: new Date().toISOString(),
      idempotencyKey: `sdk-integration:${Date.now()}`,
    });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.httpStatus).toBe(404);
      expect(result.body.code).toBe("unknown_component");
    }
  });

  it.skipIf(!knownComponent)("returns typed accepted response for known component", async () => {
    const client = createDozorClient({
      transport: new HttpDozorTransport({ baseUrl: String(baseUrl), timeoutMs: 2_000 }),
    });

    const result = await client.sendSignal({
      component: String(knownComponent),
      severity: "INFO",
      source: "integration-test:known-component",
      occurredAt: new Date().toISOString(),
      idempotencyKey: `sdk-integration:${Date.now()}`,
    });

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect([200, 202]).toContain(result.httpStatus);
      expect(["accepted", "duplicate"]).toContain(result.body.status);
    }
  });
});
