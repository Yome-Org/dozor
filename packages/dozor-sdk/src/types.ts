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

export interface DozorClientOptions {
  transport: DozorTransport;
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
