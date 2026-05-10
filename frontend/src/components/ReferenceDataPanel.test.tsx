import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReferenceDataPanel } from "./ReferenceDataPanel";
import type { RagProjectSummary, ReferenceProject, ReferenceWorkspace } from "@/types";

const workspaces: ReferenceWorkspace[] = [
  {
    key: "general",
    nameRu: "Общая",
    active: true,
    sortOrder: 0,
    isDefault: true,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "north-upgrade",
    nameRu: "Северная модернизация",
    active: true,
    sortOrder: 1,
    isDefault: false,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "archive",
    nameRu: "Архивная область",
    active: false,
    sortOrder: 2,
    isDefault: false,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const projects: ReferenceProject[] = [
  {
    key: "general-faq",
    workspaceKey: "general",
    nameRu: "Общие FAQ",
    active: true,
    sortOrder: 0,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "north-line",
    workspaceKey: "north-upgrade",
    nameRu: "Северная линия",
    active: false,
    sortOrder: 0,
    createdAt: "2026-04-20T10:00:00Z",
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const ragProjects: RagProjectSummary[] = [
  {
    key: "general",
    name: "Общая",
    active: true,
    sortOrder: 0,
    isDefault: true,
    materialCount: 7,
    readyMaterialCount: 5,
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "north-upgrade",
    name: "Северная модернизация",
    active: true,
    sortOrder: 1,
    isDefault: false,
    materialCount: 3,
    readyMaterialCount: 1,
    updatedAt: "2026-04-20T10:00:00Z",
  },
  {
    key: "archive",
    name: "Архивная область",
    active: false,
    sortOrder: 2,
    isDefault: false,
    materialCount: 2,
    readyMaterialCount: 0,
    updatedAt: "2026-04-20T10:00:00Z",
  },
];

const renderPanel = (
  referenceOverrides: Partial<Parameters<typeof ReferenceDataPanel>[0]["referenceData"]> = {},
  ragOverrides: Partial<Parameters<typeof ReferenceDataPanel>[0]["ragProjects"]> = {},
) => {
  const referenceData = {
    workspaces,
    projects,
    isLoading: false,
    error: null,
    actionError: null,
    message: null,
    reload: vi.fn().mockResolvedValue(null),
    createWorkspace: vi.fn().mockResolvedValue(workspaces[0]),
    updateWorkspace: vi.fn().mockResolvedValue(workspaces[0]),
    createProject: vi.fn().mockResolvedValue(projects[0]),
    updateProject: vi.fn().mockResolvedValue(projects[0]),
    ...referenceOverrides,
  };
  const ragProjectController = {
    projects: ragProjects,
    isLoading: false,
    error: null,
    actionError: null,
    message: null,
    reload: vi.fn().mockResolvedValue(null),
    createProject: vi.fn().mockResolvedValue(ragProjects[0]),
    updateProject: vi.fn().mockResolvedValue(ragProjects[0]),
    ...ragOverrides,
  };
  const knowledgePresets = {
    presets: [],
    selectedPreset: null,
    revisions: [],
    revisionDiff: null,
    error: null,
    actionError: null,
    message: null,
    isLoading: false,
    onLoadPreset: vi.fn().mockResolvedValue(null),
    onLoadRevisions: vi.fn().mockResolvedValue([]),
    onLoadRevisionDiff: vi.fn().mockResolvedValue(null),
    onCreatePreset: vi.fn().mockResolvedValue(null),
    onUpdatePreset: vi.fn().mockResolvedValue(null),
    onRestoreRevision: vi.fn().mockResolvedValue(null),
    onDeletePreset: vi.fn().mockResolvedValue(null),
  };

  render(
    <ReferenceDataPanel
      knowledgePresets={knowledgePresets}
      ragProjects={ragProjectController}
      referenceData={referenceData}
    />,
  );
  return { ragProjectController, referenceData };
};

const chooseSelectOption = async (
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) => {
  await user.click(screen.getByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
};

describe("ReferenceDataPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders RAG-projects and project presets while hiding legacy project tabs", async () => {
    const user = userEvent.setup();
    renderPanel();

    const tabButtons = screen.getAllByRole("button").filter((button) =>
      ["RAG-проекты", "Пресеты проекта", "Рабочие области", "Проекты"].includes(button.textContent?.trim() ?? ""),
    );
    expect(tabButtons.map((button) => button.textContent?.trim())).toEqual([
      "RAG-проекты",
      "Пресеты проекта",
    ]);
    expect(screen.getByText("Активный RAG-проект")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "Создать RAG-проект" }).length).toBeGreaterThan(0);
    expect(screen.getByText("Архивная область")).toBeTruthy();
    expect(screen.getByText("Материалы: 7; ready: 5; порядок: 0")).toBeTruthy();
    expect(screen.getAllByText("неактивен").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Рабочие области" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Проекты" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Пресеты проекта" }));
    expect(screen.queryByText("Активный RAG-проект")).toBeNull();
    expect(screen.getByText("Создать preset проекта")).toBeTruthy();
    expect(screen.getByText("Пресеты проекта: Общая")).toBeTruthy();
  });

  it("creates a RAG-project with explicit fields", async () => {
    const user = userEvent.setup();
    const { ragProjectController } = renderPanel();

    await user.type(screen.getByLabelText("Ключ"), "south-grid");
    await user.type(screen.getByLabelText("Название"), "Южная сеть");
    await user.clear(screen.getByLabelText("Порядок"));
    await user.type(screen.getByLabelText("Порядок"), "5");
    await user.click(screen.getByText("Основной"));
    const createRagProjectButton = screen
      .getAllByRole("button", { name: "Создать RAG-проект" })
      .find((button) => button.getAttribute("type") === "submit");
    expect(createRagProjectButton).toBeTruthy();
    await user.click(createRagProjectButton as HTMLButtonElement);

    await waitFor(() => {
      expect(ragProjectController.createProject).toHaveBeenCalledWith({
        key: "south-grid",
        name: "Южная сеть",
        description: null,
        active: true,
        sortOrder: 5,
        isDefault: true,
      });
    });
  });

  it("edits and deactivates a RAG-project through update", async () => {
    const user = userEvent.setup();
    const { ragProjectController } = renderPanel();
    const workspaceArticle = screen.getByRole("heading", { name: "Общая" }).closest("article") as HTMLElement;

    await user.click(within(workspaceArticle).getByRole("button", { name: "Редактировать" }));
    await user.clear(within(workspaceArticle).getByLabelText("Название"));
    await user.type(within(workspaceArticle).getByLabelText("Название"), "Общая область");
    await user.click(within(workspaceArticle).getByRole("button", { name: "Сохранить" }));

    await waitFor(() => {
      expect(ragProjectController.updateProject).toHaveBeenCalledWith(
        "general",
        expect.objectContaining({
          name: "Общая область",
          active: true,
          sortOrder: 0,
          isDefault: true,
        }),
      );
    });

    await user.click(within(workspaceArticle).getByRole("button", { name: "Деактивировать" }));
    await waitFor(() => {
      expect(ragProjectController.updateProject).toHaveBeenCalledWith(
        "general",
        expect.objectContaining({
          name: "Общая",
          active: false,
          sortOrder: 0,
          isDefault: true,
        }),
      );
    });
  });

  it("validates RAG-project key and name", async () => {
    const user = userEvent.setup();
    const { ragProjectController } = renderPanel();

    const createRagProjectButton = screen
      .getAllByRole("button", { name: "Создать RAG-проект" })
      .find((button) => button.getAttribute("type") === "submit");
    expect(createRagProjectButton).toBeTruthy();

    await user.click(createRagProjectButton as HTMLButtonElement);
    expect(await screen.findByText("Укажите ключ RAG-проекта.")).toBeTruthy();
    expect(ragProjectController.createProject).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Ключ"), "south-grid");
    await user.click(createRagProjectButton as HTMLButtonElement);
    expect(await screen.findByText("Укажите название RAG-проекта.")).toBeTruthy();
  });

  it("does not synthesize RAG-projects from reference workspaces when the RAG API fails", () => {
    renderPanel({}, {
      projects: [],
      error: "Не удалось загрузить RAG-проекты",
    });

    expect(screen.getAllByText("Не удалось загрузить RAG-проекты").length).toBeGreaterThan(0);
    expect(screen.getByText("RAG-проектов пока нет")).toBeTruthy();
    expect(screen.queryByText("Материалы: 0; ready: 0; порядок: 0")).toBeNull();
    expect(screen.queryByText("0/0 ready")).toBeNull();
  });
});
