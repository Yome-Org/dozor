import type { ZodError } from "zod";
import { DozorProtocolError, DozorValidationError } from "./errors.js";
import {
  DozorAcceptedResponseSchema,
  DozorErrorResponseSchema,
  DozorSignalRequestSchema,
} from "./schemas.js";
import type { DozorClientOptions, DozorSignalResult } from "./types.js";

const ACCEPTED_STATUSES = new Set([200, 202]);
const REJECTED_STATUSES = new Set([400, 404, 429]);

const normalizeZodError = (error: ZodError): string[] =>
  error.issues.map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join(".") : "root";
    return `${path}: ${issue.message}`;
  });

const parseJsonBody = (bodyText: string): unknown => {
  if (!bodyText.trim()) {
    return {};
  }

  try {
    return JSON.parse(bodyText);
  } catch {
    throw new DozorProtocolError("Dozor response body is not valid JSON", -1, bodyText);
  }
};

export class DozorClient {
  private readonly transport;

  constructor(options: DozorClientOptions) {
    this.transport = options.transport;
  }

  async sendSignal(request: unknown): Promise<DozorSignalResult> {
    const parsedRequest = DozorSignalRequestSchema.safeParse(request);
    if (!parsedRequest.success) {
      throw new DozorValidationError(
        "Invalid Dozor signal request payload",
        normalizeZodError(parsedRequest.error),
      );
    }

    const raw = await this.transport.sendSignal(parsedRequest.data);

    const body = parseJsonBody(raw.bodyText);

    if (ACCEPTED_STATUSES.has(raw.status)) {
      const parsedBody = DozorAcceptedResponseSchema.safeParse(body);
      if (!parsedBody.success) {
        throw new DozorProtocolError(
          `Unexpected Dozor success response schema (status=${raw.status})`,
          raw.status,
          raw.bodyText,
        );
      }

      return {
        ok: true,
        httpStatus: raw.status as 200 | 202,
        body: parsedBody.data,
      };
    }

    if (REJECTED_STATUSES.has(raw.status)) {
      const parsedBody = DozorErrorResponseSchema.safeParse(body);
      if (!parsedBody.success) {
        throw new DozorProtocolError(
          `Unexpected Dozor error response schema (status=${raw.status})`,
          raw.status,
          raw.bodyText,
        );
      }

      return {
        ok: false,
        httpStatus: raw.status as 400 | 404 | 429,
        body: parsedBody.data,
      };
    }

    throw new DozorProtocolError(
      `Unexpected Dozor response status (${raw.status})`,
      raw.status,
      raw.bodyText,
    );
  }
}

export const createDozorClient = (options: DozorClientOptions): DozorClient =>
  new DozorClient(options);
