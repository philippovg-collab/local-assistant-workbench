export class ApiClientError extends Error {
  code?: string;
  requestId?: string;
  status: number;
  timestamp?: string;

  constructor(
    message: string,
    options: {
      code?: string;
      requestId?: string;
      status: number;
      timestamp?: string;
    },
  ) {
    super(message);
    this.name = "ApiClientError";
    this.code = options.code;
    this.requestId = options.requestId;
    this.status = options.status;
    this.timestamp = options.timestamp;
  }
}

export const isApiClientError = (error: unknown): error is ApiClientError =>
  error instanceof ApiClientError;
