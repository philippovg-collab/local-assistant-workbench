import type { HealthResponse, MaterialSummary } from "../types";

export type RagReadinessState = "empty" | "historical-only" | "indexing" | "ready" | "degraded";

export type RagReadiness = {
  state: RagReadinessState;
  totalMaterialsCount: number;
  activeMaterialsCount: number;
  historicalMaterialsCount: number;
  readyMaterialsCount: number;
  hasActiveIndexing: boolean;
  hasAnyMaterials: boolean;
  hasOnlyHistoricalMaterials: boolean;
  isRagReady: boolean;
};

export type RagReadinessPresentation = {
  headline: string;
  overviewMessage: string;
  materialsMessage: string | null;
  chatHelperText: string;
  badgeLabel: string;
  isRagSubmitBlocked: boolean;
  statusDotClass: "idle" | "online" | "warn";
  materialsMessageClassName: "helper" | "warning-state";
};

type BuildRagReadinessPresentationInput = {
  health: HealthResponse | null;
  ragReadiness: RagReadiness;
  isLoadingMaterials: boolean;
  materialsError: string | null;
  selectedModel: string;
};

export const isActiveMaterialVersion = (material: MaterialSummary) =>
  (material.versionState ?? "ACTIVE") === "ACTIVE";

export const isReadyMaterial = (material: MaterialSummary) =>
  isActiveMaterialVersion(material)
  && (material.status === "READY" || material.status === "PARTIAL_READY");

export const hasActiveIndexing = (materials: MaterialSummary[]) =>
  materials.some(
    (material) =>
      isActiveMaterialVersion(material)
      && (material.status === "PENDING" || material.status === "IN_PROGRESS"),
  );

export const deriveRagReadiness = (health: HealthResponse | null, materials: MaterialSummary[]) => {
  const activeMaterials = materials.filter(isActiveMaterialVersion);
  const readyMaterials = activeMaterials.filter(isReadyMaterial);
  const hasAnyMaterials = materials.length > 0;
  const hasOnlyHistoricalMaterials = hasAnyMaterials && activeMaterials.length === 0;
  const hasIndexingInFlight = hasActiveIndexing(materials);
  let state: RagReadinessState = "degraded";

  if (!hasAnyMaterials) {
    state = "empty";
  } else if (hasOnlyHistoricalMaterials) {
    state = "historical-only";
  } else if (health?.ragStatus === "DOWN") {
    state = "degraded";
  } else if (readyMaterials.length > 0) {
    state = "ready";
  } else if (hasIndexingInFlight) {
    state = "indexing";
  }

  return {
    state,
    totalMaterialsCount: materials.length,
    activeMaterialsCount: activeMaterials.length,
    historicalMaterialsCount: materials.length - activeMaterials.length,
    readyMaterialsCount: readyMaterials.length,
    hasActiveIndexing: hasIndexingInFlight,
    hasAnyMaterials,
    hasOnlyHistoricalMaterials,
    isRagReady: state === "ready",
  };
};

const getRagDegradedReason = (health: HealthResponse | null) =>
  health?.embeddingReasonMessage
  ?? health?.vectorReasonMessage
  ?? health?.databaseReasonMessage
  ?? health?.llmReasonMessage
  ?? null;

const buildDegradedMessage = (health: HealthResponse | null) => {
  const degradedReason = getRagDegradedReason(health);
  return degradedReason
    ? `RAG backend сейчас деградирован: ${degradedReason}`
    : "Активные материалы есть, но ни один ещё не готов для retrieval.";
};

export const buildRagReadinessPresentation = ({
  health,
  ragReadiness,
  isLoadingMaterials,
  materialsError,
  selectedModel,
}: BuildRagReadinessPresentationInput): RagReadinessPresentation => {
  const badgeLabel = `${ragReadiness.activeMaterialsCount} active / ${ragReadiness.totalMaterialsCount} total`;
  const degradedMessage = buildDegradedMessage(health);

  let headline = `${ragReadiness.readyMaterialsCount}/${ragReadiness.activeMaterialsCount}`;
  if (ragReadiness.state === "empty") {
    headline = "Пустая база";
  } else if (ragReadiness.state === "historical-only") {
    headline = "Только история версий";
  } else if (ragReadiness.state === "degraded") {
    headline = "Readiness degraded";
  }

  let overviewMessage = "Активные материалы и readiness считаются по одной модели состояния.";
  if (materialsError) {
    overviewMessage = `Не удалось загрузить материалы: ${materialsError}.`;
  } else if (isLoadingMaterials) {
    overviewMessage = "Сканируем локальное хранилище.";
  } else if (ragReadiness.state === "empty") {
    overviewMessage = "В knowledge base пока нет материалов.";
  } else if (ragReadiness.state === "historical-only") {
    overviewMessage = "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.";
  } else if (ragReadiness.state === "indexing") {
    overviewMessage = "Активная версия уже принята, а индекс ещё догоняет её до READY или PARTIAL_READY.";
  } else if (ragReadiness.state === "degraded") {
    overviewMessage = degradedMessage;
  }

  let materialsMessage: string | null = null;
  if (!isLoadingMaterials && !materialsError) {
    if (ragReadiness.state === "empty") {
      materialsMessage =
        "В каталоге пока нет материалов. Добавь хотя бы один активный материал, чтобы RAG мог работать по контексту.";
    } else if (ragReadiness.state === "historical-only") {
      materialsMessage =
        "В каталоге сейчас только исторические версии. Они видны ниже для аудита, но исключены из retrieval и readiness.";
    } else if (ragReadiness.state === "indexing") {
      materialsMessage =
        "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.";
    } else if (ragReadiness.state === "degraded") {
      materialsMessage = degradedMessage;
    } else {
      materialsMessage =
        "RAG использует только активные READY и PARTIAL_READY версии, а historical остаются в каталоге для аудита.";
    }
  }

  let chatHelperText = `Вопрос уйдёт в ${selectedModel} с локально подобранным контекстом из активных материалов.`;
  if (isLoadingMaterials) {
    chatHelperText = "Проверяем локальное хранилище материалов.";
  } else if (materialsError) {
    chatHelperText = `Не удалось загрузить материалы: ${materialsError}.`;
  } else if (ragReadiness.state === "empty") {
    chatHelperText = "Сначала добавь материалы, иначе RAG-режим не на чем grounded.";
  } else if (ragReadiness.state === "historical-only") {
    chatHelperText =
      "В каталоге сейчас только исторические версии. Они видны для аудита, но исключены из retrieval. Добавь новую активную версию, чтобы RAG снова стал доступен.";
  } else if (ragReadiness.state === "indexing") {
    chatHelperText =
      "Активная версия уже принята, но индекс ещё собирается. Как только появится хотя бы один READY- или PARTIAL_READY-материал, RAG сможет отвечать по контексту.";
  } else if (ragReadiness.state === "degraded") {
    chatHelperText = degradedMessage;
  }

  return {
    headline,
    overviewMessage,
    materialsMessage,
    chatHelperText,
    badgeLabel,
    isRagSubmitBlocked: !isLoadingMaterials && !materialsError && ragReadiness.activeMaterialsCount === 0,
    statusDotClass: ragReadiness.state === "degraded"
      ? "warn"
      : ragReadiness.state === "ready"
        ? "online"
        : "idle",
    materialsMessageClassName:
      ragReadiness.state === "historical-only" || ragReadiness.state === "degraded"
        ? "warning-state"
        : "helper",
  };
};
