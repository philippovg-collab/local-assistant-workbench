import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChatAuditPanel } from "./ChatAuditPanel";
import type {
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatRunTraceDetail,
  InstructionTraceEntry,
  KnowledgeScopeResolved,
  RetrievalTrace,
} from "../types";
import {
  EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  EMPTY_RETRIEVAL_TRACE,
} from "../utils/workbenchPresentation";

const traceDetails = vi.hoisted(() => new Map<string, ChatRunTraceDetail>());

vi.mock("@/hooks/useChatAuditComparison", async () => {
  const React = await vi.importActual<typeof import("react")>("react");

  return {
    useChatAuditComparison: ({
      runs,
      selectedRun,
      currentAuditRunId,
      onLoadRun,
    }: {
      runs: ChatAuditRunSummary[];
      selectedRun: ChatAuditRunDetail | null;
      currentAuditRunId?: string | null;
      onLoadRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
    }) => {
      const initialBaseRunId = currentAuditRunId ?? selectedRun?.id ?? runs[0]?.id ?? null;
      const initialCompareRunId = runs.find((run) => run.id !== initialBaseRunId)?.id ?? null;
      const [cachedRuns, setCachedRuns] = React.useState<Record<string, ChatAuditRunDetail>>(
        selectedRun ? { [selectedRun.id]: selectedRun } : {},
      );
      const [baseRunId, setBaseRunId] = React.useState<string | null>(initialBaseRunId);
      const [compareRunId, setCompareRunId] = React.useState<string | null>(initialCompareRunId);

      const loadRun = async (runId: string) => {
        if (cachedRuns[runId]) {
          return cachedRuns[runId];
        }
        const detail = await onLoadRun(runId);
        if (detail) {
          setCachedRuns((current) => ({ ...current, [detail.id]: detail }));
        }
        return detail;
      };

      const assignRun = async (slot: "base" | "compare", runId: string) => {
        const detail = await loadRun(runId);
        if (!detail) {
          return;
        }
        if (slot === "base") {
          setBaseRunId(runId);
        } else {
          setCompareRunId(runId);
        }
      };

      const baseRun = baseRunId ? cachedRuns[baseRunId] ?? (selectedRun?.id === baseRunId ? selectedRun : null) : null;
      const compareRun = compareRunId
        ? cachedRuns[compareRunId] ?? (selectedRun?.id === compareRunId ? selectedRun : null)
        : null;

      return {
        traceError: null,
        baseRunId,
        compareRunId,
        baseRun,
        compareRun,
        baseTrace: baseRunId ? traceDetails.get(baseRunId) ?? null : null,
        compareTrace: compareRunId ? traceDetails.get(compareRunId) ?? null : null,
        assignRun,
      };
    },
  };
});

const buildInstructionTrace = (title: string, revision: number): InstructionTraceEntry => ({
  instructionId: `${title}-${revision}`,
  title,
  category: "context",
  scopeLevel: "chat_scenario",
  scopeTargetId: null,
  revision,
  active: true,
  temporary: false,
  contentPreview: `${title} preview`,
});

const buildKnowledgeScopeResolved = (
  overrides: Partial<KnowledgeScopeResolved> = {},
): KnowledgeScopeResolved => ({
  ...EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  presets: [],
  documentClasses: [],
  tags: [],
  workspaceKey: null,
  uploadedTodayOnly: false,
  ...overrides,
});

const buildRetrievalTrace = (overrides: Partial<RetrievalTrace> = {}): RetrievalTrace => ({
  ...EMPTY_RETRIEVAL_TRACE,
  totalMaterials: 2,
  totalActiveMaterials: 2,
  totalReadyMaterials: 2,
  scopedMaterials: 2,
  scopedActiveMaterials: 2,
  scopedReadyMaterials: 2,
  semanticCandidates: 2,
  lexicalCandidates: 1,
  finalChunks: 1,
  supportVerdict: "sufficient",
  ...overrides,
});

const buildRunDetail = (overrides: Partial<ChatAuditRunDetail> = {}): ChatAuditRunDetail => ({
  id: "run-1",
  mode: "rag",
  model: "qwen2.5:7b",
  prompt: "Base prompt",
  answer: "Base answer",
  contextStatus: "ready",
  answerMode: "brief",
  createdAt: "2026-04-19T00:00:00Z",
  instructionTrace: [buildInstructionTrace("Context rule", 1)],
  knowledgeScopeResolved: buildKnowledgeScopeResolved({
    presets: [{ id: "preset-1", name: "Договоры", revision: 1 }],
    documentClasses: ["contracts"],
    tags: ["finance"],
    workspaceKey: "north-upgrade",
  }),
  retrievalTrace: buildRetrievalTrace(),
  sources: [
    {
      materialId: "material-1",
      chunkId: "material-1:0",
      title: "North contract",
      excerpt: "Тариф Премиум стоит 12000 тенге.",
      score: 100,
      confidence: 1,
      matchedTerms: ["тариф", "12000"],
      openSourceUrl: "/api/materials/material-1?chunkId=material-1%3A0&chunkIndex=0",
      chunkIndex: 0,
      page: 2,
      extractor: "direct-text",
      ocrUsed: false,
      semanticDistance: 0.1,
      lexicalScore: 1.2,
    },
  ],
  ...overrides,
});

