import type { ChatRunContextDetail } from "@/types";

export function normalizeStickySources(value?: Record<string, unknown> | null): Array<[string, string]> {
  const fieldSources = value?.fieldSources;
  if (!fieldSources || typeof fieldSources !== "object") {
    return [];
  }
  return Object.entries(fieldSources as Record<string, unknown>).map(([field, source]) => [
    field,
    sourceLabel(String(source)),
  ]);
}

export function normalizeStickyResolvedState(value?: Record<string, unknown> | null): Array<[string, string]> {
  const resolvedState = value?.resolvedState;
  if (!resolvedState || typeof resolvedState !== "object") {
    return [];
  }
  return Object.entries(resolvedState as Record<string, unknown>)
    .map(([field, stateValue]) => [field, formatInspectorValue(stateValue)] as [string, string])
    .filter(([, stateValue]) => stateValue.length > 0);
}

export function onOff(value?: boolean | null) {
  return value ? "on" : "off";
}

export function formatRetrievalResolutionStatus(
  resolution: NonNullable<ChatRunContextDetail["retrievalQueryResolution"]>,
) {
  if (resolution.degraded) {
    return "degraded";
  }
  if (resolution.decision === "RESOLVED") {
    return "resolved";
  }
  return "original";
}

function formatInspectorValue(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return "empty";
    }
    if (value.every((item) => typeof item === "string" || typeof item === "number")) {
      return value.join(", ");
    }
    return `${value.length} items`;
  }
  try {
    const serialized = JSON.stringify(value);
    return serialized.length <= 180 ? serialized : `${serialized.slice(0, 177)}...`;
  } catch {
    return "object";
  }
}

function sourceLabel(source: string) {
  if (source === "request") {
    return "explicit";
  }
  if (source === "sticky") {
    return "sticky";
  }
  if (source === "conversation_default" || source === "app_default") {
    return "default";
  }
  return source;
}
