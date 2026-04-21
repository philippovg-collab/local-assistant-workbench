import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "@/api/client";
import type { ReferenceProject, ReferenceWorkspace } from "@/types";
import { useReferenceData } from "./useReferenceData";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      createReferenceProject: vi.fn(),
      createReferenceWorkspace: vi.fn(),
      fetchReferenceProjects: vi.fn(),
      fetchReferenceWorkspaces: vi.fn(),
      updateReferenceProject: vi.fn(),
      updateReferenceWorkspace: vi.fn(),
    },
  };
});

function swallow(promise: Promise<unknown>) {
  void promise.catch(() => undefined);
}

const buildWorkspace = (overrides: Partial<ReferenceWorkspace> = {}): ReferenceWorkspace => ({
  key: "general",
  nameRu: "Общая",
  active: true,
  sortOrder: 0,
  isDefault: true,
  createdAt: "2026-04-20T10:00:00Z",
  updatedAt: "2026-04-20T10:00:00Z",
  ...overrides,
});

const buildProject = (overrides: Partial<ReferenceProject> = {}): ReferenceProject => ({
  key: "north-grid",
  workspaceKey: "north-upgrade",
  nameRu: "Северная сеть",
  active: true,
  sortOrder: 0,
  createdAt: "2026-04-20T10:00:00Z",
  updatedAt: "2026-04-20T10:00:00Z",
  ...overrides,
});

function ReferenceDataHookHarness({ enabled, activeOnly }: { enabled?: boolean; activeOnly?: boolean }) {
  const referenceData = useReferenceData({ enabled, activeOnly });

  return (
    <section>
      <button
        type="button"
        onClick={() =>
          swallow(referenceData.createWorkspace({
            key: "north-upgrade",
            nameRu: "Северная модернизация",
            active: true,
          }))
        }
      >
        create-workspace
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(referenceData.updateWorkspace("north-upgrade", {
            nameRu: "Северная модернизация 2",
            active: true,
          }))
        }
      >
        update-workspace
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(referenceData.createProject({
            key: "north-grid",
            workspaceKey: "north-upgrade",
            nameRu: "Северная сеть",
          }))
        }
      >
        create-project
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(referenceData.updateProject("north-grid", {
            workspaceKey: "north-upgrade",
            nameRu: "Северная сеть 2",
          }))
        }
      >
        update-project
      </button>

      <button type="button" onClick={() => swallow(referenceData.reload())}>
        reload
      </button>

      <output data-testid="workspace-count">{referenceData.workspaces.length}</output>
      <output data-testid="project-count">{referenceData.projects.length}</output>
      <output data-testid="north-project-count">
        {referenceData.projectsForWorkspace("north-upgrade").length}
      </output>
      <output data-testid="blank-project-count">
        {referenceData.projectsForWorkspace("").length}
      </output>
      <output data-testid="load-error">{referenceData.error ?? ""}</output>
      <output data-testid="action-error">{referenceData.actionError ?? ""}</output>
      <output data-testid="message">{referenceData.message ?? ""}</output>
      <output data-testid="is-loading">{String(referenceData.isLoading)}</output>
    </section>
  );
}

describe("useReferenceData", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads workspaces and filters projects by workspace key", async () => {
    vi.mocked(apiClient.fetchReferenceWorkspaces).mockResolvedValue([
      buildWorkspace(),
      buildWorkspace({ key: "north-upgrade", nameRu: "Северная модернизация", isDefault: false }),
    ]);
    vi.mocked(apiClient.fetchReferenceProjects).mockResolvedValue([
      buildProject(),
      buildProject({ key: "other-grid", workspaceKey: "other-workspace" }),
    ]);

    render(<ReferenceDataHookHarness />);

    await waitFor(() => {
      expect(screen.getByTestId("workspace-count").textContent).toBe("2");
      expect(screen.getByTestId("project-count").textContent).toBe("2");
      expect(screen.getByTestId("north-project-count").textContent).toBe("1");
      expect(screen.getByTestId("blank-project-count").textContent).toBe("0");
    });

    expect(apiClient.fetchReferenceWorkspaces).toHaveBeenCalledWith({ activeOnly: false }, expect.any(AbortSignal));
    expect(apiClient.fetchReferenceProjects).toHaveBeenCalledWith({ activeOnly: false }, expect.any(AbortSignal));
  });

  it("does not load reference data when disabled", async () => {
    render(<ReferenceDataHookHarness enabled={false} />);

    await waitFor(() => {
      expect(screen.getByTestId("is-loading").textContent).toBe("false");
    });
    expect(apiClient.fetchReferenceWorkspaces).not.toHaveBeenCalled();
    expect(apiClient.fetchReferenceProjects).not.toHaveBeenCalled();
  });

  it("passes activeOnly option to list requests", async () => {
    vi.mocked(apiClient.fetchReferenceWorkspaces).mockResolvedValue([buildWorkspace()]);
    vi.mocked(apiClient.fetchReferenceProjects).mockResolvedValue([]);

    render(<ReferenceDataHookHarness activeOnly />);

    await waitFor(() => {
      expect(apiClient.fetchReferenceWorkspaces).toHaveBeenCalledWith({ activeOnly: true }, expect.any(AbortSignal));
    });
    expect(apiClient.fetchReferenceProjects).toHaveBeenCalledWith({ activeOnly: true }, expect.any(AbortSignal));
  });

  it("creates workspaces and reloads reference data", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.fetchReferenceWorkspaces).mockResolvedValue([buildWorkspace()]);
    vi.mocked(apiClient.fetchReferenceProjects).mockResolvedValue([]);
    vi.mocked(apiClient.createReferenceWorkspace).mockResolvedValue(buildWorkspace({
      key: "north-upgrade",
      nameRu: "Северная модернизация",
      isDefault: false,
    }));

    render(<ReferenceDataHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchReferenceWorkspaces).toHaveBeenCalled();
    });

    await user.click(screen.getByText("create-workspace"));

    await waitFor(() => {
      expect(screen.getByTestId("message").textContent).toContain("Рабочая область сохранена");
    });
    expect(apiClient.createReferenceWorkspace).toHaveBeenCalledWith({
      key: "north-upgrade",
      nameRu: "Северная модернизация",
      active: true,
    });
    expect(apiClient.fetchReferenceWorkspaces).toHaveBeenCalledTimes(2);
  });

  it("surfaces action errors from project updates", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.fetchReferenceWorkspaces).mockResolvedValue([buildWorkspace()]);
    vi.mocked(apiClient.fetchReferenceProjects).mockResolvedValue([]);
    vi.mocked(apiClient.updateReferenceProject).mockRejectedValue(new ApiClientError(
      "Reference workspace does not exist",
      {
        code: "reference_project.workspace_not_found",
        status: 400,
      },
    ));

    render(<ReferenceDataHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchReferenceProjects).toHaveBeenCalled();
    });

    await user.click(screen.getByText("update-project"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Reference workspace does not exist");
    });
  });
});
