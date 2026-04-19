import type {
  RetrievalFilters,
  RetrievalQueryHints,
  SourceTrustLevel,
} from "@/types";

export type RetrievalFilterKey = keyof RetrievalFilters;
export type RetrievalHintKey = keyof RetrievalQueryHints;

export const RETRIEVAL_FILTER_KEYS: RetrievalFilterKey[] = [
  "documentNumber",
  "documentDateFrom",
  "documentDateTo",
  "department",
  "project",
  "counterparty",
  "businessStatus",
  "language",
  "tags",
  "sourceTrustMin",
];

export const HINT_OWNED_FILTER_KEYS: RetrievalFilterKey[] = [
  "documentNumber",
  "documentDateFrom",
  "documentDateTo",
  "department",
  "project",
  "counterparty",
  "businessStatus",
  "language",
];

const ISO_DATE_PATTERN = /\b(\d{4}-\d{2}-\d{2})\b/g;
const DOTTED_DATE_PATTERN = /\b(\d{2}[./]\d{2}[./]\d{4})\b/g;
const RANGE_PATTERN = /(?:с|from)\s+(\d{4}-\d{2}-\d{2}|\d{2}[./]\d{2}[./]\d{4})\s+(?:по|to)\s+(\d{4}-\d{2}-\d{2}|\d{2}[./]\d{2}[./]\d{4})/iu;
const DOCUMENT_NUMBER_MARKER_PATTERN = /№\s*([\p{L}\p{N}][\p{L}\p{N}/._-]{2,})/iu;
const DOCUMENT_NUMBER_CODE_PATTERN = /\b([A-Z]{2,}(?:-[A-Z0-9]{2,}){1,})\b/g;
const VERSION_PATTERN = /(v\d+(?:\.\d+)*)|(rev\s*\d+)|(revision\s*\d+)|(версия\s*\d+)/iu;
const PROJECT_PATTERN = /(?:по\s+проекту|project)\s+([\p{L}\p{N}][\p{L}\p{N} .&()/_-]{1,80})/iu;
const COUNTERPARTY_PATTERN = /(?:контрагент|counterparty)\s*[: ]\s*([\p{L}\p{N}][\p{L}\p{N} .&()/_-]{1,80})/iu;
const STATUS_PATTERN = /(?:status|статус)\s*[: ]\s*([\p{L}\p{N}][\p{L}\p{N} .&()/_-]{1,60})/iu;
const DEPARTMENT_PATTERN = /(?:подразделение|департамент|department)\s*[: ]\s*([\p{L}\p{N}][\p{L}\p{N} .&()/_-]{1,80})/iu;

export const emptyRetrievalFilters = (): RetrievalFilters => ({
  documentNumber: null,
  documentDateFrom: null,
  documentDateTo: null,
  department: null,
  project: null,
  counterparty: null,
  businessStatus: null,
  language: null,
  tags: [],
  sourceTrustMin: null,
});

export const extractRetrievalQueryHints = (query: string): RetrievalQueryHints => {
  const normalized = query.trim();
  if (!normalized) {
    return {};
  }

  const dateRange = RANGE_PATTERN.exec(normalized);
  const standaloneDates = collectDates(normalized);
  const versionMatch = VERSION_PATTERN.exec(normalized);

  return {
    documentNumber: extractDocumentNumber(normalized),
    documentDateFrom: dateRange ? normalizeDate(dateRange[1]) : standaloneDates[0] ?? null,
    documentDateTo: dateRange ? normalizeDate(dateRange[2]) : standaloneDates[0] ?? null,
    versionLabel: versionMatch
      ? [versionMatch[1], versionMatch[2], versionMatch[3], versionMatch[4]].find(Boolean) ?? null
      : null,
    language: extractLanguage(normalized),
    project: extractFacet(normalized, PROJECT_PATTERN),
    counterparty: extractFacet(normalized, COUNTERPARTY_PATTERN),
    businessStatus: extractFacet(normalized, STATUS_PATTERN),
    department: extractFacet(normalized, DEPARTMENT_PATTERN),
  };
};

export const normalizeRetrievalFilters = (filters?: Partial<RetrievalFilters> | null): RetrievalFilters => ({
  documentNumber: normalizeOptionalText(filters?.documentNumber),
  documentDateFrom: normalizeOptionalDate(filters?.documentDateFrom),
  documentDateTo: normalizeOptionalDate(filters?.documentDateTo),
  department: normalizeOptionalText(filters?.department),
  project: normalizeOptionalText(filters?.project),
  counterparty: normalizeOptionalText(filters?.counterparty),
  businessStatus: normalizeOptionalText(filters?.businessStatus),
  language: normalizeOptionalText(filters?.language),
  tags: normalizeTags(filters?.tags),
  sourceTrustMin: (filters?.sourceTrustMin ?? null) as SourceTrustLevel | null,
});

