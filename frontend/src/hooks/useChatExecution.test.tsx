import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "../api/client";
import type { ChatExecutionResponse, QualityLayerFlags } from "../types";
import { buildChatExecutionResponse } from "../testBuilders";
import { useChatExecution } from "./useChatExecution";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  const executeChat = vi.fn();
  const runResults = new Map<string, ChatExecutionResponse>();
  let runCounter = 0;

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      executeChat,
      submitChatRun: vi.fn(async (request, signal) => {
        const id = `run-${++runCounter}`;
        const result = await executeChat(request, signal);
        runResults.set(id, result as ChatExecutionResponse);
        return {
          id,
          status: "COMPLETED",
          createdAt: "2026-04-16T10:00:00Z",
          traceUrl: `/api/chat-runs/${id}/trace`,
          resultUrl: `/api/chat-runs/${id}/result`,
        };
      }),
      fetchChatRunTrace: vi.fn(async (runId) => ({
        id: runId,
        mode: "rag",
        status: "COMPLETED",
        createdAt: "2026-04-16T10:00:00Z",
        llmCalls: [],
        events: [],
      })),
      fetchChatRunResult: vi.fn(async (runId) => runResults.get(runId) ?? {
        mode: "rag",
        model: "qwen2.5:7b",
        prompt: "",
        answer: "",
        createdAt: "2026-04-16T10:00:00Z",
        promptTokens: null,
        completionTokens: null,
        totalTokens: null,
        appliedInstructions: [],
        sources: [],
      }),
      cancelChatRun: vi.fn(),
    },
  };
});

type HarnessProps = {
  mode: "direct" | "rag";
  initialPrompt: string;
  initialTemporaryInstruction?: string;
  initialAnswerMode?: "brief" | "strict_sources_only";
  rolloutFlags?: QualityLayerFlags | null;
  selectedInstructionIds?: string[];
};

const enabledFlags: QualityLayerFlags = {
  metadataV1: true,
  structuredV1: true,
  metadataFiltersV1: true,
  searchApiV1: true,
  rerankerV1: true,
  queryHintsV1: true,
};

function HookHarness({
  mode,
  initialPrompt,
  initialTemporaryInstruction = "",
  initialAnswerMode = "brief",
  rolloutFlags = null,
  selectedInstructionIds = [],
}: HarnessProps) {
  const chat = useChatExecution({
    mode,
    initialModel: "qwen2.5:7b",
    initialPrompt,
    initialTemporaryInstruction,
    initialAnswerMode,
    rolloutFlags,
    selectedInstructionIds,
  });

  return (
    <section>
      <label>
        {mode}-model
        <select
          aria-label={`${mode}-model`}
          value={chat.model}
          onChange={(event) => chat.setModel(event.target.value)}
        >
          <option value="qwen2.5:7b">qwen2.5:7b</option>
          <option value="qwen2.5:3b">qwen2.5:3b</option>
        </select>
      </label>

      <label>
        {mode}-prompt
        <textarea
          aria-label={`${mode}-prompt`}
          value={chat.prompt}
          onChange={(event) => chat.setPrompt(event.target.value)}
        />
      </label>

      <label>
        {mode}-temporary
        <textarea
          aria-label={`${mode}-temporary`}
          value={chat.temporaryInstruction}
          onChange={(event) => chat.setTemporaryInstruction(event.target.value)}
        />
      </label>

      <button type="button" onClick={() => void chat.submit()}>
        submit-{mode}
      </button>
      <button type="button" onClick={() => chat.updateRetrievalFilter("project", "Manual Project")}>
        set-manual-project
      </button>
      <button type="button" onClick={() => chat.dismissHint("documentNumber")}>
        dismiss-document-number
      </button>
      <button type="button" onClick={() => chat.resetDismissedHints()}>
        reset-hints
      </button>

      <output data-testid={`${mode}-model-output`}>{chat.model}</output>
      <output data-testid={`${mode}-prompt-output`}>{chat.prompt}</output>
      <output data-testid={`${mode}-temporary-output`}>{chat.temporaryInstruction}</output>
      <output data-testid={`${mode}-error-output`}>{chat.error ?? ""}</output>
      <output data-testid={`${mode}-submitting-output`}>{String(chat.isSubmitting)}</output>
      <output data-testid={`${mode}-response-output`}>{chat.response?.answer ?? ""}</output>
      <output data-testid={`${mode}-last-request-output`}>{chat.lastSubmittedRequest?.prompt ?? ""}</output>
      <output data-testid={`${mode}-hint-document-number-output`}>{chat.queryHints.documentNumber ?? ""}</output>
      <output data-testid={`${mode}-effective-document-number-output`}>{chat.effectiveRetrievalFilters.documentNumber ?? ""}</output>
      <output data-testid={`${mode}-effective-project-output`}>{chat.effectiveRetrievalFilters.project ?? ""}</output>
    </section>
  );
}

