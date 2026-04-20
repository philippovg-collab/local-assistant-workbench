import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { ChatExecutionResponse, HealthResponse, MaterialListResponse, MaterialSummary } from "./types";
import { buildChatExecutionResponse, buildMaterialListResponse, buildMaterialSummary } from "./testBuilders";
import {
  EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  EMPTY_RETRIEVAL_TRACE,
} from "./utils/workbenchPresentation";

const buildHealthResponse = (overrides: Partial<HealthResponse> = {}): HealthResponse => ({
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  directStatus: "UP",
  ragStatus: "UP",
  llmStatus: "UP",
  embeddingStatus: "UP",
  knowledgeStatus: "READY",
  materialCount: 1,
  activeMaterialCount: 1,
  historicalMaterialCount: 0,
  readyMaterialCount: 1,
  indexingPendingCount: 0,
  indexingInProgressCount: 0,
  indexingFailedCount: 0,
  ...overrides,
});

const modelsResponse = [{ name: "qwen2.5:7b" }, { name: "qwen2.5:3b" }];

const materialsResponse: MaterialSummary[] = [
  buildMaterialSummary({
    id: "material-1",
    title: "Pricing note",
    sourceType: "text",
    originalFileName: null,
    status: "READY" as const,
    createdAt: "2026-04-16T10:00:00Z",
    contentLength: 42,
    preview: "Тариф Премиум стоит 12000 тенге в месяц.",
  }),
];

const buildMaterial = (overrides: Partial<MaterialSummary> = {}): MaterialSummary => ({
  ...buildMaterialSummary({
    id: "material-1",
    title: "Pricing note",
    sourceType: "text",
    originalFileName: null,
    status: "READY",
    versionState: "ACTIVE",
    createdAt: "2026-04-16T10:00:00Z",
    contentLength: 42,
    preview: "Тариф Премиум стоит 12000 тенге в месяц.",
  }),
  ...overrides,
});

const materialUploadPolicyResponse = {
  maxUploadBytes: 8_388_608,
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
  maxUploadBytes: 8_388_608,
  acceptedExtensions: ["txt", "pdf"],
  acceptedMimeHints: ["text/plain", "application/pdf"],
  richDocumentSupport: true,
};

const instructionsResponse = [
  {
    id: "instruction-1",
    title: "Базовая роль ассистента",
    category: "system",
    scopeLevel: "chat_scenario" as const,
    revision: 1,
    active: true,
    createdAt: "2026-04-16T10:00:00Z",
    preview: "Отвечай кратко и по делу.",
  },
  {
    id: "instruction-2",
    title: "Факты только из контекста",
    category: "safety",
    scopeLevel: "chat_scenario" as const,
    revision: 1,
    active: true,
    createdAt: "2026-04-16T10:01:00Z",
    preview: "Не выходи за пределы доступного контекста.",
  },
];

const knowledgePresetsResponse = [
  {
    id: "preset-contracts",
    name: "Договоры",
    description: "Корпус договоров",
    revision: 1,
    active: true,
    createdAt: "2026-04-16T10:00:00Z",
  },
];

const instructionDetailResponse = {
  id: "instruction-1",
  title: "Базовая роль ассистента",
  category: "system" as const,
  content: "Отвечай кратко и структурированно.",
  scopeLevel: "chat_scenario" as const,
  scopeTargetId: null,
  revision: 2,
  active: true,
  createdAt: "2026-04-16T10:00:00Z",
  updatedAt: "2026-04-16T10:10:00Z",
};

const instructionRevisionsResponse = [
  {
    instructionId: "instruction-1",
    revision: 1,
    title: "Базовая роль ассистента",
    category: "system" as const,
    content: "Отвечай кратко.",
    scopeLevel: "chat_scenario" as const,
    scopeTargetId: null,
    active: true,
    restoredFromRevision: null,
    createdAt: "2026-04-16T10:00:00Z",
    updatedAt: "2026-04-16T10:00:00Z",
  },
];

const instructionDiffResponse = {
  instructionId: "instruction-1",
  fromRevision: 2,
  toRevision: 1,
  changes: [
    {
      field: "content",
      fromValue: "Отвечай кратко и структурированно.",
      toValue: "Отвечай кратко.",
    },
  ],
};

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

