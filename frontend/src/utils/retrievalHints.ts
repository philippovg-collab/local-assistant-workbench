import type {
  DocumentStatus,
  DocumentType,
  MaterialLanguageCode,
  MaterialVersionState,
  RetrievalFilters,
  RetrievalQueryHints,
  SourceTrustLevel,
  VersionSelectionMode,
} from "@/types";

export type RetrievalFilterKey = keyof RetrievalFilters;
export type RetrievalHintKey = keyof RetrievalQueryHints;

export const RETRIEVAL_FILTER_KEYS: RetrievalFilterKey[] = [
  "documentNumber",
  "documentDateFrom",
  "documentDateTo",
  "versionLabel",
  "effectiveDate",
  "versionSelectionMode",
  "versionState",
  "uploadedAfterInclusive",
  "uploadedBeforeExclusive",
  "department",
  "project",
  "counterparty",
  "businessStatus",
  "language",
  "documentTypes",
  "documentStatuses",
  "projectKeys",
  "languageCodes",
  "periodStartFrom",
  "periodStartTo",
  "periodEndFrom",
  "periodEndTo",
  "tags",
  "sourceTrustMin",
];

export const HINT_OWNED_FILTER_KEYS: RetrievalFilterKey[] = [
  "documentNumber",
  "documentDateFrom",
  "documentDateTo",
  "versionLabel",
  "department",
  "project",
  "counterparty",
  "businessStatus",
  "language",
  "documentTypes",
  "documentStatuses",
  "languageCodes",
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
  versionLabel: null,
  effectiveDate: null,
  versionSelectionMode: null,
  versionState: null,
  uploadedAfterInclusive: null,
  uploadedBeforeExclusive: null,
  department: null,
  project: null,
  counterparty: null,
  businessStatus: null,
  language: null,
  tags: [],
  sourceTrustMin: null,
  documentTypes: [],
  documentStatuses: [],
  projectKeys: [],
  languageCodes: [],
  periodStartFrom: null,
  periodStartTo: null,
  periodEndFrom: null,
  periodEndTo: null,
});

export const extractRetrievalQueryHints = (query: string): RetrievalQueryHints => {
  const normalized = query.trim();
  if (!normalized) {
    return {};
  }

  const dateRange = RANGE_PATTERN.exec(normalized);
  const standaloneDates = collectDates(normalized);
  const versionMatch = VERSION_PATTERN.exec(normalized);
  const languageCodes = extractLanguageCode(normalized);

  return {
    documentNumber: extractDocumentNumber(normalized),
    documentDateFrom: dateRange ? normalizeDate(dateRange[1]) : standaloneDates[0] ?? null,
    documentDateTo: dateRange ? normalizeDate(dateRange[2]) : standaloneDates[0] ?? null,
    versionLabel: versionMatch
      ? [versionMatch[1], versionMatch[2], versionMatch[3], versionMatch[4]].find(Boolean) ?? null
      : null,
    language: legacyLanguageValue(languageCodes),
    project: extractFacet(normalized, PROJECT_PATTERN),
    counterparty: extractFacet(normalized, COUNTERPARTY_PATTERN),
    businessStatus: extractFacet(normalized, STATUS_PATTERN),
    department: extractFacet(normalized, DEPARTMENT_PATTERN),
    documentTypes: extractDocumentTypes(normalized),
    documentStatuses: extractDocumentStatuses(normalized),
    projectKeys: [],
    languageCodes,
    periodStartFrom: null,
    periodStartTo: null,
    periodEndFrom: null,
    periodEndTo: null,
  };
};

