import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChatForm } from "./ChatForm";
import type { ChatFormProps } from "./ChatForm";

const defaultProps = (overrides: Partial<ChatFormProps> = {}): ChatFormProps => ({
  models: [{ name: "qwen2.5:7b" }],
  modelsError: null,
  selectedModel: "qwen2.5:7b",
  onModelChange: vi.fn(),
  answerMode: "brief",
  onAnswerModeChange: vi.fn(),
  temporaryInstructionLabel: "Временная инструкция",
  temporaryInstruction: "",
  temporaryInstructionRows: 2,
  onTemporaryInstructionChange: vi.fn(),
  promptLabel: "Prompt",
  prompt: "Привет",
  promptRows: 3,
  onPromptChange: vi.fn(),
  instructions: [],
  selectedInstructionIds: [],
  onToggleInstruction: vi.fn(),
  instructionEmptyStateMessage: "Нет инструкций",
  isSubmitting: false,
  isSubmitDisabled: false,
  submitIdleLabel: "Отправить",
  submitBusyLabel: "Отправляем",
  helperText: "Готово",
  error: null,
  onSubmit: vi.fn(async () => undefined),
  ...overrides,
});

describe("ChatForm", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders DeepSeek when it is returned by the model catalog", async () => {
    const user = userEvent.setup();
    const onModelChange = vi.fn();

    render(
      <ChatForm
        {...defaultProps({
          models: [{ name: "qwen2.5:7b" }, { name: "deepseek-r1:8b" }],
          onModelChange,
        })}
      />,
    );

    await user.click(screen.getByRole("combobox", { name: "Модель" }));
    await user.click(screen.getByRole("option", { name: "deepseek-r1:8b" }));

    expect(onModelChange).toHaveBeenCalledWith("deepseek-r1:8b");
  });

  it("does not present the selected fallback model as available when the catalog is unavailable", () => {
    render(
      <ChatForm
        {...defaultProps({
          models: [],
          modelsError: "Unable to reach the local LLM provider",
          selectedModel: "qwen2.5:7b",
        })}
      />,
    );

    const modelSelect = screen.getByRole("combobox", { name: "Модель" }) as HTMLButtonElement;
    const submitButton = screen.getByRole("button", { name: "Отправить" }) as HTMLButtonElement;

    expect(modelSelect.disabled).toBe(true);
    expect(modelSelect.textContent).toContain("Список моделей недоступен");
    expect(screen.queryByText("qwen2.5:7b")).toBeNull();
    expect(screen.getByRole("alert").textContent).toContain("Unable to reach the local LLM provider");
    expect(submitButton.disabled).toBe(true);
  });
});
