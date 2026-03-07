import type { ZodError } from "zod";
import { DozorProtocolError, DozorTransportError, DozorValidationError } from "./errors.js";
import {
  DozorAcceptedResponseSchema,
  DozorErrorResponseSchema,
  DozorSignalRequestSchema,
} from "./schemas.js";
import type {
  DozorClientOptions,
  DozorErrorHookPayload,
  DozorHooks,
  DozorRedactedSignalRequest,
  DozorRequestHookPayload,
  DozorResponseHookPayload,
  DozorRetryPolicy,
  DozorSendSignalOptions,
  DozorSignalRequest,
  DozorSignalRequestField,
  DozorSignalResult,
} from "./types.js";

const ACCEPTED_STATUSES = new Set([200, 202]);
const REJECTED_STATUSES = new Set([400, 404, 429]);
const DEFAULT_REDACTED_FIELDS: DozorSignalRequestField[] = ["idempotencyKey"];
const DEFAULT_RETRY_POLICY: Required<Omit<DozorRetryPolicy, "shouldRetry">> = {
  maxAttempts: 1,
  baseDelayMs: 250,
  maxDelayMs: 5_000,
  backoffMultiplier: 2,
};

const normalizeZodError = (error: ZodError): string[] =>
  error.issues.map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join(".") : "root";
    return `${path}: ${issue.message}`;
  });

const parseJsonBody = (bodyText: string, status: number): unknown => {
  if (!bodyText.trim()) {
    return {};
  }

  try {
    return JSON.parse(bodyText);
  } catch {
    throw new DozorProtocolError("Dozor response body is not valid JSON", status, bodyText);
  }
};

const wait = async (ms: number): Promise<void> =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });

const defaultShouldRetry = (error: unknown): boolean => error instanceof DozorTransportError;

const normalizeSdkError = (error: unknown): Error => {
  if (
    error instanceof DozorValidationError ||
    error instanceof DozorProtocolError ||
    error instanceof DozorTransportError
  ) {
    return error;
  }

  if (error instanceof Error) {
    return new DozorTransportError(error.message, error);
  }

  return new DozorTransportError("Dozor transport failed", error);
};

export class DozorClient {
  private readonly transport;
  private readonly hooks;
  private readonly logger;
  private readonly redactedFields;
  private readonly redactionReplacement;
  private readonly retryPolicy;

  constructor(options: DozorClientOptions) {
    this.transport = options.transport;
    this.hooks = options.hooks;
    this.logger = options.logger;
    this.redactedFields = options.redaction?.fields ?? DEFAULT_REDACTED_FIELDS;
    this.redactionReplacement = options.redaction?.replacement ?? "[REDACTED]";
    this.retryPolicy = {
      ...DEFAULT_RETRY_POLICY,
      ...options.retryPolicy,
      shouldRetry: options.retryPolicy?.shouldRetry ?? defaultShouldRetry,
    };
  }

  async sendSignal(request: unknown, options?: DozorSendSignalOptions): Promise<DozorSignalResult> {
    const parsedRequest = DozorSignalRequestSchema.safeParse(request);
    if (!parsedRequest.success) {
      throw new DozorValidationError(
        "Invalid Dozor signal request payload",
        normalizeZodError(parsedRequest.error),
      );
    }

    const payload = parsedRequest.data;
    const redactedPayload = this.redactRequest(payload);
    const retryPolicy = {
      ...this.retryPolicy,
      ...options?.retryPolicy,
      shouldRetry: options?.retryPolicy?.shouldRetry ?? this.retryPolicy.shouldRetry,
    };

    const maxAttempts = Math.max(1, retryPolicy.maxAttempts);

    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      await this.callHook("onRequest", {
        request: payload,
        redactedRequest: redactedPayload,
        attempt,
        maxAttempts,
      });

      try {
        const raw = await this.transport.sendSignal(payload);
        const result = this.parseSignalResult(raw.status, raw.bodyText);

        await this.callHook("onResponse", {
          request: payload,
          redactedRequest: redactedPayload,
          responseStatus: raw.status,
          result,
          attempt,
          maxAttempts,
        });

        return result;
      } catch (error) {
        const normalizedError = normalizeSdkError(error);
        const willRetry =
          attempt < maxAttempts &&
          Boolean(
            retryPolicy.shouldRetry?.(normalizedError, {
              attempt,
              maxAttempts,
              request: payload,
            }),
          );

        await this.callHook("onError", {
          request: payload,
          redactedRequest: redactedPayload,
          error: normalizedError,
          attempt,
          maxAttempts,
          willRetry,
        });

        if (!willRetry) {
          throw normalizedError;
        }

        const delayMs = this.retryDelayMs(attempt, retryPolicy);
        this.logger?.warn?.("Retrying Dozor signal after transient failure", {
          attempt,
          maxAttempts,
          delayMs,
          request: redactedPayload,
          errorType: normalizedError.name,
        });

        await wait(delayMs);
      }
    }

