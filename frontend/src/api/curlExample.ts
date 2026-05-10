import type { ChatExecutionRequest } from "@/types";

const API_URL = (import.meta.env.VITE_API_URL ?? "").trim().replace(/\/$/, "");
const BACKEND_ORIGIN = (import.meta.env.VITE_BACKEND_ORIGIN ?? "").trim().replace(/\/$/, "");
const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:8080";
const FRONTEND_SAME_ORIGIN_PORTS = new Set(["8080", "8088"]);
const FRONTEND_DEV_PORTS = new Set(["5173", "4173"]);
const LOCAL_HOSTNAMES = new Set(["127.0.0.1", "localhost"]);

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

const resolveCurlBaseUrl = () => {
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

export const buildChatRunCurlExample = (input: ChatExecutionRequest) => {
  const curlBaseUrl = resolveCurlBaseUrl();

  return `curl ${curlBaseUrl}/api/chat-runs \\
  -H "Content-Type: application/json" \\
  --data-binary @- <<'JSON'
${JSON.stringify(input, null, 2)}
JSON`;
};
