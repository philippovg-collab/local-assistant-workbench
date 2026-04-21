import type { HealthResponse, MaterialSummary } from "../types";

export type RagReadinessState = "empty" | "historical-only" | "indexing" | "ready" | "degraded";

export type RagReadiness = {
  state: RagReadinessState;
  totalMaterialsCount: number;
  activeMaterialsCount: number;
  historicalMaterialsCount: number;
  readyMaterialsCount: number;
  hasContractDrift: boolean;
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

export type DirectReadinessPresentation = {
  helperText: string;
  isDirectSubmitBlocked: boolean;
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

export const deriveRagReadiness = (health: HealthResponse | null): RagReadiness => {
  const totalMaterialsCount = health?.materialCount ?? 0;
  const activeMaterialsCount = health?.activeMaterialCount ?? 0;
  const historicalMaterialsCount = health?.historicalMaterialCount ?? Math.max(0, totalMaterialsCount - activeMaterialsCount);
  const readyMaterialsCount = health?.readyMaterialCount ?? 0;
  const hasContractDrift = Boolean(health) && !health?.knowledgeStatus;
  const hasAnyMaterials = totalMaterialsCount > 0;
  const hasOnlyHistoricalMaterials = hasAnyMaterials && activeMaterialsCount === 0;
  const hasIndexingInFlight = (health?.indexingPendingCount ?? 0) > 0 || (health?.indexingInProgressCount ?? 0) > 0;
  let state: RagReadinessState = "degraded";

  if (health?.knowledgeStatus === "EMPTY") {
    state = "empty";
  } else if (health?.knowledgeStatus === "HISTORICAL_ONLY") {
    state = "historical-only";
  } else if (health?.knowledgeStatus === "INDEXING") {
    state = "indexing";
  } else if (health?.knowledgeStatus === "READY") {
    state = "ready";
  } else if (health?.knowledgeStatus === "DEGRADED") {
    state = "degraded";
  } else if (hasContractDrift) {
    state = "degraded";
  } else if (!health && !hasAnyMaterials) {
    state = "empty";
  } else if (!health && hasOnlyHistoricalMaterials) {
    state = "historical-only";
  } else if (!health && hasIndexingInFlight) {
    state = "indexing";
  }

  return {
    state,
    totalMaterialsCount,
    activeMaterialsCount,
    historicalMaterialsCount,
    readyMaterialsCount,
    hasContractDrift,
    hasActiveIndexing: hasIndexingInFlight,
    hasAnyMaterials,
    hasOnlyHistoricalMaterials,
    isRagReady: state === "ready" && health?.ragStatus === "UP" && !hasContractDrift,
  };
};

const getRagDegradedReason = (health: HealthResponse | null) =>
  health?.ragDegradedReasonMessage
  ?? health?.directReasonMessage
  ?? health?.embeddingReasonMessage
  ?? health?.vectorReasonMessage
  ?? health?.databaseReasonMessage
  ?? health?.knowledgeReasonMessage
  ?? health?.llmReasonMessage
  ?? null;

const buildDegradedMessage = (health: HealthResponse | null) => {
  if (health && !health.knowledgeStatus) {
    return "Backend health contract неполный: knowledgeStatus отсутствует, поэтому RAG readiness нельзя подтвердить.";
  }
  const degradedReason = getRagDegradedReason(health);
  return degradedReason
    ? `RAG backend сейчас деградирован: ${degradedReason}`
    : "Активные материалы есть, но ни один не проходит retrieval-ready условия: READY/PARTIAL_READY, documentStatus=ACTIVE и валидный период.";
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

  let overviewMessage = "Readiness считается по active lineage, READY/PARTIAL_READY, действующему статусу документа и валидному периоду.";
  if (!health) {
    overviewMessage = "Проверяем backend readiness и состояние knowledge base.";
  } else if (ragReadiness.state === "empty") {
    overviewMessage = health.knowledgeReasonMessage ?? "В knowledge base пока нет материалов.";
  } else if (ragReadiness.state === "historical-only") {
    overviewMessage = health.knowledgeReasonMessage
      ?? "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.";
  } else if (ragReadiness.state === "indexing") {
    overviewMessage = health.knowledgeReasonMessage
      ?? "Активная версия уже принята, а индекс ещё догоняет её до READY или PARTIAL_READY.";
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
        "RAG использует только active lineage версии со статусом READY/PARTIAL_READY, documentStatus=ACTIVE и валидным периодом; historical остаются в каталоге для аудита.";
    }
  }

  let chatHelperText = `Вопрос уйдёт в ${selectedModel} с локально подобранным контекстом из активных материалов.`;
  if (!health) {
    chatHelperText = "Проверяем backend readiness и состояние knowledge base.";
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
    isRagSubmitBlocked: !health || health.ragStatus !== "UP" || ragReadiness.hasContractDrift,
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

export const buildDirectReadinessPresentation = (
  health: HealthResponse | null,
  selectedModel: string,
): DirectReadinessPresentation => {
  if (!health) {
    return {
      helperText: "Проверяем готовность direct chat path на backend.",
      isDirectSubmitBlocked: true,
    };
  }

  if (health.directStatus !== "UP") {
    return {
      helperText: health.directReasonMessage
        ?? "Direct chat path сейчас недоступен; backend не подтвердил реальный chat(...) probe.",
      isDirectSubmitBlocked: true,
    };
  }

  return {
    helperText: `Direct-режим уйдёт в unified \`/api/chat\` через ${selectedModel} без retrieval-контекста и использует только instruction stack, временную инструкцию и пользовательский запрос.`,
    isDirectSubmitBlocked: false,
  };
};