export const normalizeRetrievalFilters = (filters?: Partial<RetrievalFilters> | null): RetrievalFilters => ({
  documentNumber: normalizeOptionalText(filters?.documentNumber),
  documentDateFrom: normalizeOptionalDate(filters?.documentDateFrom),
  documentDateTo: normalizeOptionalDate(filters?.documentDateTo),
  versionLabel: normalizeOptionalText(filters?.versionLabel),
  effectiveDate: normalizeOptionalDate(filters?.effectiveDate),
  versionSelectionMode: normalizeVersionSelectionMode(filters?.versionSelectionMode),
  versionState: normalizeMaterialVersionState(filters?.versionState),
  uploadedAfterInclusive: normalizeOptionalInstant(filters?.uploadedAfterInclusive),
  uploadedBeforeExclusive: normalizeOptionalInstant(filters?.uploadedBeforeExclusive),
  department: normalizeOptionalText(filters?.department),
  project: normalizeOptionalText(filters?.project),
  counterparty: normalizeOptionalText(filters?.counterparty),
  businessStatus: normalizeOptionalText(filters?.businessStatus),
  language: normalizeOptionalText(filters?.language),
  tags: normalizeTags(filters?.tags),
  sourceTrustMin: (filters?.sourceTrustMin ?? null) as SourceTrustLevel | null,
  documentTypes: normalizeEnumList<DocumentType>(filters?.documentTypes),
  documentStatuses: normalizeEnumList<DocumentStatus>(filters?.documentStatuses),
  projectKeys: normalizeStringList(filters?.projectKeys),
  languageCodes: normalizeEnumList<MaterialLanguageCode>(filters?.languageCodes),
  periodStartFrom: normalizeOptionalDate(filters?.periodStartFrom),
  periodStartTo: normalizeOptionalDate(filters?.periodStartTo),
  periodEndFrom: normalizeOptionalDate(filters?.periodEndFrom),
  periodEndTo: normalizeOptionalDate(filters?.periodEndTo),
});

