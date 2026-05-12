import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type {
  ChatExecutionResponse,
  HealthResponse,
  MaterialListResponse,
  MaterialSummary,
  RagProjectSummary,
  ReferenceProject,
  ReferenceWorkspace,
} from "./types";
import { buildChatExecutionResponse, buildMaterialListResponse, buildMaterialSummary } from "./testBuilders";
import {
  EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  EMPTY_RETRIEVAL_TRACE,
} from "./utils/workbenchPresentation";
import { ACTIVE_RAG_PROJECT_STORAGE_KEY } from "./components/workbench/workbenchConfig";

const buildHealthResponse = (overrides: Partial<HealthResponse> = {}): HealthResponse => ({
  application: "Local Assistant Workbench",
  status: "UP",
  timestamp: "2026-04-16T10:00:00Z",
  contextFeatures: {
    context: true,
    conversations: true,
    history: true,
    sticky: true,
    rewrite: true,
    summary: true,
    longTermMemory: true,
  },
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

const buildQualityLayer = (
  flags: Partial<NonNullable<HealthResponse["qualityLayer"]>["flags"]> = {},
): NonNullable<HealthResponse["qualityLayer"]> => ({
  flags: {
    metadataV1: false,
    structuredV1: false,
    metadataFiltersV1: false,
    searchApiV1: false,
    rerankerV1: false,
    queryHintsV1: false,
    ...flags,
  },
  metadataCoverage: {
    activeTotal: 1,
    activeWithEffectiveMetadata: 1,
    ratio: 1,
    documentType: {
      covered: 1,
      ratio: 1,
    },
    workspace: {
      covered: 1,
      ratio: 1,
    },
    documentStatus: {
      covered: 1,
      ratio: 1,
    },
  },
  activeBackfillCoverage: {
    activeTotal: 1,
    structuredProfileActive: 1,
    ratio: 1,
    pendingBackfill: 0,
    partialReadyActive: 0,
  },
  retrievalWindow: {
    sampleSize: 0,
    noContextRate: 0,
    hitDistributionByChunkType: {},
    rerankerDelta: {
      top1ChangedCount: 0,
      top1ImprovedCount: 0,
      appendixDemotions: 0,
      highTrustPromotions: 0,
    },
  },
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

const referenceTimestamp = "2026-04-20T10:00:00Z";

const referenceWorkspacesResponse: ReferenceWorkspace[] = [
  {
    key: "general",
    nameRu: "Общая",
    active: true,
    sortOrder: 0,
    isDefault: true,
    createdAt: referenceTimestamp,
    updatedAt: referenceTimestamp,
  },
  {
    key: "north-upgrade",
    nameRu: "Северная модернизация",
    active: true,
    sortOrder: 1,
    isDefault: false,
    createdAt: referenceTimestamp,
    updatedAt: referenceTimestamp,
  },
];

const ragProjectsResponse: RagProjectSummary[] = referenceWorkspacesResponse.map((workspace) => ({
  key: workspace.key,
  name: workspace.nameRu,
  description: workspace.description,
  active: workspace.active,
  isDefault: workspace.isDefault,
  sortOrder: workspace.sortOrder,
  materialCount: workspace.key === "general" ? 1 : 0,
  readyMaterialCount: workspace.key === "general" ? 1 : 0,
  updatedAt: workspace.updatedAt,
}));

const referenceProjectsResponse: ReferenceProject[] = [
  {
    key: "north-line",
    workspaceKey: "north-upgrade",
    nameRu: "Северная линия",
    active: true,
    sortOrder: 0,
    createdAt: referenceTimestamp,
    updatedAt: referenceTimestamp,
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

const getRequestPathnames = () =>
  vi.mocked(globalThis.fetch).mock.calls.map(([input]) => new URL(getUrl(input)).pathname);

const getRequestUrls = () =>
  vi.mocked(globalThis.fetch).mock.calls.map(([input]) => new URL(getUrl(input)));

const expectNoRequestsTo = (forbiddenPathnames: string[]) => {
  const pathnames = getRequestPathnames();
  forbiddenPathnames.forEach((pathname) => {
    expect(pathnames).not.toContain(pathname);
  });
};

const crossTabRequestPathnames = [
  "/api/materials",
  "/api/materials/policy",
  "/api/models",
  "/api/instructions",
  "/api/knowledge-presets",
  "/api/knowledge-facets",
  "/api/reference/workspaces",
  "/api/reference/projects",
  "/api/rag-projects",
  "/api/chat-runs",
  "/api/llm-providers",
];

const getPanel = (panelId: "overview" | "materials" | "instructions" | "rag" | "direct" | "settings") =>
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
  knowledgeScope?: {
    workspaceKey?: string | null;
  };
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
  let currentRagProjects: RagProjectSummary[];
  let currentRagProjectsError: boolean;
  let ragProjectsFetchGate: Promise<void> | null;
  let currentReferenceWorkspaces: ReferenceWorkspace[];
  let currentReferenceProjects: ReferenceProject[];
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
    currentRagProjects = ragProjectsResponse.map((project) => ({ ...project }));
    currentRagProjectsError = false;
    ragProjectsFetchGate = null;
    currentReferenceWorkspaces = referenceWorkspacesResponse.map((workspace) => ({ ...workspace }));
    currentReferenceProjects = referenceProjectsResponse.map((project) => ({ ...project }));
    chatRequests = [];
    chatRunSequence = 0;
    chatRunResponses = {};
    window.localStorage.clear();

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

      if (pathname === "/api/llm-providers" && (!init?.method || init.method === "GET")) {
        return jsonResponse([]);
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

      if (url.endsWith("/api/knowledge-facets")) {
        return jsonResponse(knowledgePresetsResponse);
      }

      if (pathname === "/api/rag-projects" && (!init?.method || init.method === "GET")) {
        if (ragProjectsFetchGate) {
          await ragProjectsFetchGate;
        }
        if (currentRagProjectsError) {
          return new Response(JSON.stringify({
            code: "rag_projects.storage_read_failed",
            message: "Unable to load RAG projects",
          }), {
            status: 500,
            headers: {
              "Content-Type": "application/json",
            },
          });
        }
        return jsonResponse(currentRagProjects);
      }

      if (pathname === "/api/rag-projects" && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Partial<RagProjectSummary>;
        const created: RagProjectSummary = {
          key: request.key ?? "",
          name: request.name ?? "",
          description: request.description ?? null,
          active: request.active ?? true,
          sortOrder: request.sortOrder ?? 0,
          isDefault: request.isDefault ?? false,
          materialCount: 0,
          readyMaterialCount: 0,
          updatedAt: referenceTimestamp,
        };
        if (created.isDefault) {
          currentRagProjects = currentRagProjects.map((project) => ({
            ...project,
            isDefault: false,
          }));
        }
        currentRagProjects = [...currentRagProjects, created];
        return jsonResponse(created);
      }

      const ragProjectMatch = pathname.match(/^\/api\/rag-projects\/([^/]+)$/);
      if (ragProjectMatch && init?.method === "PUT") {
        const projectKey = decodeURIComponent(ragProjectMatch[1] ?? "");
        const request = JSON.parse(String(init.body)) as Partial<RagProjectSummary>;
        if (request.isDefault) {
          currentRagProjects = currentRagProjects.map((project) => ({
            ...project,
            isDefault: false,
          }));
        }
        let updated = currentRagProjects.find((project) => project.key === projectKey) ?? null;
        currentRagProjects = currentRagProjects.map((project) => {
          if (project.key !== projectKey) {
            return project;
          }
          updated = {
            ...project,
            ...request,
            key: project.key,
            updatedAt: referenceTimestamp,
          };
          return updated;
        });
        return jsonResponse(updated);
      }

      if (pathname === "/api/reference/workspaces" && (!init?.method || init.method === "GET")) {
        return jsonResponse(currentReferenceWorkspaces);
      }

      if (pathname === "/api/reference/workspaces" && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Partial<ReferenceWorkspace>;
        const created: ReferenceWorkspace = {
          key: request.key ?? "",
          nameRu: request.nameRu ?? "",
          active: request.active ?? true,
          sortOrder: request.sortOrder ?? 0,
          isDefault: request.isDefault ?? false,
          createdAt: referenceTimestamp,
          updatedAt: referenceTimestamp,
        };
        if (created.isDefault) {
          currentReferenceWorkspaces = currentReferenceWorkspaces.map((workspace) => ({
            ...workspace,
            isDefault: false,
          }));
        }
        currentReferenceWorkspaces = [...currentReferenceWorkspaces, created];
        return jsonResponse(created);
      }

      const referenceWorkspaceMatch = pathname.match(/^\/api\/reference\/workspaces\/([^/]+)$/);
      if (referenceWorkspaceMatch && init?.method === "PUT") {
        const workspaceKey = decodeURIComponent(referenceWorkspaceMatch[1] ?? "");
        const request = JSON.parse(String(init.body)) as Partial<ReferenceWorkspace>;
        if (request.isDefault) {
          currentReferenceWorkspaces = currentReferenceWorkspaces.map((workspace) => ({
            ...workspace,
            isDefault: false,
          }));
        }
        let updated = currentReferenceWorkspaces.find((workspace) => workspace.key === workspaceKey) ?? null;
        currentReferenceWorkspaces = currentReferenceWorkspaces.map((workspace) => {
          if (workspace.key !== workspaceKey) {
            return workspace;
          }
          updated = {
            ...workspace,
            ...request,
            key: workspace.key,
            updatedAt: referenceTimestamp,
          };
          return updated;
        });
        return jsonResponse(updated);
      }

      if (pathname === "/api/reference/projects" && (!init?.method || init.method === "GET")) {
        return jsonResponse(currentReferenceProjects);
      }

      if (pathname === "/api/reference/projects" && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Partial<ReferenceProject>;
        const created: ReferenceProject = {
          key: request.key ?? "",
          workspaceKey: request.workspaceKey ?? "",
          nameRu: request.nameRu ?? "",
          active: request.active ?? true,
          sortOrder: request.sortOrder ?? 0,
          createdAt: referenceTimestamp,
          updatedAt: referenceTimestamp,
        };
        currentReferenceProjects = [...currentReferenceProjects, created];
        return jsonResponse(created);
      }

      const referenceProjectMatch = pathname.match(/^\/api\/reference\/projects\/([^/]+)$/);
      if (referenceProjectMatch && init?.method === "PUT") {
        const projectKey = decodeURIComponent(referenceProjectMatch[1] ?? "");
        const request = JSON.parse(String(init.body)) as Partial<ReferenceProject>;
        let updated = currentReferenceProjects.find((project) => project.key === projectKey) ?? null;
        currentReferenceProjects = currentReferenceProjects.map((project) => {
          if (project.key !== projectKey) {
            return project;
          }
          updated = {
            ...project,
            ...request,
            key: project.key,
            updatedAt: referenceTimestamp,
          };
          return updated;
        });
        return jsonResponse(updated);
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
            statusUrl: `/api/chat-runs/${runId}/status`,
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

      const chatRunStatusMatch = pathname.match(/^\/api\/chat-runs\/([^/]+)\/status$/);
      if (chatRunStatusMatch) {
        const runId = chatRunStatusMatch[1] ?? "";
        const storedRun = chatRunResponses[runId];
        if (!storedRun) {
          throw new Error(`Unexpected chat run status request: ${url}`);
        }

        return jsonResponse({
          id: runId,
          status: "COMPLETED",
          createdAt: storedRun.response.createdAt,
          completedAt: storedRun.response.createdAt,
          failedAt: null,
          latencyMsTotal: null,
          failureStage: null,
          failureCode: null,
          failureMessage: null,
        });
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

  it("hides memory review when long-term memory is disabled by health flags", async () => {
    currentHealthResponse = buildHealthResponse({
      contextFeatures: {
        context: true,
        conversations: true,
        history: true,
        sticky: true,
        rewrite: true,
        summary: true,
        longTermMemory: false,
      },
    });

    render(<App />);

    await screen.findByRole("button", { name: /дашборд/i });

    expect(screen.queryByRole("button", { name: /память/i })).toBeNull();
    expect(getRequestPathnames()).not.toContain("/api/memory-entries");
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
    await openSection(user, /дашборд/i);
    const overviewPanel = getPanel("overview");
    expect(within(overviewPanel).getByText(overviewHeadline)).toBeTruthy();
    if (overviewMessage) {
      expect(
        within(overviewPanel).getAllByText((content) => content.includes(overviewMessage)).length,
      ).toBeGreaterThan(0);
    }

    await openSection(user, /материалы/i);
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

  it("shows the Kegoc RAG brand on the login screen", async () => {
    vi.mocked(globalThis.fetch).mockImplementation(async (input) => {
      const url = getUrl(input);

      if (url.endsWith("/api/auth/session")) {
        return jsonResponse({
          authenticated: false,
          username: null,
          roles: [],
          csrfHeaderName: "X-CSRF-TOKEN",
          csrfToken: "csrf-test-token",
        });
      }

      throw new Error(`Unexpected request: ${url}`);
    });

    render(<App />);

    expect(await screen.findByRole("heading", { name: "KEGOC RAG" })).toBeTruthy();
    expect(screen.getByText("ПАНЕЛЬ УПРАВЛЕНИЯ")).toBeTruthy();
    expect(screen.getByText("Вход администратора")).toBeTruthy();
    expect(screen.getByAltText("Логотип KEGOC")).toBeTruthy();
    expect((screen.getByLabelText("Логин") as HTMLInputElement).value).toBe("admin");
    expect((screen.getByLabelText("Пароль") as HTMLInputElement).value).toBe("");
    expect(screen.getByRole("button", { name: "Войти" })).toBeTruthy();
  });

  it("keeps the authenticated dashboard to shell-level requests only", async () => {
    render(<App />);

    expect(await screen.findByRole("button", { name: /дашборд/i })).toBeTruthy();

    const pathnames = getRequestPathnames();
    expect(pathnames).toContain("/api/auth/session");
    expect(pathnames).toContain("/api/health");
    expect(pathnames).toContain("/api/rag-projects");
    expectNoRequestsTo(crossTabRequestPathnames.filter((pathname) => pathname !== "/api/rag-projects"));
  });

  it("loads feature data only when the corresponding tab becomes active", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("button", { name: /дашборд/i });
    vi.mocked(globalThis.fetch).mockClear();

    await openSection(user, /материалы/i);
    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/materials");
    });
    expect(getRequestPathnames()).toContain("/api/materials/policy");
    expectNoRequestsTo([
      "/api/models",
      "/api/instructions",
      "/api/knowledge-presets",
      "/api/knowledge-facets",
      "/api/reference/workspaces",
      "/api/reference/projects",
      "/api/rag-projects",
      "/api/chat-runs",
    ]);

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /настройки/i);
    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/llm-providers");
    });
    expectNoRequestsTo([
      "/api/materials",
      "/api/materials/policy",
      "/api/models",
      "/api/instructions",
      "/api/knowledge-presets",
      "/api/knowledge-facets",
      "/api/reference/workspaces",
      "/api/reference/projects",
      "/api/rag-projects",
      "/api/chat-runs",
    ]);

    vi.mocked(globalThis.fetch).mockClear();
    await user.click(within(getPanel("settings")).getByRole("button", { name: "RAG-проекты" }));
    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/reference/workspaces");
    });
    expect(getRequestPathnames()).toEqual(expect.arrayContaining([
      "/api/knowledge-presets",
      "/api/knowledge-facets",
      "/api/reference/workspaces",
      "/api/reference/projects",
    ]));
    expectNoRequestsTo(["/api/materials", "/api/materials/policy", "/api/models", "/api/instructions", "/api/rag-projects", "/api/chat-runs", "/api/llm-providers"]);

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /direct studio/i);
    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/models");
    });
    expect(getRequestPathnames()).toEqual(expect.arrayContaining([
      "/api/instructions",
      "/api/chat-runs",
    ]));
    expectNoRequestsTo([
      "/api/materials",
      "/api/materials/policy",
      "/api/knowledge-presets",
      "/api/knowledge-facets",
      "/api/reference/workspaces",
      "/api/reference/projects",
      "/api/rag-projects",
    ]);

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /rag studio/i);
    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/chat-runs");
    });
    expectNoRequestsTo([
      "/api/materials",
      "/api/materials/policy",
      "/api/models",
      "/api/instructions",
      "/api/knowledge-presets",
      "/api/knowledge-facets",
      "/api/reference/workspaces",
      "/api/reference/projects",
      "/api/rag-projects",
    ]);
  });

  it("resolves inactive stored RAG project before material and RAG calls", async () => {
    const user = userEvent.setup();
    currentRagProjects = [
      {
        key: "legacy-project",
        name: "Legacy Project",
        description: null,
        active: false,
        isDefault: false,
        sortOrder: -1,
        materialCount: 0,
        readyMaterialCount: 0,
        updatedAt: referenceTimestamp,
      },
      ...ragProjectsResponse.map((project) => ({ ...project })),
    ];
    window.localStorage.setItem(ACTIVE_RAG_PROJECT_STORAGE_KEY, "legacy-project");

    render(<App />);

    await waitFor(() => {
      expect(window.localStorage.getItem(ACTIVE_RAG_PROJECT_STORAGE_KEY)).toBe("general");
    });

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /материалы/i);
    await waitFor(() => {
      expect(getRequestUrls().some(
        (url) => url.pathname === "/api/materials" && url.searchParams.get("workspaceKey") === "general",
      )).toBe(true);
    });
    expect(getRequestUrls().some((url) => url.searchParams.get("workspaceKey") === "legacy-project")).toBe(false);

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /rag studio/i);
    await waitFor(() => {
      expect(getRequestUrls().some(
        (url) => url.pathname === "/api/chat-runs" && url.searchParams.get("workspaceKey") === "general",
      )).toBe(true);
    });
    expect(getRequestUrls().some((url) => url.searchParams.get("workspaceKey") === "legacy-project")).toBe(false);
  });

  it("does not start project-scoped calls before active RAG project resolution completes", async () => {
    const user = userEvent.setup();
    let releaseRagProjects: () => void = () => {};
    ragProjectsFetchGate = new Promise((resolve) => {
      releaseRagProjects = resolve;
    });

    render(<App />);
    await screen.findByRole("button", { name: /дашборд/i });

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /материалы/i);
    await act(async () => {});
    expectNoRequestsTo(["/api/materials", "/api/materials/policy"]);

    await openSection(user, /rag studio/i);
    await act(async () => {});
    expectNoRequestsTo(["/api/chat-runs"]);

    releaseRagProjects();
    await waitFor(() => {
      expect(window.localStorage.getItem(ACTIVE_RAG_PROJECT_STORAGE_KEY)).toBe("general");
    });
  });

  it("keeps stale localStorage untouched when RAG project loading fails", async () => {
    const user = userEvent.setup();
    currentRagProjectsError = true;
    window.localStorage.setItem(ACTIVE_RAG_PROJECT_STORAGE_KEY, "legacy-project");

    render(<App />);

    await waitFor(() => {
      expect(getRequestPathnames()).toContain("/api/rag-projects");
    });
    await act(async () => {});
    expect(window.localStorage.getItem(ACTIVE_RAG_PROJECT_STORAGE_KEY)).toBe("legacy-project");

    vi.mocked(globalThis.fetch).mockClear();
    await openSection(user, /материалы/i);
    await act(async () => {});
    expectNoRequestsTo(["/api/materials", "/api/materials/policy"]);

    await openSection(user, /rag studio/i);
    await act(async () => {});
    expectNoRequestsTo(["/api/chat-runs"]);
    const ragPanel = getPanel("rag");
    expect((within(ragPanel).getByRole("button", { name: "Спросить по материалам" }) as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it("opens on dashboard and shows explicit instruction selectors in both studio screens", async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await screen.findByRole("button", { name: /дашборд/i });

    const overviewButton = screen.getByRole("button", { name: /дашборд/i });
    const materialsButton = screen.getByRole("button", { name: /материалы/i });
    const instructionsButton = screen.getByRole("button", { name: /инструкции/i });
    const ragButton = screen.getByRole("button", { name: /rag studio/i });
    const directButton = screen.getByRole("button", { name: /direct studio/i });
    const memoryButton = screen.getByRole("button", { name: /память/i });
    const evalButton = container.querySelector("#nav-eval") as HTMLButtonElement;
    const settingsButton = screen.getByRole("button", { name: /настройки/i });

    const desktopNavigation = container.querySelector("aside nav") as HTMLElement;
    expect(within(desktopNavigation).getAllByRole("button").map((button) => button.textContent)).toEqual([
      expect.stringContaining("Дашборд"),
      expect.stringContaining("Материалы"),
      expect.stringContaining("Инструкции"),
      expect.stringContaining("RAG Studio"),
      expect.stringContaining("Direct Studio"),
      expect.stringContaining("Память"),
      expect.stringContaining("Оценка"),
      expect.stringContaining("Настройки"),
    ]);
    expect(within(desktopNavigation).queryByRole("button", { name: /справочники/i })).toBeNull();
    const desktopSidebar = container.querySelector("aside") as HTMLElement;
    expect(within(desktopSidebar).queryByText("Models")).toBeNull();
    expect(within(desktopSidebar).queryByText("Active KB")).toBeNull();
    expect(within(desktopSidebar).queryByText("Snippets")).toBeNull();

    expect(overviewButton.getAttribute("aria-current")).toBe("page");
    expect(materialsButton.getAttribute("aria-current")).toBeNull();
    expect(instructionsButton.getAttribute("aria-current")).toBeNull();
    expect(ragButton.getAttribute("aria-current")).toBeNull();
    expect(directButton.getAttribute("aria-current")).toBeNull();
    expect(memoryButton.getAttribute("aria-current")).toBeNull();
    expect(evalButton.getAttribute("aria-current")).toBeNull();
    expect(settingsButton.getAttribute("aria-current")).toBeNull();

    const overviewPanel = container.querySelector("#panel-overview") as HTMLElement;
    const materialsPanel = container.querySelector("#panel-materials") as HTMLElement;
    const instructionsPanel = container.querySelector("#panel-instructions") as HTMLElement;
    const ragPanel = container.querySelector("#panel-rag") as HTMLElement;
    const directPanel = container.querySelector("#panel-direct") as HTMLElement;
    const settingsPanel = container.querySelector("#panel-settings") as HTMLElement;
    const overviewHeader = container.querySelector("header") as HTMLElement;

    expect(overviewPanel.hidden).toBe(false);
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(true);
    expect(ragPanel.hidden).toBe(true);
    expect(directPanel.hidden).toBe(true);
    expect(settingsPanel.hidden).toBe(true);
    expect(settingsPanel.getAttribute("aria-labelledby")).toBe("nav-settings");
    expect(settingsPanel.getAttribute("role")).toBe("region");
    expect(within(overviewPanel).getByText("Backend")).toBeTruthy();
    expect(within(overviewHeader).getByText("Выбранный RAG-проект")).toBeTruthy();
    expect(await within(overviewHeader).findByText("Общая")).toBeTruthy();
    expect(within(overviewHeader).getByText("активен")).toBeTruthy();
    expect(screen.queryByText("Активный RAG-проект")).toBeNull();

    await user.click(materialsButton);
    expect(materialsButton.getAttribute("aria-current")).toBe("page");
    expect(overviewPanel.hidden).toBe(true);
    expect(materialsPanel.hidden).toBe(false);
    expect(within(materialsPanel).getByRole("heading", { name: "Материалы проекта: Общая" })).toBeTruthy();
    expect(screen.queryByText("Активный RAG-проект")).toBeNull();

    await user.click(instructionsButton);
    expect(instructionsButton.getAttribute("aria-current")).toBe("page");
    expect(materialsPanel.hidden).toBe(true);
    expect(instructionsPanel.hidden).toBe(false);
    expect(within(instructionsPanel).getByText("Инструкции RAG-проекта")).toBeTruthy();
    expect(within(instructionsPanel).queryByText("Сохранённые наборы знаний")).toBeNull();

    await user.click(ragButton);
    expect(ragPanel.hidden).toBe(false);
    expect(screen.queryByText("Активный RAG-проект")).toBeNull();
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

    await user.click(settingsButton);
    expect(settingsButton.getAttribute("aria-current")).toBe("page");
    expect(directPanel.hidden).toBe(true);
    expect(settingsPanel.hidden).toBe(false);
    expect(within(settingsPanel).getByRole("button", { name: "LLM-подключения" }).getAttribute("aria-pressed")).toBe("true");
    expect(within(settingsPanel).getByRole("button", { name: "RAG-проекты" })).toBeTruthy();
    expect(within(settingsPanel).getByRole("button", { name: "Пресеты проекта" })).toBeTruthy();
    expect(within(settingsPanel).getByRole("button", { name: "Фасеты проекта" })).toBeTruthy();
    expect(within(settingsPanel).getByRole("heading", {
      name: "Корпоративные OpenAI-compatible подключения",
    })).toBeTruthy();
    expect(await within(settingsPanel).findByText("Подключения не созданы. Используется fallback из env.")).toBeTruthy();

    await user.click(within(settingsPanel).getByRole("button", { name: "RAG-проекты" }));
    expect(within(settingsPanel).getByText("Активный RAG-проект")).toBeTruthy();
    expect(within(settingsPanel).getByRole("heading", { name: "Пресеты и справочники" })).toBeTruthy();
    expect(within(settingsPanel).queryByRole("button", { name: "Проекты" })).toBeNull();

    await user.click(within(settingsPanel).getByRole("button", { name: "Пресеты проекта" }));
    expect(within(settingsPanel).queryByText("Активный RAG-проект")).toBeNull();
    expect(within(settingsPanel).getByText("Создать preset проекта")).toBeTruthy();
    expect(within(settingsPanel).getByText("Пресеты проекта: Общая")).toBeTruthy();
    expect(within(settingsPanel).getByText("Scope и история выбранного набора знаний")).toBeTruthy();

    await user.click(within(settingsPanel).getByRole("button", { name: "Фасеты проекта" }));
    expect(within(settingsPanel).getByText("Создать фасет проекта")).toBeTruthy();
    expect(within(settingsPanel).getByText("Фасеты проекта: Общая")).toBeTruthy();
  });

  it("creates a RAG-project and locks materials metadata to it", async () => {
    const user = userEvent.setup();
    currentHealthResponse = buildHealthResponse({
      qualityLayer: buildQualityLayer({ metadataV1: true }),
    });
    render(<App />);

    await screen.findByRole("button", { name: /дашборд/i });
    await openSection(user, /настройки/i);
    const settingsPanel = getPanel("settings");
    await user.click(within(settingsPanel).getByRole("button", { name: "RAG-проекты" }));

    await waitFor(() => {
      expect(within(settingsPanel).getAllByText("Общая").length).toBeGreaterThan(0);
    });

    await user.type(within(settingsPanel).getByLabelText("Ключ"), "south-grid");
    await user.type(within(settingsPanel).getByLabelText("Название"), "Южная сеть");
    const createRagProjectButton = within(settingsPanel)
      .getAllByRole("button", { name: "Создать RAG-проект" })
      .find((button) => button.getAttribute("type") === "submit");
    expect(createRagProjectButton).toBeTruthy();
    await user.click(createRagProjectButton as HTMLButtonElement);

    await waitFor(() => {
      expect(within(settingsPanel).getAllByText("Южная сеть").length).toBeGreaterThan(0);
    });

    await openSection(user, /материалы/i);
    const materialsPanel = getPanel("materials");
    expect(within(materialsPanel).getByRole("heading", { name: "Материалы проекта: Южная сеть" })).toBeTruthy();
    expect(within(materialsPanel).getAllByText("key: south-grid").length).toBeGreaterThan(0);
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

    await openSection(user, /дашборд/i);
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

    await openSection(user, /инструкции/i);

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

    expect(await screen.findByRole("button", { name: /дашборд/i })).toBeTruthy();

    await openSection(user, /материалы/i);

    const materialsPanel = getPanel("materials");
    expect(within(materialsPanel).getByRole("heading", { name: "Материалы проекта: Общая" })).toBeTruthy();
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
      overviewMessage: "Readiness считается по active lineage, READY/PARTIAL_READY, действующему статусу документа и валидному периоду.",
      materialsMessage:
        "RAG использует только active lineage версии со статусом READY/PARTIAL_READY, documentStatus=ACTIVE и валидным периодом; historical остаются в каталоге для аудита.",
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

    await openSection(user, /дашборд/i);
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
