import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StatusSummary } from "./StatusSummary";
import type { HealthResponse } from "@/types";
import type { RagReadinessPresentation } from "@/utils/readiness";

afterEach(() => {
  cleanup();
});

const ragPresentation: RagReadinessPresentation = {
  headline: "1/1",
  overviewMessage: "RAG готов.",
  materialsMessage: null,
  chatHelperText: "Можно отправлять запрос.",
  badgeLabel: "1 active / 1 total",
  isRagSubmitBlocked: false,
  statusDotClass: "online",
  materialsMessageClassName: "helper",
};

const health: HealthResponse = {
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  directStatus: "UP",
  ragStatus: "UP",
  llmStatus: "UP",
  embeddingStatus: "UP",
  knowledgeStatus: "READY",
  materialCount: 4,
  activeMaterialCount: 4,
  historicalMaterialCount: 0,
  readyMaterialCount: 4,
  indexingPendingCount: 0,
  indexingInProgressCount: 0,
  indexingFailedCount: 0,
  readiness: {
    "context-layer": {
      status: "UP",
      observedAt: "2026-04-16T10:00:00Z",
    },
  },
  qualityLayer: {
    flags: {
      metadataV1: true,
      structuredV1: true,
      metadataFiltersV1: true,
      searchApiV1: true,
      rerankerV1: false,
      queryHintsV1: false,
    },
    metadataCoverage: {
      activeTotal: 4,
      activeWithEffectiveMetadata: 3,
      ratio: 0.75,
      documentType: {
        covered: 3,
        ratio: 0.75,
      },
      workspace: {
        covered: 2,
        ratio: 0.5,
      },
      documentStatus: {
        covered: 4,
        ratio: 1,
      },
    },
    activeBackfillCoverage: {
      activeTotal: 4,
      structuredProfileActive: 1,
      ratio: 0.25,
      pendingBackfill: 3,
      partialReadyActive: 1,
    },
    retrievalWindow: {
      sampleSize: 8,
      noContextRate: 0.125,
      hitDistributionByChunkType: {},
      rerankerDelta: {
        top1ChangedCount: 1,
        top1ImprovedCount: 1,
        appendixDemotions: 0,
        highTrustPromotions: 1,
      },
    },
  },
};

describe("StatusSummary", () => {
  it("shows quality flags and active backfill pending/partial coverage from existing health DTO", () => {
    render(
      <StatusSummary
        health={health}
        healthError={null}
        models={[]}
        modelsError={null}
        ragPresentation={ragPresentation}
        instructionsCount={2}
      />,
    );

    expect(screen.getByText(/metadata=on/)).toBeTruthy();
    expect(screen.getByText(/reranker=off/)).toBeTruthy();
    expect(screen.getByText(/1\/4 active на structured-v1/)).toBeTruthy();
    expect(screen.getByText(/pending=3/)).toBeTruthy();
    expect(screen.getByText(/partial-ready=1/)).toBeTruthy();
    expect(screen.getByText("Context layer")).toBeTruthy();
    expect(screen.getAllByText("UP").length).toBeGreaterThan(0);
  });
});