export const mergeHintFilters = (
  manualFilters: Partial<RetrievalFilters> | null | undefined,
  hints: Partial<RetrievalQueryHints> | null | undefined,
  dismissedHintKeys: Set<RetrievalFilterKey>,
): RetrievalFilters => {
  const normalizedManual = normalizeRetrievalFilters(manualFilters);
  const next: RetrievalFilters = { ...normalizedManual };
  const dismissedDocumentDate =
    dismissedHintKeys.has("documentDateFrom") || dismissedHintKeys.has("documentDateTo");
  const hasManualProjectCriteria =
    hasRetrievalFilterValue(normalizedManual.project) || hasRetrievalFilterValue(normalizedManual.projectKeys);
  const hasManualLanguageCriteria =
    hasRetrievalFilterValue(normalizedManual.language) || hasRetrievalFilterValue(normalizedManual.languageCodes);

  for (const key of HINT_OWNED_FILTER_KEYS) {
    if (dismissedHintKeys.has(key) || (dismissedDocumentDate && isDocumentDateFilterKey(key))) {
      next[key] = null as never;
      continue;
    }
    if (hasManualProjectCriteria && isProjectFilterKey(key)) {
      continue;
    }
    if (hasManualLanguageCriteria && isLanguageFilterKey(key)) {
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

const extractLanguageCode = (query: string): MaterialLanguageCode[] => {
  const lower = query.toLowerCase();
  if (
    lower.includes("на русском")
    || lower.includes("русский")
    || lower.includes("русском")
    || /\bru\b/i.test(query)
  ) {
    return ["RU"];
  }
  if (
    lower.includes("на английском")
    || lower.includes("английский")
    || lower.includes("английском")
    || lower.includes("english")
    || /\ben\b/i.test(query)
  ) {
    return ["EN"];
  }
  if (
    lower.includes("на казахском")
    || lower.includes("казахский")
    || lower.includes("казахском")
    || /\bkk\b/i.test(query)
  ) {
    return ["KK"];
  }
  return [];
};

const legacyLanguageValue = (languageCodes: MaterialLanguageCode[]) =>
  languageCodes.length === 0 ? null : languageCodes[0].toLowerCase();

const extractDocumentTypes = (query: string): DocumentType[] => {
  const normalized = query.toLowerCase();
  if (normalized.includes("договор") || normalized.includes("contract")) {
    return ["CONTRACT"];
  }
  if (normalized.includes("политик") || normalized.includes("policy")) {
    return ["POLICY"];
  }
  if (normalized.includes("отчет") || normalized.includes("отчёт") || normalized.includes("report")) {
    return ["REPORT"];
  }
  if (normalized.includes("регламент") || normalized.includes("procedure")) {
    return ["PROCEDURE"];
  }
  if (normalized.includes("презентац") || normalized.includes("presentation")) {
    return ["PRESENTATION"];
  }
  if (normalized.includes("таблиц") || normalized.includes("spreadsheet")) {
    return ["SPREADSHEET"];
  }
  if (normalized.includes("письм") || normalized.includes("letter")) {
    return ["LETTER"];
  }
  if (normalized.includes("инструкц") || normalized.includes("manual")) {
    return ["MANUAL"];
  }
  if (normalized.includes("faq")) {
    return ["FAQ"];
  }
  return [];
};

const extractDocumentStatuses = (query: string): DocumentStatus[] => {
  const statusText = extractFacet(query, STATUS_PATTERN);
  if (!statusText) {
    return [];
  }
  const normalized = statusText.toLowerCase();
  if (normalized.includes("active") || normalized.includes("действ") || normalized.includes("актив")) {
    return ["ACTIVE"];
  }
  if (normalized.includes("draft") || normalized.includes("чернов")) {
    return ["DRAFT"];
  }
  if (normalized.includes("archive") || normalized.includes("архив")) {
    return ["ARCHIVED"];
  }
  if (normalized.includes("revoked") || normalized.includes("отозв") || normalized.includes("отмен")) {
    return ["REVOKED"];
  }
  return [];
};

const extractFacet = (query: string, pattern: RegExp) => {
  const match = pattern.exec(query);
  if (!match?.[1]) {
    return null;
  }
  const normalized = match[1]
    .trim()
    .replace(/[,.;:!?]+$/g, "")
    .replace(/\s+(договор|contract|контрагент|counterparty|status|статус|подразделение|department|version|версия|on|in)(?:\s|$).*$/iu, "")
    .trim();
  return normalized || null;
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
    case "versionLabel":
      return hints?.versionLabel ?? null;
    case "department":
      return hints?.department ?? null;
    case "project":
      return hints?.project ?? null;
    case "projectKeys":
      return hints?.projectKeys ?? [];
    case "counterparty":
      return hints?.counterparty ?? null;
    case "businessStatus":
      return hints?.businessStatus ?? null;
    case "language":
      return hints?.language ?? null;
    case "documentTypes":
      return hints?.documentTypes ?? [];
    case "documentStatuses":
      return hints?.documentStatuses ?? [];
    case "languageCodes":
      return hints?.languageCodes ?? [];
    case "periodStartFrom":
      return hints?.periodStartFrom ?? null;
    case "periodStartTo":
      return hints?.periodStartTo ?? null;
    case "periodEndFrom":
      return hints?.periodEndFrom ?? null;
    case "periodEndTo":
      return hints?.periodEndTo ?? null;
    default:
      return null;
  }
};

const isDocumentDateFilterKey = (key: RetrievalFilterKey) =>
  key === "documentDateFrom" || key === "documentDateTo";

const isProjectFilterKey = (key: RetrievalFilterKey) =>
  key === "project" || key === "projectKeys";

const isLanguageFilterKey = (key: RetrievalFilterKey) =>
  key === "language" || key === "languageCodes";

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

const normalizeOptionalInstant = (value?: string | null) => {
  const normalized = normalizeOptionalText(value);
  if (!normalized) {
    return null;
  }
  const timestamp = Date.parse(normalized);
  return Number.isNaN(timestamp) ? null : new Date(timestamp).toISOString();
};

const VERSION_SELECTION_MODES = new Set<VersionSelectionMode>([
  "ACTIVE_ONLY",
  "INCLUDE_HISTORY",
  "VERSION_LABEL",
  "VERSION_STATE",
]);

const MATERIAL_VERSION_STATES = new Set<MaterialVersionState>([
  "ACTIVE",
  "SUPERSEDED",
]);

const normalizeVersionSelectionMode = (value?: VersionSelectionMode | null) =>
  value && VERSION_SELECTION_MODES.has(value) ? value : null;

const normalizeMaterialVersionState = (value?: MaterialVersionState | null) =>
  value && MATERIAL_VERSION_STATES.has(value) ? value : null;

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

const normalizeStringList = (values?: Array<string | null | undefined> | null) => {
  if (!values || values.length === 0) {
    return [];
  }
  return Array.from(
    new Set(
      values
        .map((value) => normalizeOptionalText(value))
        .filter((value): value is string => Boolean(value)),
    ),
  );
};

const normalizeEnumList = <Value extends string>(values?: Value[] | null) => normalizeStringList(values) as Value[];

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
