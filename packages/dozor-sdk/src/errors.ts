export class DozorValidationError extends Error {
  readonly issues: string[];

  constructor(message: string, issues: string[]) {
    super(message);
    this.name = "DozorValidationError";
    this.issues = issues;
  }
}

export class DozorTransportError extends Error {
  readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = "DozorTransportError";
    this.cause = cause;
  }
}

export class DozorProtocolError extends Error {
  readonly status: number;
  readonly bodyText: string;

  constructor(message: string, status: number, bodyText: string) {
    super(message);
    this.name = "DozorProtocolError";
    this.status = status;
    this.bodyText = bodyText;
  }
}
