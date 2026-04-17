import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { HealthResponse, MaterialSummary } from "./types";

const healthResponse = {
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  directStatus: "UP" as const,
  ragStatus: "UP" as const,
  llmStatus: "UP" as const,
  embeddingStatus: "UP" as const,
};

const modelsResponse = [{ name: "qwen2.5:7b" }, { name: "qwen2.5:3b" }];

const materialsResponse: MaterialSummary[] = [
  {
    id: "material-1",
    title: "Pricing note",
    sourceType: "text",
    originalFileName: null,
    status: "READY" as const,
    createdAt: "2026-04-16T10:00:00Z",
    contentLength: 42,
    preview: "Тариф Премиум стоит 12000 тенге в месяц.",
  },
];

const buildMaterial = (overrides: Partial<MaterialSummary> = {}): MaterialSummary => ({
  id: "material-1",
  title: "Pricing note",
  sourceType: "text",
  originalFileName: null,
  status: "READY",
  versionState: "ACTIVE",
  createdAt: "2026-04-16T10:00:00Z",
  contentLength: 42,
  preview: "Тариф Премиум стоит 12000 тенге в месяц.",
  ...overrides,
});

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
    createdAt: "2026-04-16T10:00:00Z",
    preview: "Отвечай кратко и по делу.",
  },
  {
    id: "instruction-2",
    title: "Факты только из контекста",
    category: "safety",
    createdAt: "2026-04-16T10:01:00Z",
    preview: "Не выходи за пределы доступного контекста.",
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
  let currentHealthResponse: HealthResponse;
  let currentMaterialUploadPolicyResponse:
    | typeof materialUploadPolicyResponse
    | typeof legacyMaterialUploadPolicyResponse;
  let currentMaterialsResponse: MaterialSummary[];
  let chatRequests: Array<{
    mode: "direct" | "rag";
    model: string;
    prompt: string;
    instructionIds: string[];
    systemPrompt?: string;
  }>;

  beforeEach(() => {
    vi.useRealTimers();
    currentHealthResponse = healthResponse;
    currentMaterialUploadPolicyResponse = materialUploadPolicyResponse;
    currentMaterialsResponse = materialsResponse;
    chatRequests = [];

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = getUrl(input);

      if (url.endsWith("/api/health")) {
        return jsonResponse(currentHealthResponse);
      }

      if (url.endsWith("/api/models")) {
        return jsonResponse(modelsResponse);
      }

      if (url.endsWith("/api/materials/policy")) {
        return jsonResponse(currentMaterialUploadPolicyResponse);
      }

      if (url.endsWith("/api/materials")) {
        return jsonResponse(currentMaterialsResponse);
      }

      if (url.endsWith("/api/instructions")) {
        return jsonResponse(instructionsResponse);
      }

      if (url.endsWith("/api/chat")) {
        const request = init?.body
          ? (JSON.parse(String(init.body)) as {
              mode: "direct" | "rag";
              model: string;
              prompt: string;
              instructionIds: string[];
              systemPrompt?: string;
            })
          : {
              mode: "direct" as const,
              model: "qwen2.5:7b",
              prompt: "",
              instructionIds: [],
            };

        chatRequests.push(request);

        return jsonResponse({
          mode: request.mode,
          model: request.model,
          prompt: request.prompt,
          answer: "ok",
          createdAt: "2026-04-16T10:00:00Z",
          promptTokens: 1,
          completionTokens: 1,
          totalTokens: 2,
          appliedInstructions: request.instructionIds
            .map((instructionId) =>
              instructionsResponse.find((instruction) => instruction.id === instructionId),
            )
            .filter((instruction) => instruction !== undefined),
          sources: [],
        });
      }

      throw new Error(`Unexpected request: ${url}`);
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const assertRagPresentationAcrossTabs = async ({
    user,
    overviewHeadline,
    overviewMessage,
    materialsMessage,
    chatHelperText,
    isSubmitDisabled,
    badgeLabel,
  }: {
    user: ReturnType<typeof userEvent.setup>;
    overviewHeadline: string;
    overviewMessage: string;
    materialsMessage: string;
    chatHelperText: string;
    isSubmitDisabled: boolean;
    badgeLabel: string;
  }) => {
    await user.click(screen.getByRole("tab", { name: "Обзор" }));
    const overviewPanel = screen.getByRole("tabpanel", { name: "Обзор" });
    expect(within(overviewPanel).getByText(overviewHeadline)).toBeTruthy();
    expect(within(overviewPanel).getByText(overviewMessage)).toBeTruthy();

    await user.click(screen.getByRole("tab", { name: "Материалы" }));
    const materialsPanel = screen.getByRole("tabpanel", { name: "Материалы" });
    expect(within(materialsPanel).getByText(materialsMessage)).toBeTruthy();
    expect(within(materialsPanel).getByText(badgeLabel)).toBeTruthy();

    await user.click(screen.getByRole("tab", { name: "RAG чат" }));
    const ragPanel = screen.getByRole("tabpanel", { name: "RAG чат" });
    const ragSubmitButton = within(ragPanel).getByRole("button", {
      name: "Спросить по материалам",
    }) as HTMLButtonElement;

    expect(within(ragPanel).getByText(chatHelperText)).toBeTruthy();
    expect(ragSubmitButton.disabled).toBe(isSubmitDisabled);
  };

  it("opens on overview and shows explicit instruction selectors in both chat tabs", async () => {
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
    expect(
      within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByRole("checkbox", {
        name: "Выбрать инструкцию Базовая роль ассистента",
      }),
    ).toBeTruthy();

    await user.click(directTab);
    expect(directPanel.hidden).toBe(false);
    expect(screen.getByRole("heading", { name: "Прямой запрос к модели" })).toBeTruthy();
    expect(
      within(screen.getByRole("tabpanel", { name: "Direct чат" })).getByRole("checkbox", {
        name: "Выбрать инструкцию Факты только из контекста",
      }),
    ).toBeTruthy();
  });

  it("keeps rag and direct form state plus selected instructions isolated when switching tabs", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("tab", { name: "RAG чат" }));

    const ragPanel = screen.getByRole("tabpanel", { name: "RAG чат" });
    const ragPrompt = within(ragPanel).getByLabelText("Вопрос") as HTMLTextAreaElement;
    const ragSystemPrompt = within(ragPanel).getByLabelText("System override") as HTMLTextAreaElement;
    const ragContextCheckbox = within(ragPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    }) as HTMLInputElement;
    const ragAssistantCheckbox = within(ragPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    }) as HTMLInputElement;

    await user.clear(ragPrompt);
    await user.type(ragPrompt, "Новый вопрос по материалам");
    await user.type(ragSystemPrompt, "Только по контексту");
    await user.click(ragContextCheckbox);
    await user.click(ragAssistantCheckbox);

    expect(ragPrompt.value).toBe("Новый вопрос по материалам");
    expect(ragSystemPrompt.value).toBe("Только по контексту");
    expect(ragContextCheckbox.checked).toBe(true);
    expect(ragAssistantCheckbox.checked).toBe(true);
    expect(within(ragPanel).getAllByRole("listitem")[0]?.textContent).toContain(
      "Факты только из контекста",
    );
    expect(within(ragPanel).getAllByRole("listitem")[1]?.textContent).toContain(
      "Базовая роль ассистента",
    );

    await user.click(screen.getByRole("tab", { name: "Direct чат" }));

    const directPanel = screen.getByRole("tabpanel", { name: "Direct чат" });
    const directPrompt = within(directPanel).getByLabelText("User prompt") as HTMLTextAreaElement;
    const directSystemPrompt = within(directPanel).getByLabelText("System prompt") as HTMLTextAreaElement;
    const directAssistantCheckbox = within(directPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    }) as HTMLInputElement;
    const directContextCheckbox = within(directPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    }) as HTMLInputElement;

    await user.clear(directPrompt);
    await user.type(directPrompt, "Прямой запрос для проверки");
    await user.type(directSystemPrompt, "Кратко");
    await user.click(directAssistantCheckbox);

    expect(directPrompt.value).toBe("Прямой запрос для проверки");
    expect(directSystemPrompt.value).toContain("Кратко");
    expect(directAssistantCheckbox.checked).toBe(true);
    expect(directContextCheckbox.checked).toBe(false);

    await user.click(screen.getByRole("tab", { name: "Обзор" }));
    await user.click(screen.getByRole("tab", { name: "RAG чат" }));

    const ragPromptAfterSwitch = within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByLabelText(
      "Вопрос",
    ) as HTMLTextAreaElement;
    const ragSystemPromptAfterSwitch = within(
      screen.getByRole("tabpanel", { name: "RAG чат" }),
    ).getByLabelText("System override") as HTMLTextAreaElement;
    const ragContextCheckboxAfterSwitch = within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByRole(
      "checkbox",
      {
        name: "Выбрать инструкцию Факты только из контекста",
      },
    ) as HTMLInputElement;
    const ragAssistantCheckboxAfterSwitch = within(screen.getByRole("tabpanel", { name: "RAG чат" })).getByRole(
      "checkbox",
      {
        name: "Выбрать инструкцию Базовая роль ассистента",
      },
    ) as HTMLInputElement;

    expect(ragPromptAfterSwitch.value).toBe("Новый вопрос по материалам");
    expect(ragSystemPromptAfterSwitch.value).toBe("Только по контексту");
    expect(ragContextCheckboxAfterSwitch.checked).toBe(true);
    expect(ragAssistantCheckboxAfterSwitch.checked).toBe(true);

    await user.click(screen.getByRole("tab", { name: "Direct чат" }));

    const directPromptAfterSwitch = within(screen.getByRole("tabpanel", { name: "Direct чат" })).getByLabelText(
      "User prompt",
    ) as HTMLTextAreaElement;
    const directSystemPromptAfterSwitch = within(
      screen.getByRole("tabpanel", { name: "Direct чат" }),
    ).getByLabelText("System prompt") as HTMLTextAreaElement;
    const directAssistantCheckboxAfterSwitch = within(
      screen.getByRole("tabpanel", { name: "Direct чат" }),
    ).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    }) as HTMLInputElement;
    const directContextCheckboxAfterSwitch = within(screen.getByRole("tabpanel", { name: "Direct чат" })).getByRole(
      "checkbox",
      {
        name: "Выбрать инструкцию Факты только из контекста",
      },
    ) as HTMLInputElement;

    expect(directPromptAfterSwitch.value).toBe("Прямой запрос для проверки");
    expect(directSystemPromptAfterSwitch.value).toContain("Кратко");
    expect(directAssistantCheckboxAfterSwitch.checked).toBe(true);
    expect(directContextCheckboxAfterSwitch.checked).toBe(false);
  });

  it("submits selected instructions and renders applied instructions in the response", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("tab", { name: "RAG чат" }));

    const ragPanel = screen.getByRole("tabpanel", { name: "RAG чат" });
    await user.click(
      within(ragPanel).getByRole("checkbox", {
        name: "Выбрать инструкцию Факты только из контекста",
      }),
    );
    await user.click(
      within(ragPanel).getByRole("checkbox", {
        name: "Выбрать инструкцию Базовая роль ассистента",
      }),
    );
    await user.click(within(ragPanel).getByRole("button", { name: "Спросить по материалам" }));

    await waitFor(() => {
      expect(chatRequests).toHaveLength(1);
    });

    expect(chatRequests[0]?.instructionIds).toEqual(["instruction-2", "instruction-1"]);
    const appliedInstructionsCard = screen
      .getByRole("heading", { name: "Применённые инструкции" })
      .closest("article") as HTMLElement;

    expect(appliedInstructionsCard).toBeTruthy();
    expect(within(appliedInstructionsCard).getAllByRole("listitem")[0]?.textContent).toContain(
      "Факты только из контекста",
    );
    expect(within(appliedInstructionsCard).getAllByRole("listitem")[1]?.textContent).toContain(
      "Базовая роль ассистента",
    );
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

  it("renders the ready presentation consistently across overview, materials and RAG chat", async () => {
    const user = userEvent.setup();

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "1/1",
      overviewMessage: "Активные материалы и readiness считаются по одной модели состояния.",
      materialsMessage:
        "RAG использует только активные READY и PARTIAL_READY версии, а historical остаются в каталоге для аудита.",
      chatHelperText:
        "Вопрос уйдёт в qwen2.5:7b с локально подобранным контекстом из активных материалов.",
      isSubmitDisabled: false,
      badgeLabel: "1 active / 1 total",
    });
  });

  it("renders the empty presentation consistently across overview, materials and RAG chat", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = [];

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "Пустая база",
      overviewMessage: "В knowledge base пока нет материалов.",
      materialsMessage:
        "В каталоге пока нет материалов. Добавь хотя бы один активный материал, чтобы RAG мог работать по контексту.",
      chatHelperText: "Сначала добавь материалы, иначе RAG-режим не на чем grounded.",
      isSubmitDisabled: true,
      badgeLabel: "0 active / 0 total",
    });
  });

  it("renders the indexing presentation consistently across overview, materials and RAG chat", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = [
      buildMaterial({
        status: "IN_PROGRESS",
        preview: "Индекс ещё строится.",
      }),
    ];

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "0/1",
      overviewMessage: "Активная версия уже принята, а индекс ещё догоняет её до READY или PARTIAL_READY.",
      materialsMessage:
        "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
      chatHelperText:
        "Активная версия уже принята, но индекс ещё собирается. Как только появится хотя бы один READY- или PARTIAL_READY-материал, RAG сможет отвечать по контексту.",
      isSubmitDisabled: false,
      badgeLabel: "1 active / 1 total",
    });
  });

  it("renders the degraded presentation consistently across overview, materials and RAG chat", async () => {
    const user = userEvent.setup();
    currentHealthResponse = {
      ...healthResponse,
      ragStatus: "DOWN",
      embeddingReasonMessage: "Embedding runtime is unavailable.",
    };

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "Readiness degraded",
      overviewMessage: "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
      materialsMessage: "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
      chatHelperText: "RAG backend сейчас деградирован: Embedding runtime is unavailable.",
      isSubmitDisabled: false,
      badgeLabel: "1 active / 1 total",
    });
  });

  it("degrades RAG UX when only superseded materials remain in the catalog", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = [
      buildMaterial({
        id: "material-archived-1",
        title: "Archived pricing note",
        status: "READY",
        versionState: "SUPERSEDED",
        preview: "Историческая версия тарифа.",
      }),
    ];

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "Только история версий",
      overviewMessage: "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
      materialsMessage:
        "В каталоге сейчас только исторические версии. Они видны ниже для аудита, но исключены из retrieval и readiness.",
      chatHelperText:
        "В каталоге сейчас только исторические версии. Они видны для аудита, но исключены из retrieval. Добавь новую активную версию, чтобы RAG снова стал доступен.",
      isSubmitDisabled: true,
      badgeLabel: "0 active / 1 total",
    });
  });

  it("transitions to historical-only readiness after polling without remounting the app", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = [
      buildMaterial({
        id: "material-active-1",
        status: "PENDING",
        versionState: "ACTIVE",
        preview: "Активная версия ещё индексируется.",
      }),
    ];

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "0/1",
      overviewMessage: "Активная версия уже принята, а индекс ещё догоняет её до READY или PARTIAL_READY.",
      materialsMessage:
        "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
      chatHelperText:
        "Активная версия уже принята, но индекс ещё собирается. Как только появится хотя бы один READY- или PARTIAL_READY-материал, RAG сможет отвечать по контексту.",
      isSubmitDisabled: false,
      badgeLabel: "1 active / 1 total",
    });

    currentMaterialsResponse = [
      buildMaterial({
        id: "material-archived-1",
        status: "READY",
        versionState: "SUPERSEDED",
        preview: "Историческая версия тарифа.",
      }),
    ];

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
      await Promise.resolve();
    });

    await assertRagPresentationAcrossTabs({
      user,
      overviewHeadline: "Только история версий",
      overviewMessage: "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
      materialsMessage:
        "В каталоге сейчас только исторические версии. Они видны ниже для аудита, но исключены из retrieval и readiness.",
      chatHelperText:
        "В каталоге сейчас только исторические версии. Они видны для аудита, но исключены из retrieval. Добавь новую активную версию, чтобы RAG снова стал доступен.",
      isSubmitDisabled: true,
      badgeLabel: "0 active / 1 total",
    });
  });
});
