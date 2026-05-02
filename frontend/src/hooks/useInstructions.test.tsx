import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiClient } from "../api/client";
import { useInstructions } from "./useInstructions";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      createInstruction: vi.fn(),
      deleteInstruction: vi.fn(),
      fetchInstructions: vi.fn(),
    },
  };
});

function swallow(promise: Promise<unknown>) {
  void promise.catch(() => undefined);
}

function InstructionsHookHarness({ enabled = true }: { enabled?: boolean }) {
  const instructions = useInstructions({ enabled });

  return (
    <section>
      <button
        type="button"
        onClick={() =>
          swallow(instructions.createInstruction({
            title: "",
            category: "system",
            content: "Rule",
            scopeLevel: "chat_scenario",
          }))
        }
      >
        create-invalid-title
      </button>

      <button
        type="button"
        onClick={() =>
          swallow(instructions.createInstruction({
            title: "Debug",
            category: "system",
            content: "Rule",
            scopeLevel: "chat_scenario",
          }))
        }
      >
        create-instruction
      </button>

      <output data-testid="load-error">{instructions.error ?? ""}</output>
      <output data-testid="action-error">{instructions.actionError ?? ""}</output>
      <output data-testid="message">{instructions.message ?? ""}</output>
      <output data-testid="instruction-count">{instructions.instructions.length}</output>
      <output data-testid="is-loading">{String(instructions.isLoading)}</output>
    </section>
  );
}

describe("useInstructions", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not fetch while disabled and loads once when enabled", async () => {
    vi.mocked(apiClient.fetchInstructions).mockResolvedValue([
      {
        id: "instruction-1",
        title: "Debug",
        category: "system",
        scopeLevel: "chat_scenario",
        revision: 1,
        active: true,
        createdAt: "2026-04-16T10:00:00Z",
        preview: "Rule",
      },
    ]);

    const { rerender } = render(<InstructionsHookHarness enabled={false} />);

    expect(screen.getByTestId("is-loading").textContent).toBe("false");
    expect(apiClient.fetchInstructions).not.toHaveBeenCalled();

    rerender(<InstructionsHookHarness enabled />);

    await waitFor(() => {
      expect(screen.getByTestId("instruction-count").textContent).toBe("1");
    });

    expect(apiClient.fetchInstructions).toHaveBeenCalledTimes(1);

    rerender(<InstructionsHookHarness enabled={false} />);
    expect(screen.getByTestId("instruction-count").textContent).toBe("1");

    rerender(<InstructionsHookHarness enabled />);
    expect(apiClient.fetchInstructions).toHaveBeenCalledTimes(1);
  });

  it("shows requestId for unexpected instruction load failures", async () => {
    vi.mocked(apiClient.fetchInstructions).mockRejectedValue(
      new ApiClientError("Unexpected server error", {
        code: "internal.unexpected_error",
        requestId: "req-123",
        status: 500,
      }),
    );

    render(<InstructionsHookHarness />);

    await waitFor(() => {
      expect(screen.getByTestId("load-error").textContent).toContain("req-123");
    });
  });

  it("translates validation errors for instruction creation", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchInstructions).mockResolvedValue([]);
    vi.mocked(apiClient.createInstruction).mockRejectedValue(
      new ApiClientError("Field 'title' must not be blank", {
        code: "instruction.invalid_title",
        status: 400,
      }),
    );

    render(<InstructionsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchInstructions).toHaveBeenCalled();
    });

    await user.click(screen.getByText("create-invalid-title"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("Укажи название инструкции");
    });
  });

  it("translates storage failures for instruction operations", async () => {
    const user = userEvent.setup();

    vi.mocked(apiClient.fetchInstructions).mockResolvedValue([]);
    vi.mocked(apiClient.createInstruction).mockRejectedValue(
      new ApiClientError("Unable to write instructions record", {
        code: "instructions.storage_write_failed",
        status: 500,
      }),
    );

    render(<InstructionsHookHarness />);

    await waitFor(() => {
      expect(apiClient.fetchInstructions).toHaveBeenCalled();
    });

    await user.click(screen.getByText("create-instruction"));

    await waitFor(() => {
      expect(screen.getByTestId("action-error").textContent).toContain("локальной библиотекой инструкций");
    });
  });
});
