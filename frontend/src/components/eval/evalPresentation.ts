import type { EvalCaseSeverity, EvalDatasetKind, EvalExpectedMode, EvalReviewStatus } from "@/types";

export const ALL_VALUE = "__all__";
export const NONE_VALUE = "__none__";

export const datasetKindLabels: Record<EvalDatasetKind, string> = {
  CANDIDATE: "Candidate",
  GOLDEN: "Golden",
  SMOKE: "Smoke",
};

export const severityLabels: Record<EvalCaseSeverity, string> = {
  BLOCKER: "Blocker",
  HIGH: "High",
  LOW: "Low",
  MEDIUM: "Medium",
};

export const expectedModeLabels: Record<EvalExpectedMode, string> = {
  ABSTAIN: "Abstain",
  ANSWER: "Answer",
  CLARIFY: "Clarify",
};

export const reviewStatusLabels: Record<EvalReviewStatus, string> = {
  APPROVED: "Approved",
  ARCHIVED: "Archived",
  DRAFT: "Draft",
  PROMOTED: "Promoted",
  READY_FOR_REVIEW: "Ready",
  REJECTED: "Rejected",
};

export const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};

export const asArray = (value: unknown): unknown[] => Array.isArray(value) ? value : [];

export const shortId = (value?: string | null) => value ? value.slice(0, 8) : "n/a";

export const formatDuration = (start?: string | null, end?: string | null) => {
  if (!start || !end) {
    return "n/a";
  }
  const durationMs = new Date(end).getTime() - new Date(start).getTime();
  if (!Number.isFinite(durationMs) || durationMs < 0) {
    return "n/a";
  }
  return durationMs < 1000 ? `${durationMs} ms` : `${Math.round(durationMs / 1000)} s`;
};

export const badgeForRunStatus = (status?: string | null): "success" | "destructive" | "warning" | "secondary" => {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "FAILED" || status === "CANCELLED") {
    return "destructive";
  }
  if (status === "RUNNING" || status === "QUEUED") {
    return "warning";
  }
  return "secondary";
};

export const badgeForItemStatus = (status?: string | null): "success" | "destructive" | "warning" | "secondary" => {
  if (status === "PASSED") {
    return "success";
  }
  if (status === "FAILED" || status === "ERROR") {
    return "destructive";
  }
  if (status === "RUNNING" || status === "PENDING") {
    return "warning";
  }
  return "secondary";
};

export const splitLines = (value: string) =>
  value
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter(Boolean);

export const parseJson = <T,>(value: string, fallback: T): T => {
  const trimmed = value.trim();
  if (!trimmed) {
    return fallback;
  }
  return JSON.parse(trimmed) as T;
};

export const metricEntries = (value: unknown) =>
  Object.entries(asRecord(value))
    .filter(([, entryValue]) => typeof entryValue === "number" || typeof entryValue === "string" || typeof entryValue === "boolean")
    .slice(0, 8);

export type MetricDeltaRow = {
  baselineValue: unknown;
  candidateValue: unknown;
  delta: unknown;
  direction: string;
  metric: string;
  nonScorableCount: unknown;
  sampleSize: unknown;
  verdict: string;
};

export const metricDeltaRows = (metricSummary: Record<string, unknown>): MetricDeltaRow[] =>
  Object.entries(metricSummary)
    .map(([name, value]) => {
      const entry = asRecord(value);
      return {
        baselineValue: entry.baselineValue,
        candidateValue: entry.candidateValue,
        delta: entry.delta,
        direction: String(entry.direction ?? "n/a"),
        metric: String(entry.metric ?? name),
        nonScorableCount: entry.nonScorableCount,
        sampleSize: entry.sampleSize,
        verdict: String(entry.verdict ?? "n/a"),
      };
    })
    .sort((left, right) => left.metric.localeCompare(right.metric));

export const formatMetricValue = (value: unknown) => {
  if (typeof value === "number") {
    return Number.isInteger(value) ? String(value) : value.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
  }
  if (value === null || value === undefined) {
    return "n/a";
  }
  return String(value);
};

export const badgeForMetricVerdict = (verdict: string): "success" | "destructive" | "warning" | "secondary" => {
  if (verdict === "REGRESSED") {
    return "destructive";
  }
  if (verdict === "IMPROVED" || verdict === "UNCHANGED") {
    return "success";
  }
  if (verdict === "INSUFFICIENT_DATA") {
    return "warning";
  }
  return "secondary";
};

export const badgeForOverallVerdict = (verdict?: string | null): "success" | "destructive" | "warning" | "secondary" => {
  if (verdict === "PASS") {
    return "success";
  }
  if (verdict === "FAIL" || verdict === "BLOCKED") {
    return "destructive";
  }
  if (verdict === "INCONCLUSIVE") {
    return "warning";
  }
  return "secondary";
};