    throw new DozorTransportError("Dozor signal retries exhausted");
  }

  private parseSignalResult(status: number, bodyText: string): DozorSignalResult {
    const body = parseJsonBody(bodyText, status);

    if (ACCEPTED_STATUSES.has(status)) {
      const parsedBody = DozorAcceptedResponseSchema.safeParse(body);
      if (!parsedBody.success) {
        throw new DozorProtocolError(
          `Unexpected Dozor success response schema (status=${status})`,
          status,
          bodyText,
        );
      }

      return {
        ok: true,
        httpStatus: status as 200 | 202,
        body: parsedBody.data,
      };
    }

    if (REJECTED_STATUSES.has(status)) {
      const parsedBody = DozorErrorResponseSchema.safeParse(body);
      if (!parsedBody.success) {
        throw new DozorProtocolError(
          `Unexpected Dozor error response schema (status=${status})`,
          status,
          bodyText,
        );
      }

      return {
        ok: false,
        httpStatus: status as 400 | 404 | 429,
        body: parsedBody.data,
      };
    }

    throw new DozorProtocolError(`Unexpected Dozor response status (${status})`, status, bodyText);
  }

  private retryDelayMs(
    attempt: number,
    policy: Required<Omit<DozorRetryPolicy, "shouldRetry">>,
  ): number {
    const exponent = Math.max(0, attempt - 1);
    const delay = policy.baseDelayMs * policy.backoffMultiplier ** exponent;
    return Math.min(policy.maxDelayMs, Math.max(0, Math.floor(delay)));
  }

  private redactRequest(request: DozorSignalRequest): DozorRedactedSignalRequest {
    const output: DozorRedactedSignalRequest = {
      component: request.component,
      severity: request.severity,
      source: request.source,
      occurredAt: request.occurredAt,
      idempotencyKey: request.idempotencyKey,
    };

    for (const field of this.redactedFields) {
      if (output[field] !== undefined) {
        output[field] = this.redactionReplacement;
      }
    }

    return output;
  }

  private async callHook(hookName: "onRequest", payload: DozorRequestHookPayload): Promise<void>;
  private async callHook(hookName: "onResponse", payload: DozorResponseHookPayload): Promise<void>;
  private async callHook(hookName: "onError", payload: DozorErrorHookPayload): Promise<void>;
  private async callHook(
    hookName: "onRequest" | "onResponse" | "onError",
    payload: DozorRequestHookPayload | DozorResponseHookPayload | DozorErrorHookPayload,
  ): Promise<void> {
    try {
      if (hookName === "onRequest") {
        await this.hooks?.onRequest?.(payload as DozorRequestHookPayload);
        return;
      }

      if (hookName === "onResponse") {
        await this.hooks?.onResponse?.(payload as DozorResponseHookPayload);
        return;
      }

      await this.hooks?.onError?.(payload as DozorErrorHookPayload);
    } catch (error) {
      this.logger?.debug?.("Dozor hook failed", {
        hookName,
        errorType: error instanceof Error ? error.name : "unknown",
      });
    }
  }
}

export const createDozorClient = (options: DozorClientOptions): DozorClient =>
  new DozorClient(options);
