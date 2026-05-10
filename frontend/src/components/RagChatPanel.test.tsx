import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RagChatPanel } from "./RagChatPanel";
import { buildChatExecutionResponse } from "../testBuilders";
import type { MaterialDetail } from "../types";
import type { MaterialSourceDialogState } from "../hooks/useMaterialSourceDialog";
import { parseSourceTarget } from "./ragChatPresentation";
import {
  EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  EMPTY_RETRIEVAL_TRACE,
} from "../utils/workbenchPresentation";

const closedSourceDialog = (): MaterialSourceDialogState => ({
  highlightedChunkRef: { current: null },
  openedMaterial: null,
  isLoadingMaterial: false,
  materialError: null,
  openedSourceTarget: null,
  openSource: vi.fn(async () => undefined),
  closeSourceDialog: vi.fn(),
});

const renderPanel = (
  overrides: Partial<Parameters<typeof RagChatPanel>[0]> = {},
) => {
  const response = overrides.response ?? buildChatExecutionResponse({
    mode: "rag",
    model: "qwen2.5:7b",
    prompt: "Сколько стоит тариф Премиум?",
    answer: "Тариф Премиум стоит 12000 тенге.",
    answerModeApplied: "documents_only",
    knowledgeScopeResolved: EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
    retrievalTrace: {
      ...EMPTY_RETRIEVAL_TRACE,
      totalMaterials: 1,
      totalActiveMaterials: 1,
      totalReadyMaterials: 1,
      scopedMaterials: 1,
      scopedActiveMaterials: 1,
      scopedReadyMaterials: 1,
      semanticCandidates: 1,
      lexicalCandidates: 1,
      finalChunks: 1,
      supportVerdict: "sufficient",
    },
    retrievalDebug: {
      queryHints: {
        documentNumber: "KZ-2026-0415-ENERGY",
        project: "North Upgrade",
        projectKeys: [],
      },
      manualFilters: {
        documentNumber: null,
        documentDateFrom: null,
        documentDateTo: null,
        department: null,
        project: "North Upgrade",
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
      },
      effectiveFilters: {
        documentNumber: "KZ-2026-0415-ENERGY",
        documentDateFrom: null,
        documentDateTo: null,
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
      },
      semanticCandidateCount: 2,
      lexicalCandidateCount: 2,
      rerankCandidateCount: 2,
      finalChunkCount: 1,
      supportVerdict: "sufficient",
      relevanceProfile: "hybrid-rerank-v1",
      activeRolloutFlags: {
        metadataV1: true,
        structuredV1: true,
        metadataFiltersV1: true,
        searchApiV1: true,
        rerankerV1: true,
        queryHintsV1: true,
      },
      appliedCapabilities: ["metadata-filters-v1", "query-hints-v1", "reranker-v1"],
    },
    sources: [
      {
        materialId: "material-1",
        chunkId: "material-1:1",
        title: "Pricing FAQ",
        excerpt: "Тариф Премиум стоит 12000 тенге.",
        score: 100,
        confidence: 1,
        matchedTerms: ["тариф", "12000"],
        openSourceUrl: "/api/materials/material-1?chunkId=material-1%3A1&chunkIndex=1&page=2",
        chunkIndex: 1,
        page: 2,
        extractor: "pdfbox",
        ocrUsed: false,
        chunkType: "TABLE",
        metadata: {
          documentType: "CONTRACT",
          documentStatus: "ACTIVE",
          knowledgeDocumentClass: "contracts",
          documentDate: "2026-04-15",
          documentNumber: "KZ-2026-0415-ENERGY",
          author: "Dana Sarsen",
          department: "Grid operations",
          versionLabel: "v2",
          language: "ru",
          tags: ["dispatch"],
          sourceTrust: "HIGH",
          project: "North Upgrade",
          counterparty: "GridBuild LLP",
          businessStatus: "APPROVED",
          periodStart: null,
          periodEnd: null,
          provenance: {
            fieldOrigins: {},
            fieldConfidence: {},
          },
        },
        semanticDistance: 0.2,
        lexicalScore: 1.1,
        scoreBreakdown: {
          baseRrf: 61,
          semanticRankBonus: 6,
          lexicalRankBonus: 8,
          identifierBonus: 20,
          headingBonus: 5,
          metadataBonus: 8,
          sourceTrustBoost: 6,
          appendixPenalty: 0,
          boilerplatePenalty: 0,
          lowConfidencePenalty: 0,
          finalScore: 100,
        },
      },
    ],
  });

  return render(
    <RagChatPanel
      models={[{ name: "qwen2.5:7b" }]}
      modelsError={null}
      selectedModel="qwen2.5:7b"
      onModelChange={vi.fn()}
      answerMode="documents_only"
      onAnswerModeChange={vi.fn()}
      prompt="Сколько стоит тариф Премиум?"
      onPromptChange={vi.fn()}
      temporaryInstruction=""
      onTemporaryInstructionChange={vi.fn()}
      instructions={[]}
      selectedInstructionIds={[]}
      onToggleInstruction={vi.fn()}
      knowledgePresets={[]}
      knowledgeFacets={[]}
      knowledgeScope={{
        presetIds: [],
        facetIds: [],
        documentClasses: [],
        documentTypes: [],
        documentStatuses: [],
        projectKeys: [],
        documentNumber: null,
        languageCodes: [],
        tags: [],
        workspaceKey: null,
        periodStartFrom: null,
        periodStartTo: null,
        periodEndFrom: null,
        periodEndTo: null,
        uploadedTodayOnly: false,
      }}
      onKnowledgeScopeChange={vi.fn()}
      retrievalFilters={{
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
        documentTypes: [],
        documentStatuses: [],
        projectKeys: [],
        languageCodes: [],
        periodStartFrom: null,
        periodStartTo: null,
        periodEndFrom: null,
        periodEndTo: null,
      }}
      effectiveRetrievalFilters={{
        documentNumber: "KZ-2026-0415-ENERGY",
        documentDateFrom: null,
        documentDateTo: null,
        department: null,
        project: "North Upgrade",
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
      }}
      queryHints={{
        documentNumber: "KZ-2026-0415-ENERGY",
        project: "North Upgrade",
        projectKeys: [],
      }}
      metadataFiltersEnabled={true}
      queryHintsEnabled={true}
      hintOwnedFields={["documentNumber", "project"]}
      manualOwnedFields={[]}
      dismissedHintKeys={[]}
      onRetrievalFilterChange={vi.fn()}
      onClearRetrievalFilter={vi.fn()}
      onDismissHint={vi.fn()}
      onResetDismissedHints={vi.fn()}
      helperText="Поиск ограничен текущим corpus scope."
      isBlocked={false}
      isSubmitting={false}
      error={null}
      response={response}
      chatRuns={[]}
      selectedChatRun={null}
      chatRunsError={null}
      sourceDialog={closedSourceDialog()}
      onLoadChatRun={vi.fn(async () => null)}
      onSubmit={vi.fn(async () => undefined)}
      {...overrides}
    />,
  );
};