const getPanel = (panelId: "overview" | "materials" | "instructions" | "rag" | "direct") =>
  document.querySelector(`#panel-${panelId}`) as HTMLElement;

const openSection = async (user: ReturnType<typeof userEvent.setup>, sectionName: RegExp) => {
  await user.click(screen.getByRole("button", { name: sectionName }));
};

type CapturedChatRequest = {
  mode: "direct" | "rag";
  model: string;
  prompt: string;
  instructionIds: string[];
  scenarioInstructionIds?: string[];
  answerMode?: string;
  temporaryInstruction?: string;
};

const parseChatRequest = (init?: RequestInit): CapturedChatRequest => {
  if (!init?.body) {
    return {
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "",
      instructionIds: [],
    };
  }

  return JSON.parse(String(init.body)) as CapturedChatRequest;
};

const buildChatResponseForRequest = (request: CapturedChatRequest): ChatExecutionResponse =>
  buildChatExecutionResponse({
    mode: request.mode,
    model: request.model,
    prompt: request.prompt,
    answer: "ok",
    answerModeApplied: (request.answerMode as "brief" | "strict_sources_only") ?? "brief",
    appliedInstructions: request.instructionIds
      .map((instructionId) =>
        instructionsResponse.find((instruction) => instruction.id === instructionId),
      )
      .filter((instruction) => instruction !== undefined)
      .map((instruction) => ({
        id: instruction.id,
        title: instruction.title,
        category: instruction.category as "system" | "safety",
        scopeLevel: instruction.scopeLevel,
        revision: instruction.revision,
      })),
    instructionTrace: request.instructionIds
      .map((instructionId) =>
        instructionsResponse.find((instruction) => instruction.id === instructionId),
      )
      .filter((instruction) => instruction !== undefined)
      .map((instruction) => ({
        instructionId: instruction.id,
        title: instruction.title,
        category: instruction.category as "system" | "safety",
        scopeLevel: instruction.scopeLevel,
        scopeTargetId: null,
        revision: instruction.revision,
        active: true,
        temporary: false,
        contentPreview: instruction.preview,
      })),
    knowledgeScopeResolved: EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
    retrievalTrace: {
      ...EMPTY_RETRIEVAL_TRACE,
      supportVerdict: request.mode === "rag" ? "sufficient" : "none",
    },
    sources: [],
    auditRunId: "chat-run-1",
  });

