import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

const healthResponse = {
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
};

const modelsResponse = [{ name: "qwen2.5:7b" }, { name: "qwen2.5:3b" }];

const materialsResponse = [
  {
    id: "material-1",
    title: "Pricing note",
    sourceType: "text",
    originalFileName: null,
    extractable: true,
    createdAt: "2026-04-16T10:00:00Z",
    contentLength: 42,
    preview: "Тариф Премиум стоит 12000 тенге в месяц.",
  },
];

const materialUploadPolicyResponse = {
  maxUploadBytes: 2_000_000,
  acceptedExtensions: ["txt", "pdf"],
  acceptedMimeHints: ["text/plain", "application/pdf"],
  richDocumentSupport: true,
  pdf: {
    enabled: true,
    scannedPdfSupport: true,
    mode: "embedded_text_and_ocr" as const,
    ocrLanguages: ["kaz", "rus", "eng"],
    ocrMaxPages: 20,
  },
};

const legacyMaterialUploadPolicyResponse = {
  maxUploadBytes: 2_000_000,
  acceptedExtensions: ["txt", "pdf"],
  acceptedMimeHints: ["text/plain", "application/pdf"],
  richDocumentSupport: true,
};

const instructionsResponse = [
  {
    id: "instruction-1",
    title: "Базовая роль ассистента",
    category: "system",
    content: "Отвечай кратко и по делу.",
    createdAt: "2026-04-16T10:00:00Z",
  },
];

const jsonResponse = (payload: unknown) =>
  new Response(JSON.stringify(payload), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
    },
  });

const getUrl = (input: RequestInfo | URL) => {
  if (typeof input === "string") {
    return input;
  }

  if (input instanceof Request) {
    return input.url;
  }

  return input.toString();
};

