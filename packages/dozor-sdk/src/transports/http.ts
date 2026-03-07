import { DozorTransportError } from "../errors.js";
import {
  DOZOR_SIGNAL_ENDPOINT,
  type DozorSignalRequest,
  type DozorTransport,
  type DozorTransportResponse,
} from "../types.js";

export interface HttpDozorTransportOptions {
  baseUrl: string;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
  defaultHeaders?: Record<string, string>;
}

const DEFAULT_TIMEOUT_MS = 1_500;

const normalizeBaseUrl = (value: string): string => value.replace(/\/+$/, "");

const headersToRecord = (headers: Headers): Record<string, string> => {
  const output: Record<string, string> = {};
  headers.forEach((value, key) => {
    output[key.toLowerCase()] = value;
  });
  return output;
};

export class HttpDozorTransport implements DozorTransport {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly fetchImpl: typeof fetch;
  private readonly defaultHeaders: Record<string, string>;

  constructor(options: HttpDozorTransportOptions) {
    this.baseUrl = normalizeBaseUrl(options.baseUrl);
    this.timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.defaultHeaders = options.defaultHeaders ?? {};
  }

  async sendSignal(request: DozorSignalRequest): Promise<DozorTransportResponse> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await this.fetchImpl(`${this.baseUrl}${DOZOR_SIGNAL_ENDPOINT}`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          ...this.defaultHeaders,
          ...(request.idempotencyKey ? { "idempotency-key": request.idempotencyKey } : {}),
        },
        body: JSON.stringify({
          component: request.component,
          severity: request.severity,
          source: request.source,
          occurredAt: request.occurredAt,
          ...(request.idempotencyKey ? { idempotencyKey: request.idempotencyKey } : {}),
        }),
        signal: controller.signal,
      });

      const bodyText = await response.text();

      return {
        status: response.status,
        headers: headersToRecord(response.headers),
        bodyText,
      };
    } catch (error) {
      throw new DozorTransportError("Failed to send Dozor signal", error);
    } finally {
      clearTimeout(timeout);
    }
  }
}
