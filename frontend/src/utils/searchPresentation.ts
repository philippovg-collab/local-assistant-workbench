import type { HealthResponse } from "@/types";

export type SearchPresentation = {
  label: string;
  statusLabel: string;
  detail: string;
  syncLabel: string;
  tone: "online" | "idle" | "warn";
};

const providerLabel = (provider?: string) => {
  if (provider === "postgres") {
    return "PostgreSQL";
  }
  if (provider === "elasticsearch") {
    return "Elasticsearch";
  }
  return provider ?? "Search plane";
};

export const buildSearchPresentation = (health: HealthResponse | null): SearchPresentation => {
  if (!health) {
    return {
      label: "Search plane",
      statusLabel: "Проверяем",
      detail: "Ожидаем backend health snapshot.",
      syncLabel: "Elasticsearch sync: проверяем",
      tone: "idle",
    };
  }

  const searchStatus = health.searchStatus ?? "DISABLED";
  const searchMode = health.searchMode;
  const searchProvider = health.searchProvider;
  const provider = providerLabel(searchProvider);

  if (searchMode === "postgres" && searchProvider === "postgres") {
    return {
      label: "PostgreSQL lexical",
      statusLabel: "Активен",
      detail:
        "RAG lexical retrieval работает через PostgreSQL. Elasticsearch sync отключён конфигом и для этого режима не обязателен.",
      syncLabel: searchStatus === "DISABLED"
        ? "Elasticsearch sync: выключен"
        : `Elasticsearch sync: ${searchStatus}`,
      tone: "online",
    };
  }

  if (searchStatus === "UP" && searchProvider === "elasticsearch") {
    return {
      label: "Elasticsearch lexical",
      statusLabel: "Активен",
      detail: "Production lexical retrieval идёт через Elasticsearch; PostgreSQL остаётся fallback path.",
      syncLabel: "Elasticsearch sync: активен",
      tone: "online",
    };
  }

  if (searchMode === "auto" && searchProvider === "postgres") {
    return {
      label: "PostgreSQL fallback",
      statusLabel: "Fallback активен",
      detail: health.searchReasonMessage
        ? `RAG lexical retrieval продолжает работать через PostgreSQL fallback: ${health.searchReasonMessage}`
        : "RAG lexical retrieval продолжает работать через PostgreSQL fallback.",
      syncLabel: `Elasticsearch sync: ${searchStatus}`,
      tone: "warn",
    };
  }

  if (searchStatus === "DISABLED") {
    return {
      label: provider,
      statusLabel: "Sync выключен",
      detail: health.searchReasonMessage ?? "Elasticsearch search sync выключен конфигом.",
      syncLabel: "Elasticsearch sync: выключен",
      tone: "idle",
    };
  }

  if (searchStatus === "DEGRADED" || searchStatus === "DOWN") {
    return {
      label: provider,
      statusLabel: searchStatus,
      detail: health.searchReasonMessage ?? "Search plane сейчас деградирован.",
      syncLabel: `Elasticsearch sync: ${searchStatus}`,
      tone: "warn",
    };
  }

  return {
    label: provider,
    statusLabel: searchStatus === "UP" ? "Активен" : searchStatus,
    detail: health.searchReasonMessage ?? "Search plane сейчас не деградирован.",
    syncLabel: `Elasticsearch sync: ${searchStatus}`,
    tone: searchStatus === "UP" ? "online" : "idle",
  };
};
