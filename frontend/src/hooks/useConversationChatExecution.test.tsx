import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import type { ChatExecutionRequest, ConversationDetail } from "../types";
import { buildChatExecutionResponse } from "../testBuilders";
import { useChatExecution } from "./useChatExecution";
import { useConversationChatExecution } from "./useConversationChatExecution";
import { useState } from "react";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      submitChatRun: vi.fn(async (request: ChatExecutionRequest) => ({
        id: "run-2",
        status: "COMPLETED",
        createdAt: "2026-05-10T00:00:00Z",
        statusUrl: "/api/chat-runs/run-2/status",
        traceUrl: "/api/chat-runs/run-2/trace",
        resultUrl: "/api/chat-runs/run-2/result",
        conversationId: request.conversationId ?? "conversation-1",
        turnNo: 2,
      })),
      fetchChatRunStatus: vi.fn(async () => ({
        id: "run-2",
        status: "COMPLETED",
        createdAt: "2026-05-10T00:00:00Z",
      })),
      fetchChatRunResult: vi.fn(async () => buildChatExecutionResponse({
        conversationId: "conversation-1",
        turnNo: 2,
      })),
    },
  };
});

const conversationDetail: ConversationDetail = {
  id: "conversation-1",
  workspaceKey: "grid",
  title: "Sticky chat",
  mode: "rag",
  status: "ACTIVE",
  defaultModel: "conversation-default",
  defaultAnswerMode: "brief",
  createdAt: "2026-05-10T00:00:00Z",
  updatedAt: "2026-05-10T00:00:00Z",
  lastRunAt: "2026-05-10T00:00:00Z",
  turnCount: 1,
  stickyState: {
    model: "sticky-model",
    answerMode: "strict_sources_only",
    knowledgeScope: {
      presetIds: [],
      facetIds: [],
      documentClasses: [],
      documentTypes: [],
      documentStatuses: [],
      projectKeys: ["grid-project"],
      documentNumber: null,
      languageCodes: [],
      tags: [],
      workspaceKey: "grid",
      periodStartFrom: null,
      periodStartTo: null,
      periodEndFrom: null,
      periodEndTo: null,
      uploadedTodayOnly: false,
    },
    retrievalFilters: {
      documentNumber: "sticky-doc",
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
    instructionIds: ["sticky-instruction"],
  },
};

function Harness({
  conversationsEnabled = true,
  selected = true,
}: {
  conversationsEnabled?: boolean;
  selected?: boolean;
}) {
  const [instructionIds, setInstructionIds] = useState<string[]>([]);
  const chat = useChatExecution({
    mode: "rag",
    initialModel: "initial-model",
    initialPrompt: "а по второму документу?",
    initialAnswerMode: "brief",
    rolloutFlags: {
      metadataV1: true,
      structuredV1: true,
      metadataFiltersV1: true,
      searchApiV1: true,
      rerankerV1: true,
      queryHintsV1: true,
    },
    selectedInstructionIds: instructionIds,
    workspaceKey: "grid",
  });
  const conversationChat = useConversationChatExecution({
    chat,
    conversationsEnabled,
    conversationId: selected ? "conversation-1" : null,
    conversationDetail: selected ? conversationDetail : null,
    setSelectedInstructionIds: setInstructionIds,
  });

  return (
    <section>
      <output data-testid="model">{conversationChat.model}</output>
      <output data-testid="answer-mode">{conversationChat.answerMode}</output>
      <output data-testid="instruction-ids">{instructionIds.join(",")}</output>
      <button type="button" onClick={() => void conversationChat.submit()}>submit</button>
      <button type="button" onClick={() => conversationChat.setModel("dirty-model")}>dirty-model</button>
      <button type="button" onClick={() => conversationChat.updateRetrievalFilter("documentNumber", "manual-doc")}>
        dirty-filter
      </button>
      <button
        type="button"
        onClick={() => {
          conversationChat.markInstructionIdsDirty();
          setInstructionIds([]);
        }}
      >
        clear-instructions
      </button>
    </section>
  );
}

describe("useConversationChatExecution", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("seeds controls from sticky state and omits clean inherited fields", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await waitFor(() => expect(screen.getByTestId("model").textContent).toBe("sticky-model"));
    expect(screen.getByTestId("answer-mode").textContent).toBe("strict_sources_only");
    expect(screen.getByTestId("instruction-ids").textContent).toBe("sticky-instruction");

    await user.click(screen.getByRole("button", { name: "submit" }));

    await waitFor(() => expect(apiClient.submitChatRun).toHaveBeenCalled());
    const request = vi.mocked(apiClient.submitChatRun).mock.calls[0][0] as ChatExecutionRequest;
    expect(request.conversationId).toBe("conversation-1");
    expect(request.prompt).toBe("а по второму документу?");
    expect(request.model).toBeUndefined();
    expect(request.answerMode).toBeUndefined();
    expect(request.knowledgeScope).toBeUndefined();
    expect(request.retrievalFilters).toBeUndefined();
    expect(request.instructionIds).toBeUndefined();
  });

  it("sends dirty fields and manual filters for conversational RAG", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await waitFor(() => expect(screen.getByTestId("model").textContent).toBe("sticky-model"));
    await user.click(screen.getByRole("button", { name: "dirty-model" }));
    await user.click(screen.getByRole("button", { name: "dirty-filter" }));
    await user.click(screen.getByRole("button", { name: "clear-instructions" }));
    await user.click(screen.getByRole("button", { name: "submit" }));

    await waitFor(() => expect(apiClient.submitChatRun).toHaveBeenCalled());
    const request = vi.mocked(apiClient.submitChatRun).mock.calls[0][0] as ChatExecutionRequest;
    expect(request.model).toBe("dirty-model");
    expect(request.retrievalFilters?.documentNumber).toBe("manual-doc");
    expect(request.instructionIds).toEqual([]);
  });

  it("stays stateless when conversations are disabled by feature state", async () => {
    const user = userEvent.setup();
    render(<Harness conversationsEnabled={false} />);

    expect(screen.getByTestId("model").textContent).toBe("initial-model");

    await user.click(screen.getByRole("button", { name: "submit" }));

    await waitFor(() => expect(apiClient.submitChatRun).toHaveBeenCalled());
    const request = vi.mocked(apiClient.submitChatRun).mock.calls[0][0] as ChatExecutionRequest;
    expect(request.persistConversation).toBeUndefined();
    expect(request.conversationId).toBeUndefined();
    expect(request.clientTurnId).toBeUndefined();
    expect(request.contextOptions).toBeUndefined();
    expect(request.model).toBe("initial-model");
  });
});