describe("App", () => {
  let currentMaterialUploadPolicyResponse:
    | typeof materialUploadPolicyResponse
    | typeof legacyMaterialUploadPolicyResponse;

  beforeEach(() => {
    currentMaterialUploadPolicyResponse = materialUploadPolicyResponse;

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = getUrl(input);

      if (url.endsWith("/api/health")) {
        return jsonResponse(healthResponse);
      }

      if (url.endsWith("/api/models")) {
        return jsonResponse(modelsResponse);
      }

      if (url.endsWith("/api/materials/policy")) {
        return jsonResponse(currentMaterialUploadPolicyResponse);
      }

      if (url.endsWith("/api/materials")) {
        return jsonResponse(materialsResponse);
      }

      if (url.endsWith("/api/instructions")) {
        return jsonResponse(instructionsResponse);
      }

      if (url.endsWith("/api/chat")) {
        const request = init?.body ? JSON.parse(String(init.body)) : {};

        return jsonResponse({
          mode: request.mode,
          model: request.model,
          prompt: request.prompt,
          answer: "ok",
          createdAt: "2026-04-16T10:00:00Z",
          promptTokens: 1,
          completionTokens: 1,
          totalTokens: 2,
          appliedInstructions: [],
          sources: [],
        });
      }

      throw new Error(`Unexpected request: ${url}`);
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("opens on overview and switches between the five workspace tabs", async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await screen.findByRole("tab", { name: "Обзор" });

    const overviewTab = screen.getByRole("tab", { name: "Обзор" });
    const materialsTab = screen.getByRole("tab", { name: "Материалы" });
    const instructionsTab = screen.getByRole("tab", { name: "Инструкции" });
    const ragTab = screen.getByRole("tab", { name: "RAG чат" });
    const directTab = screen.getByRole("tab", { name: "Direct чат" });

    expect(overviewTab.getAttribute("aria-selected")).toBe("true");
    expect(materialsTab.getAttribute("aria-selected")).toBe("false");
    expect(instructionsTab.getAttribute("aria-selected")).toBe("false");
    expect(ragTab.getAttribute("aria-selected")).toBe("false");
    expect(directTab.getAttribute("aria-selected")).toBe("false");

    const overviewPanel = container.querySelector("#tabpanel-overview") as HTMLElement;
    const materialsPanel = container.querySelector("#tabpanel-materials") as HTMLElement;
    const instructionsPanel = container.querySelector("#tabpanel-instructions") as HTMLElement;
    const ragPanel = container.querySelector("#tabpanel-rag") as HTMLElement;
    const directPanel = container.querySelector("#tabpanel-direct") as HTMLElement;

    expect(overviewPanel.hidden).toBe(false);
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(true);
    expect(ragPanel.hidden).toBe(true);
    expect(directPanel.hidden).toBe(true);
    expect(screen.getByText("Backend")).toBeTruthy();
    expect(screen.queryByText("Knowledge Base")).toBeNull();
    expect(screen.queryByText("Prompt Snippets")).toBeNull();
    expect(screen.queryByText("Instruction Library")).toBeNull();

    await user.click(materialsTab);
    expect(materialsTab.getAttribute("aria-selected")).toBe("true");
    expect(overviewPanel.hidden).toBe(true);
    expect(materialsPanel.hidden).toBe(false);
    expect(screen.getByRole("heading", { name: "Материалы для RAG" })).toBeTruthy();
    expect(screen.queryByText("Prompt Snippets")).toBeNull();

    await user.click(instructionsTab);
    expect(instructionsTab.getAttribute("aria-selected")).toBe("true");
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(false);
    expect(screen.getByText("Prompt Snippets")).toBeTruthy();
    expect(screen.getByText("Instruction Library")).toBeTruthy();

    await user.click(ragTab);
    expect(ragPanel.hidden).toBe(false);
    expect(screen.getByRole("heading", { name: "Ответ по материалам" })).toBeTruthy();
    expect(within(screen.getByRole("tabpanel", { name: "RAG чат" })).queryByRole("checkbox")).toBeNull();

    await user.click(directTab);
    expect(directPanel.hidden).toBe(false);
    expect(screen.getByRole("heading", { name: "Прямой запрос к модели" })).toBeTruthy();
    expect(within(screen.getByRole("tabpanel", { name: "Direct чат" })).queryByRole("checkbox")).toBeNull();
  });

  it("keeps rag and direct form state when switching tabs without instruction selectors", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("tab", { name: "RAG чат" }));

    const ragPanel = screen.getByRole("tabpanel", { name: "RAG чат" });
    const ragPrompt = within(ragPanel).getByLabelText("Вопрос") as HTMLTextAreaElement;
    const ragSystemPrompt = within(ragPanel).getByLabelText("System override") as HTMLTextAreaElement;

    await user.clear(ragPrompt);
    await user.type(ragPrompt, "Новый вопрос по материалам");
    await user.type(ragSystemPrompt, "Только по контексту");

    expect(ragPrompt.value).toBe("Новый вопрос по материалам");
    expect(ragSystemPrompt.value).toBe("Только по контексту");
    expect(within(ragPanel).queryByRole("checkbox")).toBeNull();

    await user.click(screen.getByRole("tab", { name: "Direct чат" }));

    const directPanel = screen.getByRole("tabpanel", { name: "Direct чат" });
    const directPrompt = within(directPanel).getByLabelText("User prompt") as HTMLTextAreaElement;
    const directSystemPrompt = within(directPanel).getByLabelText("System prompt") as HTMLTextAreaElement;

    await user.clear(directPrompt);
    await user.type(directPrompt, "Прямой запрос для проверки");
    await user.type(directSystemPrompt, "Кратко");

    expect(directPrompt.value).toBe("Прямой запрос для проверки");
    expect(directSystemPrompt.value).toContain("Кратко");
    expect(within(directPanel).queryByRole("checkbox")).toBeNull();

    await user.click(screen.getByRole("tab", { name: "Обзор" }));
    await user.click(screen.getByRole("tab", { name: "RAG чат" }));

    const ragPromptAfterSwitch = within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByLabelText(
      "Вопрос",
    ) as HTMLTextAreaElement;
    const ragSystemPromptAfterSwitch = within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByLabelText(
      "System override",
    ) as HTMLTextAreaElement;

    expect(ragPromptAfterSwitch.value).toBe("Новый вопрос по материалам");
    expect(ragSystemPromptAfterSwitch.value).toBe("Только по контексту");
    expect(within(screen.getByRole("tabpanel", { name: "RAG чат" })).queryByRole("checkbox")).toBeNull();

    await user.click(screen.getByRole("tab", { name: "Direct чат" }));

    const directPromptAfterSwitch = within(screen.getByRole("tabpanel", { name: "Direct чат" })).getByLabelText(
      "User prompt",
    ) as HTMLTextAreaElement;
    const directSystemPromptAfterSwitch = within(
      screen.getByRole("tabpanel", { name: "Direct чат" }),
    ).getByLabelText("System prompt") as HTMLTextAreaElement;

    expect(directPromptAfterSwitch.value).toBe("Прямой запрос для проверки");
    expect(directSystemPromptAfterSwitch.value).toContain("Кратко");
    expect(within(screen.getByRole("tabpanel", { name: "Direct чат" })).queryByRole("checkbox")).toBeNull();
  });

  it("keeps the app interactive when backend returns a legacy upload policy payload", async () => {
    const user = userEvent.setup();
    currentMaterialUploadPolicyResponse = legacyMaterialUploadPolicyResponse;

    render(<App />);

    expect(await screen.findByRole("tab", { name: "Обзор" })).toBeTruthy();

    await user.click(screen.getByRole("tab", { name: "Материалы" }));

    expect(screen.getByRole("heading", { name: "Материалы для RAG" })).toBeTruthy();
    expect(screen.getByText(/устаревший upload policy/i)).toBeTruthy();
    expect(screen.queryByText("Prompt Snippets")).toBeNull();
  });
});