describe("useChatExecution", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps direct and rag state isolated", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "ok",
      answer: "ok",
    }));

    render(
      <>
        <HookHarness initialPrompt="direct prompt" initialTemporaryInstruction="direct system" mode="direct" />
        <HookHarness initialPrompt="rag prompt" mode="rag" />
      </>,
    );

    await user.selectOptions(screen.getByLabelText("direct-model"), "qwen2.5:3b");
    await user.clear(screen.getByLabelText("direct-prompt"));
    await user.type(screen.getByLabelText("direct-prompt"), "updated direct prompt");
    await user.clear(screen.getByLabelText("direct-temporary"));
    await user.type(screen.getByLabelText("direct-temporary"), "updated direct system");

    expect(screen.getByTestId("direct-model-output").textContent).toBe("qwen2.5:3b");
    expect(screen.getByTestId("rag-model-output").textContent).toBe("qwen2.5:7b");
    expect(screen.getByTestId("direct-prompt-output").textContent).toBe("updated direct prompt");
    expect(screen.getByTestId("rag-prompt-output").textContent).toBe("rag prompt");
    expect(screen.getByTestId("direct-temporary-output").textContent).toBe("updated direct system");
    expect(screen.getByTestId("rag-temporary-output").textContent).toBe("");
  });

  it("keeps the loading state tied to the latest in-flight request", async () => {
    const user = userEvent.setup();
    const signals: Array<AbortSignal | undefined> = [];
    const pendingRequests: Array<{
      resolve: (value: ChatExecutionResponse) => void;
      signal?: AbortSignal;
    }> = [];

    vi.mocked(apiClient.executeChat).mockImplementation((_request, signal) => {
      signals.push(signal);
      return new Promise<ChatExecutionResponse>((resolve, reject) => {
        signal?.addEventListener(
          "abort",
          () => reject(new DOMException("Aborted", "AbortError")),
          { once: true },
        );
        pendingRequests.push({ resolve, signal });
      });
    });

    render(<HookHarness initialPrompt="direct prompt" mode="direct" />);

    await user.click(screen.getByText("submit-direct"));
    await user.click(screen.getByText("submit-direct"));

    await waitFor(() => {
      expect(signals.length).toBe(2);
      expect(signals[0]?.aborted).toBe(true);
      expect(signals[1]?.aborted).toBe(false);
    });

    await waitFor(() => {
      expect(screen.getByTestId("direct-submitting-output").textContent).toBe("true");
    });

    pendingRequests[1]?.resolve(buildChatExecutionResponse({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "direct prompt",
      answer: "ok",
    }));

    await waitFor(() => {
      expect(screen.getByTestId("direct-submitting-output").textContent).toBe("false");
    });
  });

  it("submits the unified chat contract with the selected instruction ids", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Какая цена?",
      answer: "12000",
    }));

    render(
      <HookHarness
        initialPrompt="Какая цена?"
        initialTemporaryInstruction="Не выдумывай"
        mode="rag"
        selectedInstructionIds={["instruction-1", "instruction-2"]}
      />,
    );

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        {
          mode: "rag",
          model: "qwen2.5:7b",
          prompt: "Какая цена?",
          instructionIds: ["instruction-1", "instruction-2"],
          answerMode: "brief",
          knowledgeScope: {
            presetIds: [],
            documentClasses: [],
            tags: [],
            workspaceKey: null,
            uploadedTodayOnly: false,
          },
          temporaryInstruction: "Не выдумывай",
        },
        expect.any(AbortSignal),
      );
    });
  });

  it("auto-applies extracted query hints into rag retrieval filters", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?",
      answer: "Ответ",
    }));

    render(
      <HookHarness
        initialPrompt="Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?"
        mode="rag"
        rolloutFlags={enabledFlags}
      />,
    );

    expect(screen.getByTestId("rag-hint-document-number-output").textContent).toBe("KZ-2026-0415-ENERGY");
    expect(screen.getByTestId("rag-effective-project-output").textContent).toContain("North Upgrade");

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.objectContaining({
          retrievalFilters: expect.objectContaining({
            documentNumber: "KZ-2026-0415-ENERGY",
            project: expect.stringContaining("North Upgrade"),
          }),
        }),
        expect.any(AbortSignal),
      );
    });
  });

  it("does not submit hint filters when rollout flags are missing", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Что указано в договоре KZ-2026-0415-ENERGY?",
      answer: "Ответ",
    }));

    render(
      <HookHarness
        initialPrompt="Что указано в договоре KZ-2026-0415-ENERGY?"
        mode="rag"
      />,
    );

    expect(screen.getByTestId("rag-hint-document-number-output").textContent).toBe("");
    expect(screen.getByTestId("rag-effective-document-number-output").textContent).toBe("");

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.not.objectContaining({
          retrievalFilters: expect.anything(),
        }),
        expect.any(AbortSignal),
      );
    });
  });

  it("submits only manual filters when query hints rollout is disabled", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?",
      answer: "Ответ",
    }));

    render(
      <HookHarness
        initialPrompt="Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?"
        mode="rag"
        rolloutFlags={{ ...enabledFlags, queryHintsV1: false }}
      />,
    );

    await user.click(screen.getByText("set-manual-project"));
    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.objectContaining({
          retrievalFilters: expect.objectContaining({
            project: "Manual Project",
          }),
        }),
        expect.any(AbortSignal),
      );
    });
    expect(vi.mocked(apiClient.executeChat).mock.calls[0][0].retrievalFilters).not.toMatchObject({
      documentNumber: "KZ-2026-0415-ENERGY",
    });
  });

  it("does not submit manual filters when metadata filters rollout is disabled", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Что указано по проекту North Upgrade?",
      answer: "Ответ",
    }));

    render(
      <HookHarness
        initialPrompt="Что указано по проекту North Upgrade?"
        mode="rag"
        rolloutFlags={{ ...enabledFlags, metadataFiltersV1: false, queryHintsV1: false }}
      />,
    );

    await user.click(screen.getByText("set-manual-project"));
    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.not.objectContaining({
          retrievalFilters: expect.anything(),
        }),
        expect.any(AbortSignal),
      );
    });
  });

  it("keeps manual override over auto hint in retrieval filters", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?",
      answer: "Ответ",
    }));

    render(
      <HookHarness
        initialPrompt="Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?"
        mode="rag"
        rolloutFlags={enabledFlags}
      />,
    );

    await user.click(screen.getByText("set-manual-project"));
    expect(screen.getByTestId("rag-effective-project-output").textContent).toBe("Manual Project");

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.objectContaining({
          retrievalFilters: expect.objectContaining({
            project: "Manual Project",
          }),
        }),
        expect.any(AbortSignal),
      );
    });
  });

  it("keeps dismissed hints cleared until reset", async () => {
    const user = userEvent.setup();

    render(
      <HookHarness
        initialPrompt="Что указано в договоре KZ-2026-0415-ENERGY?"
        mode="rag"
        rolloutFlags={enabledFlags}
      />,
    );

    expect(screen.getByTestId("rag-effective-document-number-output").textContent).toBe("KZ-2026-0415-ENERGY");

    await user.click(screen.getByText("dismiss-document-number"));
    expect(screen.getByTestId("rag-effective-document-number-output").textContent).toBe("");

    await user.type(screen.getByLabelText("rag-prompt"), " обнови ответ");
    expect(screen.getByTestId("rag-effective-document-number-output").textContent).toBe("");

    await user.click(screen.getByText("reset-hints"));
    expect(screen.getByTestId("rag-effective-document-number-output").textContent).toBe("KZ-2026-0415-ENERGY");
  });

  it("shows requestId for unexpected backend failures", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockRejectedValue(
      new ApiClientError("Unexpected server error", {
        code: "internal.unexpected_error",
        requestId: "req-123",
        status: 500,
      }),
    );

    render(<HookHarness initialPrompt="Какая цена?" mode="rag" />);

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(screen.getByTestId("rag-error-output").textContent).toContain("req-123");
    });
  });

  it("translates known llm provider errors into a friendly message", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockRejectedValue(
      new ApiClientError("Unable to reach the local LLM provider", {
        code: "llm.provider_unavailable",
        status: 503,
      }),
    );

    render(<HookHarness initialPrompt="Какая цена?" mode="rag" />);

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(screen.getByTestId("rag-error-output").textContent).toContain("Ollama запущен");
    });
  });

  it("translates inactive instruction errors into a friendly message", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockRejectedValue(
      new ApiClientError("Instruction is inactive", {
        code: "instruction.inactive",
        status: 400,
      }),
    );

    render(<HookHarness initialPrompt="Какая цена?" mode="rag" />);

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(screen.getByTestId("rag-error-output").textContent).toContain("неактивна");
    });
  });

  it("submits strict mode and renders the no-sources fallback message", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue(buildChatExecutionResponse({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Какая цена?",
      answer: "Не найдено в источниках.",
      contextStatus: "no-context",
      answerModeApplied: "strict_sources_only",
    }));

    render(
      <HookHarness
        initialPrompt="Какая цена?"
        initialAnswerMode="strict_sources_only"
        mode="rag"
      />,
    );

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        expect.objectContaining({
          answerMode: "strict_sources_only",
        }),
        expect.any(AbortSignal),
      );
      expect(screen.getByTestId("rag-response-output").textContent).toBe("Не найдено в источниках.");
    });
  });

  it("clears stale response on a new submit and tracks the last submitted request", async () => {
    const user = userEvent.setup();
    let resolveSecondRequest: (value: ChatExecutionResponse) => void = () => undefined;

    vi.mocked(apiClient.executeChat)
      .mockResolvedValueOnce(buildChatExecutionResponse({
        mode: "direct",
        model: "qwen2.5:7b",
        prompt: "first prompt",
        answer: "first answer",
      }))
      .mockImplementationOnce(() =>
        new Promise<ChatExecutionResponse>((resolve) => {
          resolveSecondRequest = resolve;
        }),
      );

    render(<HookHarness initialPrompt="first prompt" mode="direct" />);

    await user.click(screen.getByText("submit-direct"));

    await waitFor(() => {
      expect(screen.getByTestId("direct-response-output").textContent).toBe("first answer");
      expect(screen.getByTestId("direct-last-request-output").textContent).toBe("first prompt");
    });

    await user.clear(screen.getByLabelText("direct-prompt"));
    await user.type(screen.getByLabelText("direct-prompt"), "second prompt");
    await user.click(screen.getByText("submit-direct"));

    await waitFor(() => {
      expect(screen.getByTestId("direct-response-output").textContent).toBe("");
      expect(screen.getByTestId("direct-last-request-output").textContent).toBe("second prompt");
      expect(screen.getByTestId("direct-submitting-output").textContent).toBe("true");
    });

    resolveSecondRequest(buildChatExecutionResponse({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "second prompt",
      answer: "second answer",
      createdAt: "2026-04-16T10:00:01Z",
    }));

    await waitFor(() => {
      expect(screen.getByTestId("direct-response-output").textContent).toBe("second answer");
    });
  });
});
