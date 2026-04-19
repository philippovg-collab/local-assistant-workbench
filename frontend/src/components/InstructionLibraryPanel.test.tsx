import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { InstructionLibraryPanel } from "./InstructionLibraryPanel";
import type { InstructionDetail, InstructionSummary } from "../types";

const summary: InstructionSummary = {
  id: "instruction-1",
  title: "Existing instruction",
  category: "system",
  scopeLevel: "chat_scenario",
  scopeTargetId: null,
  revision: 2,
  active: true,
  preview: "Existing preview",
  createdAt: "2026-04-19T00:00:00Z",
};

const detail: InstructionDetail = {
  id: "instruction-1",
  title: "Existing instruction",
  category: "system",
  content: "Existing content",
  scopeLevel: "chat_scenario",
  scopeTargetId: null,
  revision: 2,
  active: true,
  createdAt: "2026-04-19T00:00:00Z",
};

describe("InstructionLibraryPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps add instruction visible and opens edit in a sheet", async () => {
    const user = userEvent.setup();
    const onUpdateInstruction = vi.fn().mockResolvedValue(undefined);

    render(
      <InstructionLibraryPanel
        actionError={null}
        deletingInstructionId={null}
        detailError={null}
        error={null}
        instructions={[summary]}
        isLoading={false}
        isLoadingDetail={false}
        message={null}
        onCreateInstruction={vi.fn()}
        onDeleteInstruction={vi.fn()}
        onLoadInstruction={vi.fn(async () => detail)}
        onLoadInstructionDiff={vi.fn(async () => null)}
        onLoadInstructionRevisions={vi.fn(async () => [])}
        onRestoreInstructionRevision={vi.fn()}
        onUpdateInstruction={onUpdateInstruction}
        revisionDiff={null}
        revisions={[]}
        selectedInstruction={null}
      />,
    );

    expect(screen.getByText("Добавить инструкцию")).toBeTruthy();
    expect(screen.queryByRole("dialog", { name: "Редактировать инструкцию" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Редактировать" }));

    expect(await screen.findByRole("dialog", { name: "Редактировать инструкцию" })).toBeTruthy();
    expect(screen.getByText("Добавить инструкцию")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Сохранить изменения" }));

    await waitFor(() => {
      expect(onUpdateInstruction).toHaveBeenCalledWith(
        "instruction-1",
        expect.objectContaining({
          title: "Existing instruction",
          content: "Existing content",
        }),
      );
    });
  });
});
