import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { KnowledgePresetLibraryPanel } from "./KnowledgePresetLibraryPanel";
import type { KnowledgePresetDetail, KnowledgePresetSummary } from "../types";

const summary: KnowledgePresetSummary = {
  id: "preset-1",
  name: "Contracts preset",
  description: "Contract scope",
  revision: 3,
  active: true,
  createdAt: "2026-04-19T00:00:00Z",
};

const detail: KnowledgePresetDetail = {
  ...summary,
  scope: {
    presetIds: [],
    documentClasses: ["contracts"],
    tags: ["premium"],
    workspaceKey: "legal",
    uploadedTodayOnly: false,
  },
};

describe("KnowledgePresetLibraryPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps create preset visible and opens edit in a dialog", async () => {
    const user = userEvent.setup();
    const onUpdatePreset = vi.fn().mockResolvedValue(undefined);

    render(
      <KnowledgePresetLibraryPanel
        actionError={null}
        error={null}
        isLoading={false}
        message={null}
        onCreatePreset={vi.fn()}
        onDeletePreset={vi.fn()}
        onLoadPreset={vi.fn(async () => detail)}
        onLoadRevisionDiff={vi.fn(async () => null)}
        onLoadRevisions={vi.fn(async () => [])}
        onRestoreRevision={vi.fn()}
        onUpdatePreset={onUpdatePreset}
        presets={[summary]}
        revisionDiff={null}
        revisions={[]}
        selectedPreset={null}
      />,
    );

    expect(screen.getByText("Создать preset проекта")).toBeTruthy();
    expect(screen.queryByRole("dialog", { name: "Редактировать knowledge preset" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Редактировать" }));

    expect(await screen.findByRole("dialog", { name: "Редактировать knowledge preset" })).toBeTruthy();
    expect(screen.getByText("Создать preset проекта")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Сохранить изменения" }));

    await waitFor(() => {
      expect(onUpdatePreset).toHaveBeenCalledWith(
        "preset-1",
        expect.objectContaining({
          name: "Contracts preset",
          scope: expect.objectContaining({
            documentClasses: ["contracts"],
            tags: ["premium"],
          }),
        }),
      );
    });
  });
});
