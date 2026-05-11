import type { AuthSession } from "../types";
import { ApiClientError } from "./errors";

const API_URL = (import.meta.env.VITE_API_URL ?? "").trim().replace(/\/$/, "");
const BACKEND_ORIGIN = (import.meta.env.VITE_BACKEND_ORIGIN ?? "").trim().replace(/\/$/, "");
const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:8080";
const FRONTEND_SAME_ORIGIN_PORTS = new Set(["8080", "8088"]);
const FRONTEND_DEV_PORTS = new Set(["5173", "4173"]);
const LOCAL_HOSTNAMES = new Set(["127.0.0.1", "localhost"]);
const CSRF_UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

let csrfHeaderName = "X-XSRF-TOKEN";
let csrfToken: string | null = null;

type ApiErrorPayload = {
  code?: string;
  error?: string;
  message?: string;
  requestId?: string;
  timestamp?: string;
};

const resolvePort = (origin: string, port: string) => {
  if (port) {
    return port;
  }

  try {
    return new URL(origin).port;
  } catch {
    return "";
  }
};

const isFrontendDevOrigin = (origin: string) => {
  try {
    const url = new URL(origin);
    return LOCAL_HOSTNAMES.has(url.hostname) && FRONTEND_DEV_PORTS.has(resolvePort(url.origin, url.port));
  } catch {
    return false;
  }
};

export const resolveApiBaseUrl = () => {
  if (typeof window === "undefined") {
    return API_URL || BACKEND_ORIGIN || DEFAULT_BACKEND_ORIGIN;
  }

  const { hostname, origin, port, protocol } = window.location;
  const resolvedPort = resolvePort(origin, port);
  const isLocalHostname = LOCAL_HOSTNAMES.has(hostname);

  if (!resolvedPort || FRONTEND_SAME_ORIGIN_PORTS.has(resolvedPort)) {
    return origin;
  }
  if (!isLocalHostname || protocol === "https:") {
    return origin;
  }
  if (FRONTEND_DEV_PORTS.has(resolvedPort)) {
    if (API_URL && !isFrontendDevOrigin(API_URL)) {
      return API_URL;
    }
    return BACKEND_ORIGIN || `${protocol}//${hostname}:8080`;
  }
  return API_URL || BACKEND_ORIGIN || DEFAULT_BACKEND_ORIGIN;
};

const buildApiUrl = (path: string) => `${resolveApiBaseUrl()}${path}`;

const buildStatusMessage = (response: Response) =>
  `API responded with status ${response.status}${response.statusText ? ` ${response.statusText}` : ""}`;

const looksLikeHtml = (contentType: string, text: string) =>
  contentType.includes("text/html") || /^\s*<(?:!doctype\s+html|html|head|body)\b/i.test(text);

const readErrorPayload = async (response: Response): Promise<ApiErrorPayload> => {
  const contentType = response.headers.get("content-type") ?? "";

  if (contentType.includes("application/json")) {
    try {
      return (await response.json()) as ApiErrorPayload;
    } catch {
      return {};
    }
  }

  const text = await response.text();
  if (!text || looksLikeHtml(contentType, text)) {
    return {
      message: buildStatusMessage(response),
    };
  }

  return {
    message: text,
  };
};

const buildApiClientError = async (response: Response) => {
  const payload = await readErrorPayload(response);
  const message = payload.message ?? payload.error ?? buildStatusMessage(response);

  return new ApiClientError(message, {
    code: payload.code,
    requestId: payload.requestId,
    status: response.status,
    timestamp: payload.timestamp,
  });
};

export const rememberCsrf = (session: AuthSession) => {
  if (session.csrfHeaderName) {
    csrfHeaderName = session.csrfHeaderName;
  }
  csrfToken = session.csrfToken ?? null;
};

const shouldAttachCsrf = (method?: string) =>
  CSRF_UNSAFE_METHODS.has((method ?? "GET").toUpperCase()) && csrfToken !== null;

const buildRequestInit = (init: RequestInit = {}): RequestInit => {
  const headers = new Headers(init.headers);
  if (shouldAttachCsrf(init.method)) {
    headers.set(csrfHeaderName, csrfToken ?? "");
  }
  return {
    ...init,
    credentials: "include",
    headers,
  };
};

export const requestJson = async <T>(path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), buildRequestInit(init));
  if (!response.ok) {
    throw await buildApiClientError(response);
  }

  return (await response.json()) as T;
};

export const requestVoid = async (path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), buildRequestInit(init));
  if (!response.ok) {
    throw await buildApiClientError(response);
  }
};
