import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/client";
import { LlmProviderSettingsPanel } from "./LlmProviderSettingsPanel";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchLlmProviders: vi.fn(),
      probeLlmProvider: vi.fn(),
      fetchLlmProviderModels: vi.fn(),
      createLlmProvider: vi.fn(),
      updateLlmProvider: vi.fn(),
      activateLlmProvider: vi.fn(),
      activateLlmFallback: vi.fn(),
      deleteLlmProvider: vi.fn(),
    },
  };
});

const provider = {
  id: "provider-1",
  name: "Corp LLM VM 01",
  providerType: "OPENAI_COMPATIBLE" as const,
  purpose: "CHAT_AND_EMBEDDING" as const,
  baseUrl: "http://10.10.20.15:8000",
  hasApiKey: true,
  authHeaderName: "Authorization",
  authScheme: "Bearer",
  chatCompletionsPath: "/v1/chat/completions",
  modelsPath: "/v1/models",
  embeddingsPath: "/v1/embeddings",
  defaultModel: "qwen2.5:32b",
  embeddingModel: "bge-m3",
  temperature: 0.2,
  timeoutSeconds: 600,
  expectedEmbeddingDimension: 1024,
  activeChat: true,
  activeEmbedding: false,
  status: "UNKNOWN" as const,
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("LlmProviderSettingsPanel", () => {
  it("renders providers and probe result", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    vi.mocked(apiClient.probeLlmProvider).mockResolvedValue({
      id: "provider-1",
      status: "UP",
      checkedAt: "2026-05-10T10:00:00Z",
      latencyMs: 840,
      modelsAvailable: true,
      chatAvailable: true,
      embeddingAvailable: true,
    });

    render(<LlmProviderSettingsPanel />);

    expect(await screen.findByText("Corp LLM VM 01")).toBeTruthy();
    expect(screen.getByText("Chat active")).toBeTruthy();

    await userEvent.click(screen.getByRole("button", { name: /Probe/i }));

    await waitFor(() => {
      expect(apiClient.probeLlmProvider).toHaveBeenCalledWith("provider-1");
    });
    expect(await screen.findByText("Подключение доступно.")).toBeTruthy();
  });

  it("opens create form and submits without rendering the API key afterward", async () => {
    vi.mocked(apiClient.fetchLlmProviders)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ ...provider, hasApiKey: true }]);
    vi.mocked(apiClient.createLlmProvider).mockResolvedValue({ ...provider, hasApiKey: true });

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getAllByRole("textbox")[0], "Corp LLM");
    await userEvent.type(screen.getByPlaceholderText("http://10.10.20.15:8000"), "http://10.10.20.15:8000");
    const apiKeyInput = document.querySelector('input[type="password"]') as HTMLInputElement;
    await userEvent.type(apiKeyInput, "super-secret");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    await waitFor(() => {
      expect(apiClient.createLlmProvider).toHaveBeenCalled();
    });
    expect(screen.queryByText("super-secret")).toBeNull();
  });

  it("validates provider paths before submit", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([]);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "Corp LLM");
    await userEvent.type(screen.getByLabelText("Base URL"), "http://10.10.20.15:8000");
    await userEvent.clear(screen.getByLabelText("Models path"));
    await userEvent.type(screen.getByLabelText("Models path"), "https://evil.internal/v1/models");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(apiClient.createLlmProvider).not.toHaveBeenCalled();
    expect(await screen.findByText('Models path должен начинаться с "/" и не быть абсолютным URL.')).toBeTruthy();
  });

  it("validates auth header names before submit", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([]);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "Corp LLM");
    await userEvent.type(screen.getByLabelText("Base URL"), "http://10.10.20.15:8000");
    await userEvent.clear(screen.getByLabelText("Auth header name"));
    await userEvent.type(screen.getByLabelText("Auth header name"), "Authorization Bad");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(apiClient.createLlmProvider).not.toHaveBeenCalled();
    expect(await screen.findByText("Auth header name должен быть корректным HTTP header token.")).toBeTruthy();
  });

  it("renders degraded probe state distinctly", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    vi.mocked(apiClient.probeLlmProvider).mockResolvedValue({
      id: "provider-1",
      status: "DEGRADED",
      checkedAt: "2026-05-10T10:00:00Z",
      latencyMs: 840,
      modelsAvailable: false,
      chatAvailable: true,
      embeddingAvailable: true,
      errorCode: "llm_provider.models_unsupported",
      errorMessage: "Provider models endpoint is unavailable or unsupported",
    });

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Probe/i }));

    expect(await screen.findByText("Models endpoint недоступен, chat/embeddings OK.")).toBeTruthy();
    expect(await screen.findByText("Provider models endpoint is unavailable or unsupported")).toBeTruthy();
  });

  it("clear key disables and empties the API key field while editing", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Edit/i }));
    const apiKeyInput = screen.getByLabelText("API key");
    await userEvent.type(apiKeyInput, "replacement-secret");
    await userEvent.click(screen.getByLabelText("Очистить сохраненный API key"));

    expect(apiKeyInput).toBeInstanceOf(HTMLInputElement);
    const input = apiKeyInput as HTMLInputElement;
    expect(input.disabled).toBe(true);
    expect(input.value).toBe("");
  });

  it("requires confirmation before embedding activation", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<LlmProviderSettingsPanel />);

    await screen.findByText("Corp LLM VM 01");
    await userEvent.click(screen.getByRole("button", { name: /^Embeddings$/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(apiClient.activateLlmProvider).not.toHaveBeenCalled();
  });
});
