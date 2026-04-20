import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { InstructionSelector } from "./InstructionSelector";
import type { InstructionSummary } from "@/types";

const buildInstruction = (overrides: Partial<InstructionSummary> = {}): InstructionSummary => ({
  id: "instruction-1",
  title: "Context rule",
  category: "context",
  scopeLevel: "chat_scenario",
  revision: 1,
  active: true,
  preview: "Use internal context rules.",
  createdAt: "2026-04-16T10:00:00Z",
  ...overrides,
});

describe("InstructionSelector", () => {
  afterEach(() => {
    cleanup();
  });

  it("describes runtime prompt application without implying only the base system prompt is used", () => {
    render(
      <InstructionSelector
        emptyStateMessage="Нет инструкций"
        instructions={[buildInstruction()]}
        label="Инструкции"
        selectedInstructionIds={[]}
        onToggleInstruction={vi.fn()}
      />,
    );

    expect(screen.getByText("Выбранные инструкции добавляются в runtime prompt в указанном порядке.")).toBeTruthy();
    expect(screen.getByText(/базовую prompt policy и автоматические assistant\/workspace-инструкции/i)).toBeTruthy();
    expect(screen.queryByText(/только с базовым system prompt/i)).toBeNull();
  });

  it("renders selected instructions in the same order they will be applied", () => {
    render(
      <InstructionSelector
        emptyStateMessage="Нет инструкций"
        instructions={[
          buildInstruction({ id: "instruction-1", title: "First in library", category: "system" }),
          buildInstruction({ id: "instruction-2", title: "Selected first", category: "context" }),
          buildInstruction({ id: "instruction-3", title: "Selected second", category: "user" }),
        ]}
        label="Инструкции"
        selectedInstructionIds={["instruction-2", "instruction-3"]}
        onToggleInstruction={vi.fn()}
      />,
    );

    const appliedOrder = screen.getByText("Порядок применения").closest("div")?.parentElement as HTMLElement;
    const items = within(appliedOrder).getAllByRole("listitem");
    expect(items[0]?.textContent).toContain("Selected first");
    expect(items[1]?.textContent).toContain("Selected second");
  });
});