describe("RagChatPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("requests the source viewer for the targeted chunk from openSourceUrl", async () => {
    const user = userEvent.setup();
    const sourceDialog = closedSourceDialog();

    renderPanel({ sourceDialog });

    await user.click(screen.getByRole("button", { name: "Открыть источник" }));

    await waitFor(() => {
      expect(sourceDialog.openSource).toHaveBeenCalledWith(
        "material-1",
        "/api/materials/material-1?chunkId=material-1%3A1&chunkIndex=1&page=2",
      );
    });
  });

  it("renders the source dialog from hook state", async () => {
    renderPanel({
      sourceDialog: {
        ...closedSourceDialog(),
        openedMaterial: materialDetailFixture(),
        openedSourceTarget: parseSourceTarget(
          "material-1",
          "/api/materials/material-1?chunkId=material-1%3A1&chunkIndex=1&page=2",
        ),
      },
    });

    expect(await screen.findByText("retrieval target")).toBeTruthy();
    expect(screen.getByText("Jump target: 1 · page 2")).toBeTruthy();
    expect(screen.getByText("Второй chunk с тарифом 12000 тенге.")).toBeTruthy();
  });

  it("shows an error when the source material cannot be loaded", async () => {
    renderPanel({
      sourceDialog: {
        ...closedSourceDialog(),
        materialError: "Не удалось загрузить источник. Проверь доступность backend и попробуй ещё раз.",
        openedSourceTarget: parseSourceTarget("material-1", "/api/materials/material-1"),
      },
    });

    expect(await screen.findByText(/Не удалось загрузить источник/i)).toBeTruthy();
  });

  it("renders hint chips and debug breakdown without the legacy filter drawer", async () => {
    renderPanel();

    expect(screen.getAllByText(/Номер документа: KZ-2026-0415-ENERGY/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Проект: North Upgrade/i).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /фильтры поиска/i })).toBeNull();
    expect(screen.getByText(/Retrieval debug/i)).toBeTruthy();

    const scoreSummary = screen.getByText(/Score breakdown/i).closest("summary");
    expect(scoreSummary).toBeTruthy();
    fireEvent.click(scoreSummary!);
    expect(screen.getByText(/identifier bonus: 20/i)).toBeTruthy();
    expect(screen.queryByText(/sourceTrust:/i)).toBeNull();
  });

});

const materialDetailFixture = (): MaterialDetail => ({
  id: "material-1",
  title: "Pricing FAQ",
  sourceType: "file",
  originalFileName: "pricing.pdf",
  mediaType: "application/pdf",
  content: "Первый chunk.\n\nВторой chunk с тарифом 12000 тенге.",
  status: "READY",
  versionState: "ACTIVE",
  createdAt: "2026-04-19T00:00:00Z",
  updatedAt: "2026-04-19T00:00:00Z",
  metadata: undefined,
  chunks: [
    {
      chunkId: "material-1:0",
      chunkIndex: 0,
      text: "Первый chunk.",
      page: 1,
      extractor: "pdfbox",
      ocrUsed: false,
    },
    {
      chunkId: "material-1:1",
      chunkIndex: 1,
      text: "Второй chunk с тарифом 12000 тенге.",
      page: 2,
      extractor: "pdfbox",
      ocrUsed: false,
    },
  ],
});
