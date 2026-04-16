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

function InstructionsHookHarness() {
  const instructions = useInstructions();

  return (
    <section>
      <button
        type="button"
        onClick={() =>
          swallow(instructions.createInstruction({
            title: "",
            category: "system",
            content: "Rule",
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
          }))
        }
      >
        create-instruction
      </button>

      <output data-testid="load-error">{instructions.error ?? ""}</output>
      <output data-testid="action-error">{instructions.actionError ?? ""}</output>
      <output data-testid="message">{instructions.message ?? ""}</output>
    </section>
  );
}

describe("useInstructions", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
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
