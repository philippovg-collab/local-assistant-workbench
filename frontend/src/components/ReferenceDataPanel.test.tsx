import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReferenceDataPanel } from "./ReferenceDataPanel";
import type { ReferenceProject, ReferenceWorkspace } from "@/types";

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

const renderPanel = (overrides: Partial<Parameters<typeof ReferenceDataPanel>[0]["referenceData"]> = {}) => {
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
    ...overrides,
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

  render(<ReferenceDataPanel knowledgePresets={knowledgePresets} referenceData={referenceData} />);
  return referenceData;
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
    expect(screen.getByRole("button", { name: "Создать RAG-проект" })).toBeTruthy();
    expect(screen.getByText("Архивная область")).toBeTruthy();
    expect(screen.getAllByText("неактивен").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Рабочие области" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Проекты" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Пресеты проекта" }));
    expect(screen.getByText("Создать preset проекта")).toBeTruthy();
    expect(screen.getByText("Пресеты проекта: Общая")).toBeTruthy();
  });

  it("creates a RAG-project with explicit fields", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();

    await user.type(screen.getByLabelText("Ключ"), "south-grid");
    await user.type(screen.getByLabelText("Название"), "Южная сеть");
    await user.clear(screen.getByLabelText("Порядок"));
    await user.type(screen.getByLabelText("Порядок"), "5");
    await user.click(screen.getByText("Основной"));
    await user.click(screen.getByRole("button", { name: "Создать RAG-проект" }));

    await waitFor(() => {
      expect(referenceData.createWorkspace).toHaveBeenCalledWith({
        key: "south-grid",
        nameRu: "Южная сеть",
        description: null,
        active: true,
        sortOrder: 5,
        isDefault: true,
      });
    });
  });

  it("edits and deactivates a RAG-project through update", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();
    const workspaceArticle = screen.getByText("Общая").closest("article") as HTMLElement;

    await user.click(within(workspaceArticle).getByRole("button", { name: "Редактировать" }));
    await user.clear(within(workspaceArticle).getByLabelText("Название"));
    await user.type(within(workspaceArticle).getByLabelText("Название"), "Общая область");
    await user.click(within(workspaceArticle).getByRole("button", { name: "Сохранить" }));

    await waitFor(() => {
      expect(referenceData.updateWorkspace).toHaveBeenCalledWith(
        "general",
        expect.objectContaining({
          nameRu: "Общая область",
          active: true,
          sortOrder: 0,
          isDefault: true,
        }),
      );
    });

    await user.click(within(workspaceArticle).getByRole("button", { name: "Деактивировать" }));
    await waitFor(() => {
      expect(referenceData.updateWorkspace).toHaveBeenCalledWith(
        "general",
        expect.objectContaining({
          nameRu: "Общая",
          active: false,
          sortOrder: 0,
          isDefault: true,
        }),
      );
    });
  });

  it("validates RAG-project key and name", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();

    await user.click(screen.getByRole("button", { name: "Создать RAG-проект" }));
    expect(await screen.findByText("Укажите ключ RAG-проекта.")).toBeTruthy();
    expect(referenceData.createWorkspace).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Ключ"), "south-grid");
    await user.click(screen.getByRole("button", { name: "Создать RAG-проект" }));
    expect(await screen.findByText("Укажите название RAG-проекта.")).toBeTruthy();
  });
});
