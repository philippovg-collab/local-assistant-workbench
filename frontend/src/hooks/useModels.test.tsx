import { cleanup, render, screen, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import { useModels } from "./useModels";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchModels: vi.fn(),
    },
  };
});

function ModelsHarness({ enabled = true }: { enabled?: boolean }) {
  const { models, error } = useModels({ enabled });

  return (
    <section>
      <output data-testid="models-count">{String(models.length)}</output>
      <output data-testid="models-error">{error ?? ""}</output>
    </section>
  );
}

describe("useModels", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("polls models while the page is visible", async () => {
    vi.mocked(apiClient.fetchModels)
      .mockResolvedValueOnce([{ name: "qwen2.5:7b" }])
      .mockResolvedValueOnce([{ name: "qwen2.5:7b" }, { name: "qwen2.5:3b" }]);

    await act(async () => {
      render(<ModelsHarness />);
      await Promise.resolve();
    });

    expect(apiClient.fetchModels).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("models-count").textContent).toBe("1");

    await act(async () => {
      vi.advanceTimersByTime(15_000);
      await Promise.resolve();
    });

    expect(apiClient.fetchModels).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("models-count").textContent).toBe("2");
  });

  it("does not fetch while disabled and keeps cached models across tab-style toggles", async () => {
    vi.mocked(apiClient.fetchModels).mockResolvedValue([{ name: "qwen2.5:7b" }]);

    const { rerender } = render(<ModelsHarness enabled={false} />);

    expect(apiClient.fetchModels).not.toHaveBeenCalled();
    expect(screen.getByTestId("models-count").textContent).toBe("0");

    await act(async () => {
      rerender(<ModelsHarness enabled />);
      await Promise.resolve();
    });

    expect(apiClient.fetchModels).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("models-count").textContent).toBe("1");

    await act(async () => {
      rerender(<ModelsHarness enabled={false} />);
      await Promise.resolve();
    });

    expect(screen.getByTestId("models-count").textContent).toBe("1");

    await act(async () => {
      rerender(<ModelsHarness enabled />);
      await Promise.resolve();
    });

    expect(apiClient.fetchModels).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("models-count").textContent).toBe("1");
  });
});
