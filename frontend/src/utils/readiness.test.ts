import { describe, expect, it } from "vitest";
import type { HealthResponse, MaterialSummary } from "../types";
import { buildRagReadinessPresentation, deriveRagReadiness } from "./readiness";

const buildMaterial = (overrides: Partial<MaterialSummary> = {}): MaterialSummary => ({
  id: "material-1",
  title: "Material",
  sourceType: "text",
  originalFileName: null,
  status: "READY",
  versionState: "ACTIVE",
  statusReasonCode: null,
  statusReasonMessage: null,
  createdAt: "2026-04-16T10:00:00Z",
  contentLength: 128,
  preview: "Preview",
  ...overrides,
});

const healthyRagRuntime: HealthResponse = {
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  directStatus: "UP",
  ragStatus: "UP",
};

describe("deriveRagReadiness", () => {
  it("marks an empty catalog as empty", () => {
    const readiness = deriveRagReadiness(healthyRagRuntime, []);

    expect(readiness.state).toBe("empty");
    expect(readiness.activeMaterialsCount).toBe(0);
    expect(readiness.readyMaterialsCount).toBe(0);
    expect(readiness.isRagReady).toBe(false);
  });

  it("marks a catalog with only superseded versions as historical-only", () => {
    const readiness = deriveRagReadiness(healthyRagRuntime, [
      buildMaterial({ versionState: "SUPERSEDED" }),
    ]);

    expect(readiness.state).toBe("historical-only");
    expect(readiness.activeMaterialsCount).toBe(0);
    expect(readiness.historicalMaterialsCount).toBe(1);
    expect(readiness.hasOnlyHistoricalMaterials).toBe(true);
  });

  it("marks active pending materials as indexing", () => {
    const readiness = deriveRagReadiness(healthyRagRuntime, [
      buildMaterial({ status: "IN_PROGRESS" }),
    ]);

    expect(readiness.state).toBe("indexing");
    expect(readiness.activeMaterialsCount).toBe(1);
    expect(readiness.readyMaterialsCount).toBe(0);
    expect(readiness.hasActiveIndexing).toBe(true);
  });

  it("marks active ready materials as ready", () => {
    const readiness = deriveRagReadiness(healthyRagRuntime, [buildMaterial()]);

    expect(readiness.state).toBe("ready");
    expect(readiness.activeMaterialsCount).toBe(1);
    expect(readiness.readyMaterialsCount).toBe(1);
    expect(readiness.isRagReady).toBe(true);
  });

  it("marks active materials as degraded when runtime is down", () => {
    const readiness = deriveRagReadiness(
      {
        ...healthyRagRuntime,
        ragStatus: "DOWN",
        embeddingReasonMessage: "Embedding runtime is unavailable.",
      },
      [buildMaterial()],
    );

    expect(readiness.state).toBe("degraded");
    expect(readiness.isRagReady).toBe(false);
  });
});

describe("buildRagReadinessPresentation", () => {
  it("builds the empty-state presentation contract", () => {
    const presentation = buildRagReadinessPresentation({
      health: healthyRagRuntime,
      ragReadiness: deriveRagReadiness(healthyRagRuntime, []),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("Пустая база");
    expect(presentation.overviewMessage).toBe("В knowledge base пока нет материалов.");
    expect(presentation.materialsMessage).toBe(
      "В каталоге пока нет материалов. Добавь хотя бы один активный материал, чтобы RAG мог работать по контексту.",
    );
    expect(presentation.chatHelperText).toBe("Сначала добавь материалы, иначе RAG-режим не на чем grounded.");
    expect(presentation.badgeLabel).toBe("0 active / 0 total");
    expect(presentation.isRagSubmitBlocked).toBe(true);
    expect(presentation.statusDotClass).toBe("idle");
  });

  it("builds the historical-only presentation contract", () => {
    const presentation = buildRagReadinessPresentation({
      health: healthyRagRuntime,
      ragReadiness: deriveRagReadiness(healthyRagRuntime, [
        buildMaterial({ versionState: "SUPERSEDED" }),
      ]),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("Только история версий");
    expect(presentation.overviewMessage).toBe(
      "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
    );
    expect(presentation.materialsMessage).toBe(
      "В каталоге сейчас только исторические версии. Они видны ниже для аудита, но исключены из retrieval и readiness.",
    );
    expect(presentation.chatHelperText).toContain("Добавь новую активную версию");
    expect(presentation.badgeLabel).toBe("0 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(true);
    expect(presentation.statusDotClass).toBe("idle");
    expect(presentation.materialsMessageClassName).toBe("warning-state");
  });

  it("builds the indexing presentation contract", () => {
    const presentation = buildRagReadinessPresentation({
      health: healthyRagRuntime,
      ragReadiness: deriveRagReadiness(healthyRagRuntime, [
        buildMaterial({ status: "IN_PROGRESS" }),
      ]),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("0/1");
    expect(presentation.overviewMessage).toBe(
      "Активная версия уже принята, а индекс ещё догоняет её до READY или PARTIAL_READY.",
    );
    expect(presentation.materialsMessage).toBe(
      "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
    );
    expect(presentation.chatHelperText).toContain("индекс ещё собирается");
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(false);
    expect(presentation.statusDotClass).toBe("idle");
  });

  it("builds the ready presentation contract", () => {
    const presentation = buildRagReadinessPresentation({
      health: healthyRagRuntime,
      ragReadiness: deriveRagReadiness(healthyRagRuntime, [buildMaterial()]),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("1/1");
    expect(presentation.overviewMessage).toBe(
      "Активные материалы и readiness считаются по одной модели состояния.",
    );
    expect(presentation.materialsMessage).toBe(
      "RAG использует только активные READY и PARTIAL_READY версии, а historical остаются в каталоге для аудита.",
    );
    expect(presentation.chatHelperText).toBe(
      "Вопрос уйдёт в qwen2.5:7b с локально подобранным контекстом из активных материалов.",
    );
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(false);
    expect(presentation.statusDotClass).toBe("online");
  });

  it("builds the degraded presentation contract with a single prioritized reason", () => {
    const degradedHealth: HealthResponse = {
      ...healthyRagRuntime,
      ragStatus: "DOWN",
      embeddingReasonMessage: "Embedding runtime is unavailable.",
    };

    const presentation = buildRagReadinessPresentation({
      health: degradedHealth,
      ragReadiness: deriveRagReadiness(degradedHealth, [buildMaterial()]),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("Readiness degraded");
    expect(presentation.overviewMessage).toBe(
      "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
    );
    expect(presentation.materialsMessage).toBe(
      "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
    );
    expect(presentation.chatHelperText).toBe(
      "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
    );
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(false);
    expect(presentation.statusDotClass).toBe("warn");
    expect(presentation.materialsMessageClassName).toBe("warning-state");
  });
});
