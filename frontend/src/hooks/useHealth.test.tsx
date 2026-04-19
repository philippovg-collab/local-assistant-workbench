import { cleanup, render, screen, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import { useHealth } from "./useHealth";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");

  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchHealth: vi.fn(),
    },
  };
});

function HealthHarness() {
  const { health, error } = useHealth();

  return (
    <section>
      <output data-testid="health-status">{health?.status ?? ""}</output>
      <output data-testid="health-error">{error ?? ""}</output>
    </section>
  );
}

describe("useHealth", () => {
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

  it("polls health while the page is visible", async () => {
    vi.mocked(apiClient.fetchHealth)
      .mockResolvedValueOnce({
        application: "spring-backend",
        status: "UP",
        timestamp: "2026-04-17T10:00:00Z",
      })
      .mockResolvedValueOnce({
        application: "spring-backend",
        status: "DEGRADED",
        timestamp: "2026-04-17T10:00:30Z",
      });

    await act(async () => {
      render(<HealthHarness />);
      await Promise.resolve();
    });

    expect(apiClient.fetchHealth).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("health-status").textContent).toBe("UP");

    await act(async () => {
      vi.advanceTimersByTime(30_000);
      await Promise.resolve();
    });

    expect(apiClient.fetchHealth).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("health-status").textContent).toBe("DEGRADED");
  });
});
