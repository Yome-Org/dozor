import type { DozorProtocolError, DozorTransportError, DozorValidationError } from "./errors.js";

export const DOZOR_SIGNAL_ENDPOINT = "/signal";

export type DozorSeverity = "INFO" | "WARNING" | "CRITICAL";

export type DozorAcceptedStatus = "accepted" | "duplicate";

export interface DozorSignalRequest {
  component: string;
  severity: DozorSeverity;
  source: string;
  occurredAt: string;
  idempotencyKey?: string;
}

export type DozorSignalRequestField = keyof DozorSignalRequest;

export type DozorRedactedSignalRequest = {
  [K in DozorSignalRequestField]: DozorSignalRequest[K] | string;
};

export interface DozorAcceptedResponse {
  status: DozorAcceptedStatus;
  queueUtilization: number;
}

export interface DozorErrorResponse {
  code: string;
  message: string;
  details?: string | null;
}

export interface DozorTransportResponse {
  status: number;
  headers: Record<string, string>;
  bodyText: string;
}

export interface DozorTransport {
  sendSignal(request: DozorSignalRequest): Promise<DozorTransportResponse>;
}

export interface DozorRetryDecisionContext {
  attempt: number;
  maxAttempts: number;
  request: DozorSignalRequest;
}

export interface DozorRetryPolicy {
  maxAttempts: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  backoffMultiplier?: number;
  shouldRetry?: (error: unknown, context: DozorRetryDecisionContext) => boolean;
}

export interface DozorRedactionOptions {
  fields?: DozorSignalRequestField[];
  replacement?: string;
}

export interface DozorRequestHookPayload {
  request: DozorSignalRequest;
  redactedRequest: DozorRedactedSignalRequest;
  attempt: number;
  maxAttempts: number;
}

export interface DozorResponseHookPayload {
  request: DozorSignalRequest;
  redactedRequest: DozorRedactedSignalRequest;
  responseStatus: number;
  result: DozorSignalResult;
  attempt: number;
  maxAttempts: number;
}

export interface DozorErrorHookPayload {
  request: DozorSignalRequest;
  redactedRequest: DozorRedactedSignalRequest;
  error: unknown;
  attempt: number;
  maxAttempts: number;
  willRetry: boolean;
}

export interface DozorHooks {
  onRequest?: (payload: DozorRequestHookPayload) => void | Promise<void>;
  onResponse?: (payload: DozorResponseHookPayload) => void | Promise<void>;
  onError?: (payload: DozorErrorHookPayload) => void | Promise<void>;
}

export interface DozorLogger {
  debug?: (message: string, context?: Record<string, unknown>) => void;
  info?: (message: string, context?: Record<string, unknown>) => void;
  warn?: (message: string, context?: Record<string, unknown>) => void;
  error?: (message: string, context?: Record<string, unknown>) => void;
}

export interface DozorClientOptions {
  transport: DozorTransport;
  retryPolicy?: Partial<DozorRetryPolicy>;
  hooks?: DozorHooks;
  logger?: DozorLogger;
  redaction?: DozorRedactionOptions;
}

export interface DozorSendSignalOptions {
  retryPolicy?: Partial<DozorRetryPolicy>;
}

export interface DozorSignalAcceptedResult {
  ok: true;
  httpStatus: 200 | 202;
  body: DozorAcceptedResponse;
}

export interface DozorSignalRejectedResult {
  ok: false;
  httpStatus: 400 | 404 | 429;
  body: DozorErrorResponse;
}

export type DozorSignalResult = DozorSignalAcceptedResult | DozorSignalRejectedResult;

export type DozorSdkError = DozorValidationError | DozorTransportError | DozorProtocolError;
