import { describe, expect, it } from "vitest";
import type { HealthResponse } from "../types";
import {
  buildDirectReadinessPresentation,
  buildRagReadinessPresentation,
  deriveRagReadiness,
} from "./readiness";

const buildHealth = (overrides: Partial<HealthResponse> = {}): HealthResponse => ({
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  directStatus: "UP",
  ragStatus: "UP",
  llmStatus: "UP",
  embeddingStatus: "UP",
  knowledgeStatus: "READY",
  materialCount: 1,
  activeMaterialCount: 1,
  historicalMaterialCount: 0,
  readyMaterialCount: 1,
  indexingPendingCount: 0,
  indexingInProgressCount: 0,
  ...overrides,
});

describe("deriveRagReadiness", () => {
  it("marks an empty catalog as empty from backend health payload", () => {
    const readiness = deriveRagReadiness(buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "EMPTY",
      knowledgeReasonMessage: "В knowledge base пока нет материалов.",
      materialCount: 0,
      activeMaterialCount: 0,
      historicalMaterialCount: 0,
      readyMaterialCount: 0,
    }));

    expect(readiness.state).toBe("empty");
    expect(readiness.activeMaterialsCount).toBe(0);
    expect(readiness.readyMaterialsCount).toBe(0);
    expect(readiness.isRagReady).toBe(false);
  });

  it("marks a catalog with only superseded versions as historical-only", () => {
    const readiness = deriveRagReadiness(buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "HISTORICAL_ONLY",
      materialCount: 1,
      activeMaterialCount: 0,
      historicalMaterialCount: 1,
      readyMaterialCount: 0,
    }));

    expect(readiness.state).toBe("historical-only");
    expect(readiness.activeMaterialsCount).toBe(0);
    expect(readiness.historicalMaterialsCount).toBe(1);
    expect(readiness.hasOnlyHistoricalMaterials).toBe(true);
  });

  it("marks active pending materials as indexing", () => {
    const readiness = deriveRagReadiness(buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "INDEXING",
      readyMaterialCount: 0,
      indexingPendingCount: 1,
      indexingInProgressCount: 1,
    }));

    expect(readiness.state).toBe("indexing");
    expect(readiness.activeMaterialsCount).toBe(1);
    expect(readiness.readyMaterialsCount).toBe(0);
    expect(readiness.hasActiveIndexing).toBe(true);
  });

  it("marks active ready materials as ready", () => {
    const readiness = deriveRagReadiness(buildHealth());

    expect(readiness.state).toBe("ready");
    expect(readiness.activeMaterialsCount).toBe(1);
    expect(readiness.readyMaterialsCount).toBe(1);
    expect(readiness.isRagReady).toBe(true);
  });

  it("marks active materials as degraded when runtime or knowledge is down", () => {
    const readiness = deriveRagReadiness(buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "DEGRADED",
      knowledgeReasonMessage: "Активные материалы есть, но ни один не проходит retrieval-ready условия.",
    }));

    expect(readiness.state).toBe("degraded");
    expect(readiness.isRagReady).toBe(false);
  });

  it("treats missing knowledgeStatus as backend contract drift instead of inferring readiness", () => {
    const readiness = deriveRagReadiness(buildHealth({
      ragStatus: "UP",
      knowledgeStatus: undefined,
    }));

    expect(readiness.state).toBe("degraded");
    expect(readiness.hasContractDrift).toBe(true);
    expect(readiness.isRagReady).toBe(false);
  });
});