const summaryOf = (detail: ChatAuditRunDetail): ChatAuditRunSummary => ({
  id: detail.id,
  mode: detail.mode,
  model: detail.model,
  answerMode: detail.answerMode,
  promptPreview: detail.prompt,
  answerPreview: detail.answer,
  createdAt: detail.createdAt,
});

const buildTraceDetail = (runId: string, status: "COMPLETED" | "FAILED" = "COMPLETED"): ChatRunTraceDetail => ({
  id: runId,
  mode: "rag",
  status,
  requestedModel: "qwen2.5:7b",
  resolvedModel: status === "COMPLETED" ? "qwen2.5:7b" : null,
  requestedAnswerMode: "brief",
  appliedAnswerMode: status === "COMPLETED" ? "brief" : null,
  contextStatus: status === "COMPLETED" ? "ready" : null,
  createdAt: "2026-04-19T00:00:00Z",
  completedAt: status === "COMPLETED" ? "2026-04-19T00:00:01Z" : null,
  failedAt: status === "FAILED" ? "2026-04-19T00:00:01Z" : null,
  latencyMsTotal: 1000,
  failureStage: status === "FAILED" ? "LLM" : null,
  failureCode: status === "FAILED" ? "chat_trace.execution_failed" : null,
  failureMessage: status === "FAILED" ? "model unavailable" : null,
  requestSnapshot: null,
  promptSnapshot: {
    baseSystemPrompt: "Base system",
    systemInstructionsText: null,
    safetyInstructionsText: null,
    contextInstructionsText: null,
    userInstructionsText: null,
    temporaryInstructionText: null,
    answerModeBlockText: null,
    groundingBlockText: null,
    resolvedSystemPrompt: "Base system",
    messages: [{ role: "system", content: "Base system" }, { role: "user", content: "Question" }],
    promptHash: `hash-${runId}`,
    instructionTrace: [],
    knowledgeScopeResolved: EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
    groundingRulesApplied: true,
  },
  retrievalSummary: {
    retrievalStatus: "DONE",
    trace: buildRetrievalTrace(),
    debug: null,
    lexicalProvider: "postgres",
    relevanceProfile: "hybrid-rerank-v1",
    embeddingModel: null,
    chunkProfile: null,
    queryHints: null,
    manualFilters: null,
    effectiveFilters: null,
    rolloutFlags: null,
    appliedCapabilities: null,
  },
  llmCalls: [
    {
      id: `${runId}-llm`,
      provider: "ollama",
      model: "qwen2.5:7b",
      requestMessages: [{ role: "user", content: "Question" }],
      rawResponseText: "{\"message\":{\"content\":\"Answer\"}}",
      parsedAnswerText: "Answer",
      promptTokens: 1,
      completionTokens: 1,
      totalTokens: 2,
      latencyMs: 500,
      retryCount: 0,
      timeoutSeconds: null,
      finishReason: status === "COMPLETED" ? "stop" : null,
      errorCode: status === "FAILED" ? "chat_trace.execution_failed" : null,
      errorMessage: status === "FAILED" ? "model unavailable" : null,
      createdAt: "2026-04-19T00:00:01Z",
    },
  ],
  output: status === "COMPLETED" ? {
    rawModelAnswer: "Answer",
    finalUserAnswer: "Answer",
    sources: [],
    postprocess: { answerMode: "brief" },
    abstained: false,
    strictSourcesBlockedAnswer: false,
  } : null,
  events: [],
});

