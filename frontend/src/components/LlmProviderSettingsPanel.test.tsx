import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
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
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

describe("LlmProviderSettingsPanel", () => {
  it("renders loading, empty state, provider list, and active badges", async () => {
    let resolveProviders: (providers: (typeof provider)[]) => void = () => {};
    vi.mocked(apiClient.fetchLlmProviders).mockReturnValue(
      new Promise((resolve) => {
        resolveProviders = resolve;
      }),
    );

    render(<LlmProviderSettingsPanel />);

    expect(screen.getByText("Загружаем подключения...")).toBeTruthy();
    resolveProviders([]);
    expect(await screen.findByText("Подключения не созданы. Используется env fallback.")).toBeTruthy();

    cleanup();
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([
      provider,
      { ...provider, id: "provider-2", name: "Embedding VM", activeChat: false, activeEmbedding: true },
    ]);

    render(<LlmProviderSettingsPanel />);

    expect(await screen.findByText("Corp LLM VM 01")).toBeTruthy();
    expect(screen.getByText("Embedding VM")).toBeTruthy();
    expect(screen.getByText("Chat active")).toBeTruthy();
    expect(screen.getByText("Embedding active")).toBeTruthy();
  });

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
    expect(await screen.findByText("UP")).toBeTruthy();
    expect(await screen.findByText("840 ms")).toBeTruthy();
  });

  it("creates a provider with trimmed payload and does not render the API key afterward", async () => {
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(apiClient.fetchLlmProviders)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ ...provider, hasApiKey: true }]);
    vi.mocked(apiClient.createLlmProvider).mockResolvedValue({ ...provider, hasApiKey: true });

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "  Corp LLM  ");
    await userEvent.type(screen.getByLabelText("Base URL"), "  http://10.10.20.15:8000  ");
    const apiKeyInput = document.querySelector('input[type="password"]') as HTMLInputElement;
    await userEvent.type(apiKeyInput, "super-secret");
    await userEvent.click(screen.getByLabelText("Сделать активным для Chat"));
    await userEvent.click(screen.getByLabelText("Сделать активным для Embeddings"));
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    await waitFor(() => {
      expect(apiClient.createLlmProvider).toHaveBeenCalledWith(expect.objectContaining({
        activeChat: true,
        activeEmbedding: true,
        apiKey: "super-secret",
        baseUrl: "http://10.10.20.15:8000",
        name: "Corp LLM",
      }));
    });
    expect(confirmSpy).toHaveBeenCalled();
    expect(screen.queryByText("super-secret")).toBeNull();
  });

  it("cancels create when active embedding confirmation is rejected", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([]);
    vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "Corp LLM");
    await userEvent.type(screen.getByLabelText("Base URL"), "http://10.10.20.15:8000");
    await userEvent.click(screen.getByLabelText("Сделать активным для Embeddings"));
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(apiClient.createLlmProvider).not.toHaveBeenCalled();
  });

  it("preserves, replaces, and clears API keys while editing without pre-filling secrets", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    vi.mocked(apiClient.updateLlmProvider).mockResolvedValue(provider);

    const { rerender } = render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Edit/i }));
    const firstApiKeyInput = screen.getByLabelText("API key") as HTMLInputElement;
    expect(firstApiKeyInput.value).toBe("");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    await waitFor(() => {
      expect(apiClient.updateLlmProvider).toHaveBeenCalledWith("provider-1", expect.objectContaining({
        apiKey: null,
        clearApiKey: false,
      }));
    });

    vi.mocked(apiClient.updateLlmProvider).mockClear();
    await userEvent.click(await screen.findByRole("button", { name: /Edit/i }));
    await userEvent.type(screen.getByLabelText("API key"), "replacement-secret");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    await waitFor(() => {
      expect(apiClient.updateLlmProvider).toHaveBeenCalledWith("provider-1", expect.objectContaining({
        apiKey: "replacement-secret",
        clearApiKey: false,
      }));
    });

    vi.mocked(apiClient.updateLlmProvider).mockClear();
    rerender(<LlmProviderSettingsPanel />);
    await userEvent.click(await screen.findByRole("button", { name: /Edit/i }));
    await userEvent.click(screen.getByLabelText("Очистить сохраненный API key"));
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    await waitFor(() => {
      expect(apiClient.updateLlmProvider).toHaveBeenCalledWith("provider-1", expect.objectContaining({
        apiKey: null,
        clearApiKey: true,
      }));
    });
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

  it("validates base URL and numeric ranges before submit", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([]);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "Corp LLM");
    await userEvent.type(screen.getByLabelText("Base URL"), "ftp://10.10.20.15:8000");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(apiClient.createLlmProvider).not.toHaveBeenCalled();
    expect(await screen.findByText("Base URL должен быть корректным http:// или https:// URL.")).toBeTruthy();

    await userEvent.clear(screen.getByLabelText("Base URL"));
    await userEvent.type(screen.getByLabelText("Base URL"), "http://10.10.20.15:8000");
    await userEvent.clear(screen.getByLabelText("Temperature"));
    await userEvent.type(screen.getByLabelText("Temperature"), "3");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(apiClient.createLlmProvider).not.toHaveBeenCalled();
    expect(await screen.findByText("Temperature: укажите число от 0 до 2.")).toBeTruthy();
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
    expect(await screen.findByText("DEGRADED")).toBeTruthy();
    expect(await screen.findByText("840 ms")).toBeTruthy();
    expect(await screen.findByText("Provider models endpoint is unavailable or unsupported")).toBeTruthy();
  });

  it("renders down probe state with sanitized error text", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    vi.mocked(apiClient.probeLlmProvider).mockResolvedValue({
      id: "provider-1",
      status: "DOWN",
      checkedAt: "2026-05-10T10:00:00Z",
      latencyMs: 0,
      modelsAvailable: false,
      chatAvailable: false,
      embeddingAvailable: false,
      errorCode: "llm_provider.unreachable",
      errorMessage: "Connection refused by provider",
    });

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Probe/i }));

    expect(await screen.findByText("DOWN")).toBeTruthy();
    expect(await screen.findByText("0 ms")).toBeTruthy();
    expect((await screen.findAllByText("Connection refused by provider")).length).toBeGreaterThan(0);
  });

  it("renders remote model count and names", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    vi.mocked(apiClient.fetchLlmProviderModels).mockResolvedValue([
      { name: "qwen2.5:32b" },
      { name: "bge-m3" },
    ]);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Models/i }));

    expect(await screen.findByText("Получено моделей: 2")).toBeTruthy();
    expect(await screen.findByText("2 remote")).toBeTruthy();
    expect((await screen.findAllByText("qwen2.5:32b")).length).toBeGreaterThan(1);
    expect((await screen.findAllByText("bge-m3")).length).toBeGreaterThan(1);
  });

  it("disables incompatible activation actions with clear labels", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([
      { ...provider, activeChat: false, purpose: "EMBEDDING" },
      { ...provider, id: "provider-2", name: "Chat VM", activeChat: false, purpose: "CHAT" },
    ]);

    render(<LlmProviderSettingsPanel />);

    const embeddingRow = (await screen.findByText("Corp LLM VM 01")).closest("tr") as HTMLElement;
    const chatRow = screen.getByText("Chat VM").closest("tr") as HTMLElement;

    expect((within(embeddingRow).getByRole("button", { name: /Chat недоступен/i }) as HTMLButtonElement).disabled)
      .toBe(true);
    expect((within(chatRow).getByRole("button", { name: /Embeddings недоступны/i }) as HTMLButtonElement).disabled)
      .toBe(true);
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

  it("cancels fallback embedding activation when confirmation is rejected", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([provider]);
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Embeddings env fallback/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(apiClient.activateLlmFallback).not.toHaveBeenCalled();
  });

  it("confirms inactive delete and disables active provider delete", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([
      provider,
      { ...provider, id: "provider-2", name: "Inactive VM", activeChat: false, activeEmbedding: false },
    ]);
    vi.mocked(apiClient.deleteLlmProvider).mockResolvedValue(undefined);
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);

    render(<LlmProviderSettingsPanel />);

    const activeRow = (await screen.findByText("Corp LLM VM 01")).closest("tr") as HTMLElement;
    const inactiveRow = screen.getByText("Inactive VM").closest("tr") as HTMLElement;

    expect((within(activeRow).getByRole("button", { name: /Delete/i }) as HTMLButtonElement).disabled)
      .toBe(true);
    await userEvent.click(within(inactiveRow).getByRole("button", { name: /Delete/i }));

    expect(confirmSpy).toHaveBeenCalledWith(expect.stringContaining("Inactive VM"));
    expect(apiClient.deleteLlmProvider).not.toHaveBeenCalled();

    await userEvent.click(within(inactiveRow).getByRole("button", { name: /Delete/i }));

    await waitFor(() => {
      expect(apiClient.deleteLlmProvider).toHaveBeenCalledWith("provider-2");
    });
  });

  it("shows backend errors and keeps the dialog state intact", async () => {
    vi.mocked(apiClient.fetchLlmProviders).mockResolvedValue([]);
    vi.mocked(apiClient.createLlmProvider).mockRejectedValue(new Error("Backend validation failed"));

    render(<LlmProviderSettingsPanel />);

    await userEvent.click(await screen.findByRole("button", { name: /Добавить/i }));
    await userEvent.type(screen.getByLabelText("Название подключения"), "Corp LLM");
    await userEvent.type(screen.getByLabelText("Base URL"), "http://10.10.20.15:8000");
    await userEvent.click(screen.getByRole("button", { name: /Сохранить/i }));

    expect(await screen.findByText("Backend validation failed")).toBeTruthy();
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect((screen.getByLabelText("Название подключения") as HTMLInputElement).value).toBe("Corp LLM");
  });
});
