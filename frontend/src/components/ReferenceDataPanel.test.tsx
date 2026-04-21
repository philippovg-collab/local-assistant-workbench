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

  render(<ReferenceDataPanel referenceData={referenceData} />);
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

  it("renders workspace and project tabs with inactive entities", async () => {
    const user = userEvent.setup();
    renderPanel();

    expect(screen.getByRole("button", { name: "Рабочие области" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Проекты" })).toBeTruthy();
    expect(screen.getByText("Архивная область")).toBeTruthy();
    expect(screen.getAllByText("неактивна").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Проекты" }));
    expect(screen.getByText("Северная линия")).toBeTruthy();
    expect(screen.getAllByText("неактивен").length).toBeGreaterThan(0);
  });

  it("creates a workspace with explicit fields", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();

    await user.type(screen.getByLabelText("Ключ рабочей области"), "south-grid");
    await user.type(screen.getByLabelText("Название рабочей области"), "Южная сеть");
    await user.clear(screen.getByLabelText("Порядок"));
    await user.type(screen.getByLabelText("Порядок"), "5");
    await user.click(screen.getByText("Основная"));
    await user.click(screen.getByRole("button", { name: "Создать рабочую область" }));

    await waitFor(() => {
      expect(referenceData.createWorkspace).toHaveBeenCalledWith({
        key: "south-grid",
        nameRu: "Южная сеть",
        active: true,
        sortOrder: 5,
        isDefault: true,
      });
    });
  });

  it("edits and deactivates a workspace through update", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();
    const workspaceArticle = screen.getByText("Общая").closest("article") as HTMLElement;

    await user.click(within(workspaceArticle).getByRole("button", { name: "Редактировать" }));
    await user.clear(within(workspaceArticle).getByLabelText("Название рабочей области"));
    await user.type(within(workspaceArticle).getByLabelText("Название рабочей области"), "Общая область");
    await user.click(within(workspaceArticle).getByRole("button", { name: "Сохранить" }));

    await waitFor(() => {
      expect(referenceData.updateWorkspace).toHaveBeenCalledWith("general", {
        nameRu: "Общая область",
        active: true,
        sortOrder: 0,
        isDefault: true,
      });
    });

    await user.click(within(workspaceArticle).getByRole("button", { name: "Деактивировать" }));
    await waitFor(() => {
      expect(referenceData.updateWorkspace).toHaveBeenCalledWith("general", {
        nameRu: "Общая",
        active: false,
        sortOrder: 0,
        isDefault: true,
      });
    });
  });

  it("creates a project and validates required workspace", async () => {
    const user = userEvent.setup();
    const referenceData = renderPanel();

    await user.click(screen.getByRole("button", { name: "Проекты" }));
    await user.type(screen.getByLabelText("Ключ проекта"), "south-line");
    await user.click(screen.getByRole("button", { name: "Создать проект" }));
    expect(await screen.findByText("Выберите рабочую область проекта.")).toBeTruthy();

    await chooseSelectOption(user, "Рабочая область проекта", "Общая");
    await user.type(screen.getByLabelText("Название проекта"), "Южная линия");
    await user.click(screen.getByRole("button", { name: "Создать проект" }));

    await waitFor(() => {
      expect(referenceData.createProject).toHaveBeenCalledWith({
        key: "south-line",
        workspaceKey: "general",
        nameRu: "Южная линия",
        active: true,
        sortOrder: 0,
      });
    });
  });

  it("filters projects by workspace", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole("button", { name: "Проекты" }));
    await chooseSelectOption(user, "Фильтр по рабочей области", "Северная модернизация");

    expect(screen.getByText("Северная линия")).toBeTruthy();
    expect(screen.queryByText("Общие FAQ")).toBeNull();
  });
});