describe("App", () => {
  let currentHealthResponse: HealthResponse;
  let currentMaterialUploadPolicyResponse:
    | typeof materialUploadPolicyResponse
    | typeof legacyMaterialUploadPolicyResponse;
  let currentMaterialsResponse: MaterialListResponse;
  let chatRequests: CapturedChatRequest[];
  let chatRunSequence: number;
  let chatRunResponses: Record<
    string,
    {
      request: CapturedChatRequest;
      response: ChatExecutionResponse;
    }
  >;

  beforeEach(() => {
    vi.useRealTimers();
    currentHealthResponse = buildHealthResponse();
    currentMaterialUploadPolicyResponse = materialUploadPolicyResponse;
    currentMaterialsResponse = buildMaterialListResponse(materialsResponse);
    chatRequests = [];
    chatRunSequence = 0;
    chatRunResponses = {};

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = getUrl(input);
      const pathname = new URL(url).pathname;

      if (url.endsWith("/api/auth/session")) {
        return jsonResponse({
          authenticated: true,
          username: "admin",
          roles: ["ROLE_ADMIN"],
          csrfHeaderName: "X-CSRF-TOKEN",
          csrfToken: "csrf-test-token",
        });
      }

      if (url.endsWith("/api/health")) {
        return jsonResponse(currentHealthResponse);
      }

      if (url.endsWith("/api/models")) {
        return jsonResponse(modelsResponse);
      }

      if (url.endsWith("/api/materials/policy")) {
        return jsonResponse(currentMaterialUploadPolicyResponse);
      }

      if (pathname === "/api/materials") {
        return jsonResponse(currentMaterialsResponse);
      }

      if (url.endsWith("/api/instructions")) {
        return jsonResponse(instructionsResponse);
      }

      if (url.endsWith("/api/instructions/instruction-1/revisions")) {
        return jsonResponse(instructionRevisionsResponse);
      }

      if (url.includes("/api/instructions/instruction-1/diff?")) {
        return jsonResponse(instructionDiffResponse);
      }

      if (url.endsWith("/api/instructions/instruction-1")) {
        return jsonResponse(instructionDetailResponse);
      }

      if (url.endsWith("/api/knowledge-presets")) {
        return jsonResponse(knowledgePresetsResponse);
      }

      if (pathname === "/api/chat-runs" && init?.method === "POST") {
        const request = parseChatRequest(init);
        const runId = `chat-run-${++chatRunSequence}`;
        const response = {
          ...buildChatResponseForRequest(request),
          auditRunId: runId,
        };

        chatRequests.push(request);
        chatRunResponses[runId] = { request, response };

        return new Response(
          JSON.stringify({
            id: runId,
            status: "COMPLETED",
            createdAt: response.createdAt,
            traceUrl: `/api/chat-runs/${runId}/trace`,
            resultUrl: `/api/chat-runs/${runId}/result`,
          }),
          {
            status: 202,
            headers: {
              "Content-Type": "application/json",
            },
          },
        );
      }

      if (pathname === "/api/chat-runs") {
        return jsonResponse(
          Object.entries(chatRunResponses).map(([id, { request, response }]) => ({
            id,
            mode: request.mode,
            model: request.model,
            answerMode: response.answerModeApplied,
            promptPreview: request.prompt,
            answerPreview: response.answer,
            createdAt: response.createdAt,
            status: "COMPLETED",
            failureStage: null,
            failureCode: null,
            failedAt: null,
            latencyMsTotal: null,
          })),
        );
      }

      const chatRunTraceMatch = pathname.match(/^\/api\/chat-runs\/([^/]+)\/trace$/);
      if (chatRunTraceMatch) {
        const runId = chatRunTraceMatch[1] ?? "";
        const storedRun = chatRunResponses[runId];
        if (!storedRun) {
          throw new Error(`Unexpected chat run trace request: ${url}`);
        }

        return jsonResponse({
          id: runId,
          mode: storedRun.request.mode,
          status: "COMPLETED",
          requestedModel: storedRun.request.model,
          resolvedModel: storedRun.response.model,
          requestedAnswerMode: storedRun.request.answerMode ?? null,
          appliedAnswerMode: storedRun.response.answerModeApplied ?? null,
          contextStatus: storedRun.request.mode === "rag" ? "ready" : "no-context",
          createdAt: storedRun.response.createdAt,
          completedAt: storedRun.response.createdAt,
          failedAt: null,
          latencyMsTotal: null,
          failureStage: null,
          failureCode: null,
          failureMessage: null,
          requestSnapshot: {
            prompt: storedRun.request.prompt,
          },
          promptSnapshot: {
            messages: [],
            instructionTrace: storedRun.response.instructionTrace,
            knowledgeScopeResolved: storedRun.response.knowledgeScopeResolved,
            groundingRulesApplied: storedRun.request.mode === "rag",
          },
          retrievalSummary: {
            retrievalStatus: storedRun.request.mode === "rag" ? "DONE" : "NOT_APPLICABLE",
            trace: storedRun.response.retrievalTrace,
            debug: storedRun.response.retrievalDebug,
          },
          llmCalls: [],
          output: {
            finalUserAnswer: storedRun.response.answer,
            sources: storedRun.response.sources,
          },
          events: [],
        });
      }

      const chatRunResultMatch = pathname.match(/^\/api\/chat-runs\/([^/]+)\/result$/);
      if (chatRunResultMatch) {
        const runId = chatRunResultMatch[1] ?? "";
        const storedRun = chatRunResponses[runId];
        if (!storedRun) {
          throw new Error(`Unexpected chat run result request: ${url}`);
        }

        return jsonResponse(storedRun.response);
      }

      const chatRunDetailMatch = pathname.match(/^\/api\/chat-runs\/([^/]+)$/);
      if (chatRunDetailMatch) {
        const runId = chatRunDetailMatch[1] ?? "";
        const storedRun = chatRunResponses[runId];
        if (!storedRun) {
          throw new Error(`Unexpected chat run detail request: ${url}`);
        }

        return jsonResponse({
          id: runId,
          mode: storedRun.request.mode,
          model: storedRun.response.model,
          prompt: storedRun.request.prompt,
          answer: storedRun.response.answer,
          contextStatus: storedRun.request.mode === "rag" ? "ready" : "no-context",
          answerMode: storedRun.response.answerModeApplied ?? null,
          createdAt: storedRun.response.createdAt,
          instructionTrace: storedRun.response.instructionTrace,
          knowledgeScopeResolved: storedRun.response.knowledgeScopeResolved,
          retrievalTrace: storedRun.response.retrievalTrace,
          sources: storedRun.response.sources,
          status: "COMPLETED",
          failureStage: null,
          failureCode: null,
          failureMessage: null,
          completedAt: storedRun.response.createdAt,
          failedAt: null,
          latencyMsTotal: null,
        });
      }

      if (url.endsWith("/api/chat")) {
        const request = parseChatRequest(init);
        chatRequests.push(request);

        return jsonResponse(buildChatResponseForRequest(request));
      }

      throw new Error(`Unexpected request: ${url}`);
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const assertRagPresentationAcrossSections = async ({
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
    overviewMessage?: string | null;
    materialsMessage: string;
    chatHelperText: string;
    isSubmitDisabled: boolean;
    badgeLabel: string;
  }) => {
    await openSection(user, /dashboard/i);
    const overviewPanel = getPanel("overview");
    expect(within(overviewPanel).getByText(overviewHeadline)).toBeTruthy();
    if (overviewMessage) {
      expect(
        within(overviewPanel).getAllByText((content) => content.includes(overviewMessage)).length,
      ).toBeGreaterThan(0);
    }

    await openSection(user, /materials/i);
    const materialsPanel = getPanel("materials");
    expect(within(materialsPanel).getByText(materialsMessage)).toBeTruthy();
    expect(within(materialsPanel).getByText(badgeLabel)).toBeTruthy();

    await openSection(user, /rag studio/i);
    const ragPanel = getPanel("rag");
    const ragSubmitButton = within(ragPanel).getByRole("button", {
      name: "Спросить по материалам",
    }) as HTMLButtonElement;

    expect(within(ragPanel).getByText(chatHelperText)).toBeTruthy();
    expect(ragSubmitButton.disabled).toBe(isSubmitDisabled);
  };

  it("opens on dashboard and shows explicit instruction selectors in both studio screens", async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await screen.findByRole("button", { name: /dashboard/i });

    const overviewButton = screen.getByRole("button", { name: /dashboard/i });
    const materialsButton = screen.getByRole("button", { name: /materials/i });
    const instructionsButton = screen.getByRole("button", { name: /instructions/i });
    const ragButton = screen.getByRole("button", { name: /rag studio/i });
    const directButton = screen.getByRole("button", { name: /direct studio/i });

    expect(overviewButton.getAttribute("aria-current")).toBe("page");
    expect(materialsButton.getAttribute("aria-current")).toBeNull();
    expect(instructionsButton.getAttribute("aria-current")).toBeNull();
    expect(ragButton.getAttribute("aria-current")).toBeNull();
    expect(directButton.getAttribute("aria-current")).toBeNull();

    const overviewPanel = container.querySelector("#panel-overview") as HTMLElement;
    const materialsPanel = container.querySelector("#panel-materials") as HTMLElement;
    const instructionsPanel = container.querySelector("#panel-instructions") as HTMLElement;
    const ragPanel = container.querySelector("#panel-rag") as HTMLElement;
    const directPanel = container.querySelector("#panel-direct") as HTMLElement;

    expect(overviewPanel.hidden).toBe(false);
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(true);
    expect(ragPanel.hidden).toBe(true);
    expect(directPanel.hidden).toBe(true);
    expect(within(overviewPanel).getByText("Backend")).toBeTruthy();

    await user.click(materialsButton);
    expect(materialsButton.getAttribute("aria-current")).toBe("page");
    expect(overviewPanel.hidden).toBe(true);
    expect(materialsPanel.hidden).toBe(false);
    expect(within(materialsPanel).getByRole("heading", { name: "Материалы для RAG" })).toBeTruthy();

    await user.click(instructionsButton);
    expect(instructionsButton.getAttribute("aria-current")).toBe("page");
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(false);
    expect(within(instructionsPanel).getByText("Библиотека инструкций по уровням")).toBeTruthy();
    expect(within(instructionsPanel).getByText("Сохранённые наборы знаний")).toBeTruthy();

    await user.click(ragButton);
    expect(ragPanel.hidden).toBe(false);
    expect(within(ragPanel).getByRole("heading", { name: "Запрос по материалам" })).toBeTruthy();
    expect(within(ragPanel).getByRole("heading", { name: "Ответ по материалам" })).toBeTruthy();
    expect(
      within(ragPanel).getByRole("checkbox", {
        name: "Выбрать инструкцию Базовая роль ассистента",
      }),
    ).toBeTruthy();

    await user.click(directButton);
    expect(directPanel.hidden).toBe(false);
    expect(within(directPanel).getByRole("heading", { name: "Прямой запрос к модели" })).toBeTruthy();
    expect(
      within(directPanel).getByRole("checkbox", {
        name: "Выбрать инструкцию Факты только из контекста",
      }),
    ).toBeTruthy();
  });

  it("keeps rag and direct form state plus selected instructions isolated when switching sections", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /rag studio/i);

    const ragPanel = getPanel("rag");
    const ragPrompt = within(ragPanel).getByLabelText("Вопрос") as HTMLTextAreaElement;
    const ragTemporaryInstruction = within(ragPanel).getByLabelText("Временная инструкция на этот запрос") as HTMLTextAreaElement;
    const ragContextCheckbox = within(ragPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    });
    const ragAssistantCheckbox = within(ragPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    });

    await user.clear(ragPrompt);
    await user.type(ragPrompt, "Новый вопрос по материалам");
    await user.type(ragTemporaryInstruction, "Только по контексту");
    await user.click(ragContextCheckbox);
    await user.click(ragAssistantCheckbox);

    expect(ragPrompt.value).toBe("Новый вопрос по материалам");
    expect(ragTemporaryInstruction.value).toBe("Только по контексту");
    expect(ragContextCheckbox.getAttribute("aria-checked")).toBe("true");
    expect(ragAssistantCheckbox.getAttribute("aria-checked")).toBe("true");
    expect(within(ragPanel).getAllByRole("listitem")[0]?.textContent).toContain(
      "Факты только из контекста",
    );
    expect(within(ragPanel).getAllByRole("listitem")[1]?.textContent).toContain(
      "Базовая роль ассистента",
    );

    await openSection(user, /direct studio/i);

    const directPanel = getPanel("direct");
    const directPrompt = within(directPanel).getByLabelText("User prompt") as HTMLTextAreaElement;
    const directTemporaryInstruction = within(directPanel).getByLabelText("Временная инструкция на этот запрос") as HTMLTextAreaElement;
    const directAssistantCheckbox = within(directPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    });
    const directContextCheckbox = within(directPanel).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    });

    await user.clear(directPrompt);
    await user.type(directPrompt, "Прямой запрос для проверки");
    await user.type(directTemporaryInstruction, "Кратко");
    await user.click(directAssistantCheckbox);

    expect(directPrompt.value).toBe("Прямой запрос для проверки");
    expect(directTemporaryInstruction.value).toContain("Кратко");
    expect(directAssistantCheckbox.getAttribute("aria-checked")).toBe("true");
    expect(directContextCheckbox.getAttribute("aria-checked")).toBe("false");

    await openSection(user, /dashboard/i);
    await openSection(user, /rag studio/i);

    const ragPromptAfterSwitch = within(getPanel("rag")).getByLabelText("Вопрос") as HTMLTextAreaElement;
    const ragTemporaryInstructionAfterSwitch = within(getPanel("rag")).getByLabelText(
      "Временная инструкция на этот запрос",
    ) as HTMLTextAreaElement;
    const ragContextCheckboxAfterSwitch = within(getPanel("rag")).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    });
    const ragAssistantCheckboxAfterSwitch = within(getPanel("rag")).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    });

    expect(ragPromptAfterSwitch.value).toBe("Новый вопрос по материалам");
    expect(ragTemporaryInstructionAfterSwitch.value).toBe("Только по контексту");
    expect(ragContextCheckboxAfterSwitch.getAttribute("aria-checked")).toBe("true");
    expect(ragAssistantCheckboxAfterSwitch.getAttribute("aria-checked")).toBe("true");

    await openSection(user, /direct studio/i);

    const directPromptAfterSwitch = within(getPanel("direct")).getByLabelText(
      "User prompt",
    ) as HTMLTextAreaElement;
    const directTemporaryInstructionAfterSwitch = within(getPanel("direct")).getByLabelText(
      "Временная инструкция на этот запрос",
    ) as HTMLTextAreaElement;
    const directAssistantCheckboxAfterSwitch = within(getPanel("direct")).getByRole("checkbox", {
      name: "Выбрать инструкцию Базовая роль ассистента",
    });
    const directContextCheckboxAfterSwitch = within(getPanel("direct")).getByRole("checkbox", {
      name: "Выбрать инструкцию Факты только из контекста",
    });

    expect(directPromptAfterSwitch.value).toBe("Прямой запрос для проверки");
    expect(directTemporaryInstructionAfterSwitch.value).toContain("Кратко");
    expect(directAssistantCheckboxAfterSwitch.getAttribute("aria-checked")).toBe("true");
    expect(directContextCheckboxAfterSwitch.getAttribute("aria-checked")).toBe("false");
  });

  it("submits selected instructions and renders applied instructions in the response", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /rag studio/i);

    const ragPanel = getPanel("rag");
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
    const appliedInstructionsCard = within(ragPanel)
      .getByRole("heading", { name: "Применённые инструкции" })
      .closest("article") as HTMLElement;

    expect(appliedInstructionsCard).toBeTruthy();
    expect(within(appliedInstructionsCard).getAllByRole("listitem")[0]?.textContent).toContain(
      "Факты только из контекста",
    );
    expect(within(appliedInstructionsCard).getAllByRole("listitem")[1]?.textContent).toContain(
      "Базовая роль ассистента",
    );
    expect(within(ragPanel).getAllByText("достаточная опора").length).toBeGreaterThan(0);
  });

  it("loads and shows revision diff for an inspected instruction", async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /instructions/i);

    const instructionsPanel = getPanel("instructions");
    await user.click(within(instructionsPanel).getAllByRole("button", { name: "Открыть" })[0]);

    await waitFor(() => {
      expect(within(instructionsPanel).getByText("История ревизий")).toBeTruthy();
    });

    await user.click(within(instructionsPanel).getByRole("button", { name: "Сравнить" }));

    await waitFor(() => {
      expect(within(instructionsPanel).getByText(/Сравнение rev 2/)).toBeTruthy();
    });

    expect(within(instructionsPanel).getByText("Текст")).toBeTruthy();
    expect(within(instructionsPanel).getByText("Отвечай кратко и структурированно.")).toBeTruthy();
  });

  it("keeps the app interactive when backend returns a legacy upload policy payload", async () => {
    const user = userEvent.setup();
    currentMaterialUploadPolicyResponse = legacyMaterialUploadPolicyResponse;

    render(<App />);

    expect(await screen.findByRole("button", { name: /dashboard/i })).toBeTruthy();

    await openSection(user, /materials/i);

    const materialsPanel = getPanel("materials");
    expect(within(materialsPanel).getByRole("heading", { name: "Материалы для RAG" })).toBeTruthy();
    expect(within(materialsPanel).getByText(/устаревший upload policy/i)).toBeTruthy();
  });

  it("renders the ready presentation consistently across dashboard, materials and RAG studio", async () => {
    const user = userEvent.setup();

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
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

  it("renders the empty presentation consistently across dashboard, materials and RAG studio", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = buildMaterialListResponse();
    currentHealthResponse = buildHealthResponse({
      ragStatus: "DOWN",
      knowledgeStatus: "EMPTY",
      knowledgeReasonMessage: "В knowledge base пока нет материалов.",
      materialCount: 0,
      activeMaterialCount: 0,
      historicalMaterialCount: 0,
      readyMaterialCount: 0,
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
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

  it("renders the indexing presentation consistently across dashboard, materials and RAG studio", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = buildMaterialListResponse([
      buildMaterial({
        status: "IN_PROGRESS",
        preview: "Индекс ещё строится.",
      }),
    ]);
    currentHealthResponse = buildHealthResponse({
      ragStatus: "DOWN",
      knowledgeStatus: "INDEXING",
      knowledgeReasonMessage: "Активная версия уже принята, но индекс ещё догоняет её до READY или PARTIAL_READY.",
      readyMaterialCount: 0,
      indexingPendingCount: 1,
      indexingInProgressCount: 1,
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
      user,
      overviewHeadline: "0/1",
      overviewMessage: null,
      materialsMessage:
        "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
      chatHelperText:
        "Активная версия уже принята, но индекс ещё собирается. Как только появится хотя бы один READY- или PARTIAL_READY-материал, RAG сможет отвечать по контексту.",
      isSubmitDisabled: true,
      badgeLabel: "1 active / 1 total",
    });
  });

  it("renders the degraded presentation consistently across dashboard, materials and RAG studio", async () => {
    const user = userEvent.setup();
    currentHealthResponse = buildHealthResponse({
      status: "DEGRADED",
      ragStatus: "DOWN",
      knowledgeStatus: "DEGRADED",
      directStatus: "DOWN",
      directReasonMessage: "Direct chat probe failed: timeout",
      ragDegradedReasonMessage: "Direct chat probe failed: timeout",
      embeddingReasonMessage: "Embedding runtime is unavailable.",
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
      user,
      overviewHeadline: "Readiness degraded",
      overviewMessage: "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
      materialsMessage: "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
      chatHelperText: "RAG backend сейчас деградирован: Direct chat probe failed: timeout",
      isSubmitDisabled: true,
      badgeLabel: "1 active / 1 total",
    });
  });

  it("keeps RAG ready when only the Elasticsearch shadow plane is down", async () => {
    const user = userEvent.setup();
    currentHealthResponse = buildHealthResponse({
      status: "DEGRADED",
      searchStatus: "DEGRADED",
      searchMode: "auto",
      searchProvider: "postgres",
      searchReasonCode: "search.sync_backlog_stale",
      searchReasonMessage: "Elasticsearch sync backlog is older than the safe threshold of 120 seconds.",
      searchSyncBacklog: {
        pendingCount: 2,
        inProgressCount: 1,
        failedCount: 3,
        nextRetryAt: "2026-04-17T10:15:00Z",
        oldestOutstandingAt: "2026-04-17T10:01:00Z",
        lastSuccessfulSyncAt: "2026-04-17T10:05:00Z",
      },
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /dashboard/i);
    const overviewPanel = getPanel("overview");
    expect(within(overviewPanel).getByText("DEGRADED")).toBeTruthy();
    expect(within(overviewPanel).getByText("PostgreSQL fallback")).toBeTruthy();
    expect(within(overviewPanel).getByText("Fallback активен")).toBeTruthy();
    expect(within(overviewPanel).getByText("Elasticsearch sync: DEGRADED")).toBeTruthy();
    expect(
      within(overviewPanel).getAllByText((content) =>
        content.includes("Elasticsearch sync backlog is older than the safe threshold of 120 seconds."),
      ).length,
    ).toBeGreaterThan(0);

    await openSection(user, /rag studio/i);
    const ragPanel = getPanel("rag");
    const ragSubmitButton = within(ragPanel).getByRole("button", {
      name: "Спросить по материалам",
    }) as HTMLButtonElement;

    expect(ragSubmitButton.disabled).toBe(false);
    expect(within(ragPanel).getByText(/локально подобранным контекстом/i)).toBeTruthy();
  });

  it("blocks direct submit when backend says direct chat path is not ready", async () => {
    const user = userEvent.setup();
    currentHealthResponse = buildHealthResponse({
      status: "DEGRADED",
      directStatus: "DOWN",
      directReasonMessage: "Direct chat probe failed: timeout",
      llmStatus: "UP",
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /direct studio/i);
    const directPanel = getPanel("direct");
    const directSubmitButton = within(directPanel).getByRole("button", {
      name: "Отправить напрямую",
    }) as HTMLButtonElement;

    expect(within(directPanel).getByText("Direct chat probe failed: timeout")).toBeTruthy();
    expect(directSubmitButton.disabled).toBe(true);
  });

  it("blocks RAG submit when health payload is missing knowledgeStatus", async () => {
    const user = userEvent.setup();
    currentHealthResponse = buildHealthResponse({
      ragStatus: "UP",
      knowledgeStatus: undefined,
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await openSection(user, /rag studio/i);
    const ragPanel = getPanel("rag");
    const ragSubmitButton = within(ragPanel).getByRole("button", {
      name: "Спросить по материалам",
    }) as HTMLButtonElement;

    expect(ragSubmitButton.disabled).toBe(true);
    expect(
      within(ragPanel).getByText(
        "Backend health contract неполный: knowledgeStatus отсутствует, поэтому RAG readiness нельзя подтвердить.",
      ),
    ).toBeTruthy();
  });

  it("degrades RAG UX when only superseded materials remain in the catalog", async () => {
    const user = userEvent.setup();
    currentMaterialsResponse = buildMaterialListResponse([
      buildMaterial({
        id: "material-archived-1",
        title: "Archived pricing note",
        status: "READY",
        versionState: "SUPERSEDED",
        preview: "Историческая версия тарифа.",
      }),
    ]);
    currentHealthResponse = buildHealthResponse({
      ragStatus: "DOWN",
      knowledgeStatus: "HISTORICAL_ONLY",
      knowledgeReasonMessage: "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
      materialCount: 1,
      activeMaterialCount: 0,
      historicalMaterialCount: 1,
      readyMaterialCount: 0,
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
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
    currentMaterialsResponse = buildMaterialListResponse([
      buildMaterial({
        id: "material-active-1",
        status: "PENDING",
        versionState: "ACTIVE",
        preview: "Активная версия ещё индексируется.",
      }),
    ]);
    currentHealthResponse = buildHealthResponse({
      ragStatus: "DOWN",
      knowledgeStatus: "INDEXING",
      knowledgeReasonMessage: "Активная версия уже принята, но индекс ещё догоняет её до READY или PARTIAL_READY.",
      readyMaterialCount: 0,
      indexingPendingCount: 1,
      indexingInProgressCount: 0,
    });

    render(<App />);

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalled();
    });

    await assertRagPresentationAcrossSections({
      user,
      overviewHeadline: "0/1",
      overviewMessage: null,
      materialsMessage:
        "Активная версия уже создана, но индекс ещё собирается. RAG начнёт опираться на неё, когда появится READY или PARTIAL_READY.",
      chatHelperText:
        "Активная версия уже принята, но индекс ещё собирается. Как только появится хотя бы один READY- или PARTIAL_READY-материал, RAG сможет отвечать по контексту.",
      isSubmitDisabled: true,
      badgeLabel: "1 active / 1 total",
    });

    currentMaterialsResponse = buildMaterialListResponse([
      buildMaterial({
        id: "material-archived-1",
        status: "READY",
        versionState: "SUPERSEDED",
        preview: "Историческая версия тарифа.",
      }),
    ]);
    currentHealthResponse = buildHealthResponse({
      ragStatus: "DOWN",
      knowledgeStatus: "HISTORICAL_ONLY",
      knowledgeReasonMessage: "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
      materialCount: 1,
      activeMaterialCount: 0,
      historicalMaterialCount: 1,
      readyMaterialCount: 0,
      indexingPendingCount: 0,
      indexingInProgressCount: 0,
    });

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
      await Promise.resolve();
    });

    await assertRagPresentationAcrossSections({
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