describe("buildRagReadinessPresentation", () => {
  it("builds the empty-state presentation contract", () => {
    const health = buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "EMPTY",
      knowledgeReasonMessage: "В knowledge base пока нет материалов.",
      materialCount: 0,
      activeMaterialCount: 0,
      historicalMaterialCount: 0,
      readyMaterialCount: 0,
    });
    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
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
    const health = buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "HISTORICAL_ONLY",
      knowledgeReasonMessage: "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
      materialCount: 1,
      activeMaterialCount: 0,
      historicalMaterialCount: 1,
      readyMaterialCount: 0,
    });
    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
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

  it("builds the indexing presentation contract and blocks submit until backend marks RAG ready", () => {
    const health = buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "INDEXING",
      knowledgeReasonMessage: "Активная версия уже принята, но индекс ещё догоняет её до READY или PARTIAL_READY.",
      readyMaterialCount: 0,
      indexingPendingCount: 1,
      indexingInProgressCount: 1,
    });
    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("0/1");
    expect(presentation.overviewMessage).toBe(
      "Активная версия уже принята, но индекс ещё догоняет её до READY или PARTIAL_READY.",
    );
    expect(presentation.materialsMessage).toBe(
      "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
    );
    expect(presentation.chatHelperText).toContain("индекс ещё собирается");
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(true);
    expect(presentation.statusDotClass).toBe("idle");
  });

  it("builds the ready presentation contract", () => {
    const health = buildHealth();
    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("1/1");
    expect(presentation.overviewMessage).toBe(
      "Readiness считается по active lineage, READY/PARTIAL_READY, действующему статусу документа и валидному периоду.",
    );
    expect(presentation.materialsMessage).toBe(
      "RAG использует только active lineage версии со статусом READY/PARTIAL_READY, documentStatus=ACTIVE и валидным периодом; historical остаются в каталоге для аудита.",
    );
    expect(presentation.chatHelperText).toBe(
      "Вопрос уйдёт в qwen2.5:7b с локально подобранным контекстом из активных материалов.",
    );
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(false);
    expect(presentation.statusDotClass).toBe("online");
  });

  it("builds the degraded presentation contract with a single prioritized reason", () => {
    const health = buildHealth({
      ragStatus: "DOWN",
      knowledgeStatus: "DEGRADED",
      directStatus: "DOWN",
      directReasonMessage: "Direct chat probe failed: timeout",
      ragDegradedReasonMessage: "Direct chat probe failed: timeout",
    });

    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.headline).toBe("Readiness degraded");
    expect(presentation.overviewMessage).toBe(
      "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
    );
    expect(presentation.materialsMessage).toBe(
      "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
    );
    expect(presentation.chatHelperText).toBe(
      "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
    );
    expect(presentation.badgeLabel).toBe("1 active / 1 total");
    expect(presentation.isRagSubmitBlocked).toBe(true);
    expect(presentation.statusDotClass).toBe("warn");
    expect(presentation.materialsMessageClassName).toBe("warning-state");
  });

  it("blocks RAG submit and shows an explicit contract-drift message when knowledgeStatus is missing", () => {
    const health = buildHealth({
      ragStatus: "UP",
      knowledgeStatus: undefined,
    });

    const presentation = buildRagReadinessPresentation({
      health,
      ragReadiness: deriveRagReadiness(health),
      isLoadingMaterials: false,
      materialsError: null,
      selectedModel: "qwen2.5:7b",
    });

    expect(presentation.isRagSubmitBlocked).toBe(true);
    expect(presentation.overviewMessage).toBe(
      "Backend health contract неполный: knowledgeStatus отсутствует, поэтому RAG readiness нельзя подтвердить.",
    );
    expect(presentation.materialsMessage).toBe(
      "Backend health contract неполный: knowledgeStatus отсутствует, поэтому RAG readiness нельзя подтвердить.",
    );
    expect(presentation.chatHelperText).toBe(
      "Backend health contract неполный: knowledgeStatus отсутствует, поэтому RAG readiness нельзя подтвердить.",
    );
  });
});

describe("buildDirectReadinessPresentation", () => {
  it("uses direct reason instead of llm catalog reason when chat path is broken", () => {
    const presentation = buildDirectReadinessPresentation(
      buildHealth({
        directStatus: "DOWN",
        directReasonMessage: "Direct chat probe failed: timeout",
        llmStatus: "UP",
        llmReasonMessage: "This should not be shown",
      }),
      "qwen2.5:7b",
    );

    expect(presentation.isDirectSubmitBlocked).toBe(true);
    expect(presentation.helperText).toBe("Direct chat probe failed: timeout");
  });

  it("keeps direct submit enabled only when backend confirms direct path is healthy", () => {
    const presentation = buildDirectReadinessPresentation(buildHealth(), "qwen2.5:7b");

    expect(presentation.isDirectSubmitBlocked).toBe(false);
    expect(presentation.helperText).toContain("qwen2.5:7b");
  });
});