export const mergeHintFilters = (
  manualFilters: Partial<RetrievalFilters> | null | undefined,
  hints: Partial<RetrievalQueryHints> | null | undefined,
  dismissedHintKeys: Set<RetrievalFilterKey>,
): RetrievalFilters => {
  const normalizedManual = normalizeRetrievalFilters(manualFilters);
  const next: RetrievalFilters = { ...normalizedManual };

  for (const key of HINT_OWNED_FILTER_KEYS) {
    if (dismissedHintKeys.has(key)) {
      next[key] = null as never;
      continue;
    }
    const manualValue = normalizedManual[key];
    if (hasRetrievalFilterValue(manualValue)) {
      continue;
    }
    const hintValue = hintValueForFilter(key, hints);
    if (hasRetrievalFilterValue(hintValue)) {
      next[key] = hintValue as never;
    }
  }

  return normalizeRetrievalFilters(next);
};

export const isRetrievalFiltersEmpty = (filters?: Partial<RetrievalFilters> | null) => {
  const normalized = normalizeRetrievalFilters(filters);
  return !RETRIEVAL_FILTER_KEYS.some((key) => hasRetrievalFilterValue(normalized[key]));
};

export const formatRetrievalFilterValue = (key: RetrievalFilterKey, value: RetrievalFilters[RetrievalFilterKey]) => {
  if (value == null) {
    return "";
  }
  if (Array.isArray(value)) {
    return value.join(", ");
  }
  return String(value);
};

const collectDates = (query: string) => {
  const dates = new Set<string>();
  for (const match of query.matchAll(ISO_DATE_PATTERN)) {
    const normalized = normalizeDate(match[1]);
    if (normalized) {
      dates.add(normalized);
    }
  }
  for (const match of query.matchAll(DOTTED_DATE_PATTERN)) {
    const normalized = normalizeDate(match[1]);
    if (normalized) {
      dates.add(normalized);
    }
  }
  return Array.from(dates);
};

const extractDocumentNumber = (query: string) => {
  const markerMatch = DOCUMENT_NUMBER_MARKER_PATTERN.exec(query);
  if (markerMatch?.[1]) {
    return markerMatch[1];
  }

  DOCUMENT_NUMBER_CODE_PATTERN.lastIndex = 0;
  const codeMatch = DOCUMENT_NUMBER_CODE_PATTERN.exec(query.toUpperCase());
  return codeMatch?.[1] ?? null;
};

const extractLanguage = (query: string) => {
  const lower = query.toLowerCase();
  if (
    lower.includes("на русском")
    || lower.includes("русский")
    || lower.includes("русском")
    || /\bru\b/i.test(query)
  ) {
    return "ru";
  }
  if (
    lower.includes("на английском")
    || lower.includes("английский")
    || lower.includes("английском")
    || lower.includes("english")
    || /\ben\b/i.test(query)
  ) {
    return "en";
  }
  if (
    lower.includes("на казахском")
    || lower.includes("казахский")
    || lower.includes("казахском")
    || /\bkk\b/i.test(query)
  ) {
    return "kk";
  }
  return null;
};

const extractFacet = (query: string, pattern: RegExp) => {
  const match = pattern.exec(query);
  if (!match?.[1]) {
    return null;
  }
  return match[1]
    .trim()
    .replace(/[,.;:!?]+$/g, "")
    .replace(/\s+(договор|contract|контрагент|counterparty|status|статус|подразделение|department|version|версия)\b.*$/iu, "")
    .trim();
};

const hintValueForFilter = (
  key: RetrievalFilterKey,
  hints: Partial<RetrievalQueryHints> | null | undefined,
) => {
  switch (key) {
    case "documentNumber":
      return hints?.documentNumber ?? null;
    case "documentDateFrom":
      return hints?.documentDateFrom ?? null;
    case "documentDateTo":
      return hints?.documentDateTo ?? null;
    case "department":
      return hints?.department ?? null;
    case "project":
      return hints?.project ?? null;
    case "counterparty":
      return hints?.counterparty ?? null;
    case "businessStatus":
      return hints?.businessStatus ?? null;
    case "language":
      return hints?.language ?? null;
    default:
      return null;
  }
};

const normalizeDate = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const normalized = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
    return normalized;
  }
  const dotted = normalized.replace(/\//g, ".");
  const match = dotted.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (!match) {
    return null;
  }
  return `${match[3]}-${match[2]}-${match[1]}`;
};

const normalizeOptionalText = (value?: string | null) => {
  if (value == null) {
    return null;
  }
  const normalized = value.trim();
  return normalized ? normalized : null;
};

const normalizeOptionalDate = (value?: string | null) => normalizeDate(value);

const normalizeTags = (tags?: string[] | null) => {
  if (!tags || tags.length === 0) {
    return [];
  }
  return Array.from(
    new Set(
      tags
        .map((tag) => normalizeOptionalText(tag))
        .filter((tag): tag is string => Boolean(tag)),
    ),
  );
};

export const hasRetrievalFilterValue = (value: unknown) => {
  if (value == null) {
    return false;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (typeof value === "string") {
    return value.trim().length > 0;
  }
  return true;
};
