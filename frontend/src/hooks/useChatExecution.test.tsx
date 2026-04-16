import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "../api/client";
import { useChatExecution } from "./useChatExecution";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      executeChat: vi.fn(),
    },
  };
});

type HarnessProps = {
  mode: "direct" | "rag";
  initialPrompt: string;
  initialSystemPrompt?: string;
};

function HookHarness({ mode, initialPrompt, initialSystemPrompt = "" }: HarnessProps) {
  const chat = useChatExecution({
    mode,
    initialModel: "qwen2.5:7b",
    initialPrompt,
    initialSystemPrompt,
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
        {mode}-system
        <textarea
          aria-label={`${mode}-system`}
          value={chat.systemPrompt}
          onChange={(event) => chat.setSystemPrompt(event.target.value)}
        />
      </label>

      <button type="button" onClick={() => void chat.submit()}>
        submit-{mode}
      </button>

      <output data-testid={`${mode}-model-output`}>{chat.model}</output>
      <output data-testid={`${mode}-prompt-output`}>{chat.prompt}</output>
      <output data-testid={`${mode}-system-output`}>{chat.systemPrompt}</output>
      <output data-testid={`${mode}-error-output`}>{chat.error ?? ""}</output>
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
    vi.mocked(apiClient.executeChat).mockResolvedValue({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "ok",
      answer: "ok",
      createdAt: "2026-04-16T10:00:00Z",
      promptTokens: 1,
      completionTokens: 1,
      totalTokens: 2,
      appliedInstructions: [],
      sources: [],
    });

    render(
      <>
        <HookHarness initialPrompt="direct prompt" initialSystemPrompt="direct system" mode="direct" />
        <HookHarness initialPrompt="rag prompt" mode="rag" />
      </>,
    );

    await user.selectOptions(screen.getByLabelText("direct-model"), "qwen2.5:3b");
    await user.clear(screen.getByLabelText("direct-prompt"));
    await user.type(screen.getByLabelText("direct-prompt"), "updated direct prompt");
    await user.clear(screen.getByLabelText("direct-system"));
    await user.type(screen.getByLabelText("direct-system"), "updated direct system");

    expect(screen.getByTestId("direct-model-output").textContent).toBe("qwen2.5:3b");
    expect(screen.getByTestId("rag-model-output").textContent).toBe("qwen2.5:7b");
    expect(screen.getByTestId("direct-prompt-output").textContent).toBe("updated direct prompt");
    expect(screen.getByTestId("rag-prompt-output").textContent).toBe("rag prompt");
    expect(screen.getByTestId("direct-system-output").textContent).toBe("updated direct system");
    expect(screen.getByTestId("rag-system-output").textContent).toBe("");
  });

  it("aborts the previous in-flight request when submit is called again", async () => {
    const user = userEvent.setup();
    const signals: Array<AbortSignal | undefined> = [];

    vi.mocked(apiClient.executeChat).mockImplementation(async (_request, signal) => {
      signals.push(signal);
      return await new Promise(() => undefined);
    });

    render(<HookHarness initialPrompt="direct prompt" mode="direct" />);

    await user.click(screen.getByText("submit-direct"));
    await user.click(screen.getByText("submit-direct"));

    await waitFor(() => {
      expect(signals.length).toBe(2);
      expect(signals[0]?.aborted).toBe(true);
      expect(signals[1]?.aborted).toBe(false);
    });
  });

  it("submits the unified chat contract with an empty instruction list", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.executeChat).mockResolvedValue({
      mode: "rag",
      model: "qwen2.5:7b",
      prompt: "Какая цена?",
      answer: "12000",
      createdAt: "2026-04-16T10:00:00Z",
      promptTokens: 2,
      completionTokens: 1,
      totalTokens: 3,
      appliedInstructions: [],
      sources: [],
    });

    render(<HookHarness initialPrompt="Какая цена?" initialSystemPrompt="Не выдумывай" mode="rag" />);

    await user.click(screen.getByText("submit-rag"));

    await waitFor(() => {
      expect(apiClient.executeChat).toHaveBeenCalledWith(
        {
          mode: "rag",
          model: "qwen2.5:7b",
          prompt: "Какая цена?",
          systemPrompt: "Не выдумывай",
          instructionIds: [],
        },
        expect.any(AbortSignal),
      );
    });
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
});
