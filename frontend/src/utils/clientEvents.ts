import { isApiClientError } from "@/api/errors";

const MAX_EVENTS_PER_MINUTE = 10;
const WINDOW_MS = 60_000;
const MAX_MESSAGE_CHARS = 2_000;
const MAX_STACK_CHARS = 6_000;
const MAX_METADATA_STRING_CHARS = 1_000;
const MAX_METADATA_DEPTH = 4;
const MAX_METADATA_KEYS = 40;
const MAX_METADATA_ITEMS = 30;
const SECRET_KEY_PATTERN = /^(password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)$/i;

let eventTimestamps: number[] = [];
let globalHandlersInstalled = false;
let clientEventReporter: ClientEventReporter | undefined;

export type ClientEventInput = {
  severity: "info" | "warn" | "error" | "fatal";
  type: string;
  message?: string;
  stack?: string;
  componentStack?: string;
  path?: string;
  requestId?: string;
  metadata?: Record<string, unknown>;
};

export type ClientEventReporter = (event: ClientEventInput) => void | Promise<void>;

const SECRET_PATTERNS: Array<[RegExp, string]> = [
  [/(Authorization\s*:\s*Bearer\s+)[^\s,;]+/gi, "$1[REDACTED]"],
  [/(Authorization\s*:\s*Basic\s+)[A-Za-z0-9+/=._~-]+/gi, "$1[REDACTED]"],
  [/\b(Bearer\s+)[A-Za-z0-9._~+\-/]+=*/gi, "$1[REDACTED]"],
  [/((?:Cookie|Set-Cookie)\s*:\s*)[^\r\n]+/gi, "$1[REDACTED]"],
  [/("(?:password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)"\s*:\s*")[^"]*(")/gi, "$1[REDACTED]$2"],
  [/\b(password|passwd|pwd|api[_-]?key|apikey|x-api-key|access[_-]?token|refresh[_-]?token|secret|token)(\s*[=:]\s*)([^\s,;&"']+)/gi, "$1$2[REDACTED]"],
];

export const currentClientPath = () => {
  if (typeof window === "undefined") {
    return undefined;
  }
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
};

export const sanitizeClientEvent = (event: ClientEventInput): ClientEventInput => ({
  severity: event.severity,
  type: clip(maskSecrets(event.type), MAX_MESSAGE_CHARS) ?? "unknown",
  message: clip(maskSecrets(event.message), MAX_MESSAGE_CHARS),
  stack: clip(maskSecrets(event.stack), MAX_STACK_CHARS),
  componentStack: clip(maskSecrets(event.componentStack), MAX_STACK_CHARS),
  path: clip(maskSecrets(event.path ?? currentClientPath()), MAX_MESSAGE_CHARS),
  requestId: clip(maskSecrets(event.requestId), MAX_MESSAGE_CHARS),
  metadata: sanitizeMetadata(event.metadata),
});

export const logClientEvent = (event: ClientEventInput) => {
  if (!clientEventReporter || !shouldSendEvent()) {
    return;
  }

  const sanitized = sanitizeClientEvent(event);
  void Promise.resolve(clientEventReporter(sanitized)).catch(() => undefined);
};

export const setClientEventReporter = (reporter?: ClientEventReporter) => {
  clientEventReporter = reporter;
};

export const installGlobalClientEventHandlers = () => {
  if (typeof window === "undefined" || globalHandlersInstalled) {
    return () => undefined;
  }
  globalHandlersInstalled = true;

  const handleError = (event: ErrorEvent) => {
    const payload = payloadFromUnknown(event.error, event.message);
    logClientEvent({
      severity: "error",
      type: "window.error",
      message: payload.message,
      stack: payload.stack,
      path: currentClientPath(),
      requestId: payload.requestId,
      metadata: {
        ...payload.metadata,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
      },
    });
  };

  const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
    const payload = payloadFromUnknown(event.reason);
    logClientEvent({
      severity: "error",
      type: "window.unhandledrejection",
      message: payload.message,
      stack: payload.stack,
      path: currentClientPath(),
      requestId: payload.requestId,
      metadata: payload.metadata,
    });
  };

  window.addEventListener("error", handleError);
  window.addEventListener("unhandledrejection", handleUnhandledRejection);

  return () => {
    window.removeEventListener("error", handleError);
    window.removeEventListener("unhandledrejection", handleUnhandledRejection);
    globalHandlersInstalled = false;
  };
};

export const payloadFromUnknown = (value: unknown, fallbackMessage = "Unknown client error") => {
  if (isApiClientError(value)) {
    return {
      message: value.message,
      stack: value.stack,
      requestId: value.requestId,
      metadata: {
        apiStatus: value.status,
        apiCode: value.code,
        apiTimestamp: value.timestamp,
        apiRequestId: value.requestId,
      },
    };
  }

  if (value instanceof Error) {
    return {
      message: value.message || fallbackMessage,
      stack: value.stack,
      requestId: undefined,
      metadata: {
        errorName: value.name,
      },
    };
  }

  return {
    message: typeof value === "string" ? value : fallbackMessage,
    stack: undefined,
    requestId: undefined,
    metadata: {
      reason: sanitizeMetadataValue(value),
    },
  };
};

export const resetClientEventThrottleForTests = () => {
  eventTimestamps = [];
  globalHandlersInstalled = false;
  clientEventReporter = undefined;
};

const shouldSendEvent = () => {
  const now = Date.now();
  eventTimestamps = eventTimestamps.filter((timestamp) => now - timestamp < WINDOW_MS);
  if (eventTimestamps.length >= MAX_EVENTS_PER_MINUTE) {
    return false;
  }
  eventTimestamps.push(now);
  return true;
};

const sanitizeMetadata = (metadata?: Record<string, unknown>) => {
  if (!metadata) {
    return undefined;
  }
  const sanitized = sanitizeMetadataValue(metadata);
  return isPlainRecord(sanitized) ? sanitized : undefined;
};

const sanitizeMetadataValue = (
  value: unknown,
  depth = 0,
  seen = new WeakSet<object>(),
): unknown => {
  if (value === null || value === undefined || typeof value === "number" || typeof value === "boolean") {
    return value;
  }

  if (typeof value === "string") {
    return clip(maskSecrets(value), MAX_METADATA_STRING_CHARS);
  }

  if (typeof value === "bigint") {
    return value.toString();
  }

  if (typeof value === "function" || typeof value === "symbol") {
    return `[${typeof value}]`;
  }

  if (depth >= MAX_METADATA_DEPTH) {
    return "[depth-limit]";
  }

  if (Array.isArray(value)) {
    return value
      .slice(0, MAX_METADATA_ITEMS)
      .map((item) => sanitizeMetadataValue(item, depth + 1, seen));
  }

  if (typeof value === "object") {
    if (seen.has(value)) {
      return "[circular]";
    }
    seen.add(value);
    const entries = Object.entries(value as Record<string, unknown>).slice(0, MAX_METADATA_KEYS);
    return Object.fromEntries(
      entries.map(([key, nestedValue]) => {
        const safeKey = clip(maskSecrets(key), MAX_METADATA_STRING_CHARS) ?? "key";
        return [
          safeKey,
          SECRET_KEY_PATTERN.test(key) ? "[REDACTED]" : sanitizeMetadataValue(nestedValue, depth + 1, seen),
        ];
      }),
    );
  }

  return String(value);
};

const isPlainRecord = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const maskSecrets = (value?: string) => {
  if (value === undefined) {
    return undefined;
  }
  return SECRET_PATTERNS.reduce((result, [pattern, replacement]) => result.replace(pattern, replacement), value);
};

const clip = (value: string | undefined, maxLength: number) => {
  if (value === undefined || value.length <= maxLength) {
    return value;
  }
  const marker = "[truncated]";
  return `${value.slice(0, Math.max(0, maxLength - marker.length))}${marker}`;
};
