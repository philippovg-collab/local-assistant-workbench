import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConversationThreadPanel } from "./ConversationThreadPanel";
import { apiClient } from "@/api/client";
import type { ChatRunContextDetail, ConversationRunDetail, ConversationSummary } from "@/types";

vi.mock("@/api/client", () => ({
  apiClient: {
    fetchChatRunContext: vi.fn(),
  },
  isApiClientError: (error: unknown) => Boolean(error && typeof error === "object" && "status" in error),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ConversationThreadPanel", () => {
  it("renders context summary for the inspected conversation run", async () => {
    vi.mocked(apiClient.fetchChatRunContext).mockResolvedValue(snapshot());

    render(panel());

    expect(await screen.findByText("История")).toBeTruthy();
    expect(screen.getByText("Сброшено")).toBeTruthy();
    expect(screen.getByText("Токены")).toBeTruthy();
    expect(screen.getByText("47/1000")).toBeTruthy();
    expect(screen.getByText("original")).toBeTruthy();
    expect(screen.getByText("контекст")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "State" }));
    expect(screen.getByText("value · model")).toBeTruthy();
    expect(screen.getByText("sticky-model")).toBeTruthy();
  });

  it("handles a missing context snapshot without hiding the run", async () => {
    vi.mocked(apiClient.fetchChatRunContext).mockRejectedValue(Object.assign(new Error("not found"), { status: 404 }));

    render(panel());

    expect(await screen.findByText("Context snapshot уже недоступен.")).toBeTruthy();
    expect(screen.getByText(/#2 сделай короче/)).toBeTruthy();
  });
});

function panel() {
  return (
    <ConversationThreadPanel
      conversations={[conversation()]}
      currentRunId="run-2"
      runs={[run()]}
      selectedConversationId="conversation-1"
      onArchiveConversation={async () => undefined}
      onCreateConversation={async () => undefined}
      onRefreshRuns={async () => undefined}
      onSelectConversation={() => undefined}
    />
  );
}

function conversation(): ConversationSummary {
  return {
    id: "conversation-1",
    title: "Проверка",
    mode: "direct",
    status: "ACTIVE",
    createdAt: "2026-05-10T00:00:00Z",
    updatedAt: "2026-05-10T00:00:00Z",
    turnCount: 2,
  };
}

function run(): ConversationRunDetail {
  return {
    conversationId: "conversation-1",
    runId: "run-2",
    turnNo: 2,
    userPrompt: "сделай короче",
    contextAssemblyId: "context-1",
    createdAt: "2026-05-10T00:00:00Z",
    status: "COMPLETED",
    statusUrl: "/api/chat-runs/run-2/status",
    traceUrl: "/api/chat-runs/run-2/trace",
    resultUrl: "/api/chat-runs/run-2/result",
  };
}

function snapshot(): ChatRunContextDetail {
  return {
    status: "AVAILABLE",
    runId: "run-2",
    conversationId: "conversation-1",
    turnNo: 2,
    contextAssemblyId: "context-1",
    promptPreview: "сделай короче",
    retrievalQueryResolution: {
      originalQuery: "сделай короче",
      queryForRetrieval: "сделай короче",
      resolvedQuery: "сделай короче",
      decision: "FALLBACK_ORIGINAL",
      confidence: 1,
      degraded: false,
    },
    selectedHistory: [{
      runId: "run-1",
      turnNo: 1,
      mode: "direct",
      promptPreview: "длинный ответ",
      answerPreview: "короткий ответ",
      status: "COMPLETED",
      tokenEstimate: 42,
    }],
    droppedItems: [{ itemType: "history", runId: "run-1", turnNo: 1, reason: "token_budget", tokenEstimate: 12 }],
    tokenBudget: {
      max: 1000,
      used: 47,
      remaining: 953,
      history: 42,
      summary: 5,
      retrieval: 0,
      dropped: 12,
    },
    summaryState: {
      used: true,
      status: "READY",
      throughTurnNo: 1,
      tokenEstimate: 5,
    },
    stickyStateResolution: {
      active: true,
      stickyVersion: 3,
      fieldSources: {
        model: "sticky",
      },
      resolvedState: {
        model: "sticky-model",
      },
    },
    createdAt: "2026-05-10T00:00:00Z",
  };
}