describe("ChatAuditPanel", () => {
  afterEach(() => {
    traceDetails.clear();
    cleanup();
    vi.clearAllMocks();
  });

  it("renders the empty state when no runs exist", () => {
    render(
      <ChatAuditPanel
        runs={[]}
        selectedRun={null}
        error={null}
        onLoadRun={vi.fn()}
        currentInstructionTrace={[]}
        currentKnowledgeScopeResolved={EMPTY_KNOWLEDGE_SCOPE_RESOLVED}
        currentRetrievalTrace={EMPTY_RETRIEVAL_TRACE}
      />,
    );

    expect(screen.getByText("История запусков пока пуста")).toBeTruthy();
  });

  it("loads compare runs, switches the base run, and renders diff plus inspectors", async () => {
    const user = userEvent.setup();
    const selectedRun = buildRunDetail();
    const compareRun = buildRunDetail({
      id: "run-2",
      model: "deepseek-r1:14b",
      prompt: "Compare prompt",
      answer: "Compare answer",
      answerMode: "strict_sources_only",
      createdAt: "2026-04-19T00:05:00Z",
      instructionTrace: [buildInstructionTrace("Strict rule", 2)],
      knowledgeScopeResolved: buildKnowledgeScopeResolved({
        presets: [{ id: "preset-1", name: "Договоры", revision: 2 }],
        documentClasses: ["contracts"],
        tags: ["finance", "priority"],
        workspaceKey: "north-upgrade",
        uploadedTodayOnly: true,
      }),
      retrievalTrace: buildRetrievalTrace({
        semanticCandidates: 4,
        lexicalCandidates: 3,
        finalChunks: 2,
        supportVerdict: "weak",
      }),
      sources: [
        {
          materialId: "material-1",
          chunkId: "material-1:1",
          title: "North contract",
          excerpt: "Дополнительный chunk про приоритетную поддержку.",
          score: 90,
          confidence: 0.9,
          matchedTerms: ["поддержка"],
          openSourceUrl: "/api/materials/material-1?chunkId=material-1%3A1&chunkIndex=1",
          chunkIndex: 1,
          page: 3,
          extractor: "pdfbox",
          ocrUsed: false,
          semanticDistance: 0.2,
          lexicalScore: 0.8,
        },
      ],
    });
    const newBaseRun = buildRunDetail({
      id: "run-3",
      model: "qwen2.5:32b",
      prompt: "New base prompt",
      answer: "New base answer",
      answerMode: "documents_only",
      createdAt: "2026-04-19T00:10:00Z",
      instructionTrace: [buildInstructionTrace("Documents rule", 4)],
      knowledgeScopeResolved: buildKnowledgeScopeResolved({
        presets: [{ id: "preset-2", name: "Регламенты", revision: 3 }],
        documentClasses: ["regulations"],
        tags: ["policy"],
        workspaceKey: "ops-workspace",
      }),
      retrievalTrace: buildRetrievalTrace({
        semanticCandidates: 1,
        lexicalCandidates: 1,
        finalChunks: 1,
        supportVerdict: "sufficient",
      }),
      sources: [
        {
          materialId: "material-2",
          chunkId: "material-2:0",
          title: "Operations policy",
          excerpt: "Регламент подтверждает SLA.",
          score: 88,
          confidence: 0.88,
          matchedTerms: ["sla"],
          openSourceUrl: "/api/materials/material-2?chunkId=material-2%3A0&chunkIndex=0",
          chunkIndex: 0,
          page: 1,
          extractor: "tika",
          ocrUsed: false,
          semanticDistance: 0.3,
          lexicalScore: 0.7,
        },
      ],
    });

    const runs = [summaryOf(selectedRun), summaryOf(compareRun), summaryOf(newBaseRun)];
    const onLoadRun = vi.fn(async (runId: string) => {
      if (runId === compareRun.id) {
        return compareRun;
      }
      if (runId === newBaseRun.id) {
        return newBaseRun;
      }
      return null;
    });
    traceDetails.set(selectedRun.id, buildTraceDetail(selectedRun.id));
    traceDetails.set(compareRun.id, buildTraceDetail(compareRun.id, "FAILED"));
    traceDetails.set(newBaseRun.id, buildTraceDetail(newBaseRun.id));

    render(
      <ChatAuditPanel
        runs={runs}
        selectedRun={selectedRun}
        error="История частично недоступна."
        onLoadRun={onLoadRun}
        currentAuditRunId={selectedRun.id}
        currentInstructionTrace={selectedRun.instructionTrace}
        currentKnowledgeScopeResolved={selectedRun.knowledgeScopeResolved}
        currentRetrievalTrace={selectedRun.retrievalTrace}
      />,
    );

    expect(screen.getByText("История частично недоступна.")).toBeTruthy();

    const compareCard = screen.getByText(compareRun.prompt).closest("article");
    const newBaseCard = screen.getByText(newBaseRun.prompt).closest("article");

    if (!compareCard || !newBaseCard) {
      throw new Error("Run cards not rendered");
    }

    await user.click(within(compareCard).getByRole("button", { name: "Сравнить" }));
    await user.click(within(newBaseCard).getByRole("button", { name: "База" }));

    await waitFor(() => {
      expect(onLoadRun).toHaveBeenCalledWith(compareRun.id);
      expect(onLoadRun).toHaveBeenCalledWith(newBaseRun.id);
    });

    await waitFor(() => {
      expect(screen.getByText("Model: qwen2.5:32b vs deepseek-r1:14b")).toBeTruthy();
    });

    expect(screen.getByText("Preset revisions: +1 / -1")).toBeTruthy();
    expect(screen.getByText("Chunk ids: +1 / -1")).toBeTruthy();
    expect(screen.getByText("Base run inspector")).toBeTruthy();
    expect(screen.getByText("Compare run inspector")).toBeTruthy();
    expect(screen.getByText("New base answer")).toBeTruthy();
    expect(screen.getByText("Compare answer")).toBeTruthy();
    expect(screen.getByText("Operations policy")).toBeTruthy();
    expect(screen.getByText("North contract")).toBeTruthy();
    await waitFor(() => {
      expect(screen.getAllByText("Trace foundation").length).toBeGreaterThan(0);
    });
    expect(screen.getByText(/hash-run-3/)).toBeTruthy();
    expect(screen.getByText(/LLM · chat_trace.execution_failed · model unavailable/)).toBeTruthy();
  });
});
