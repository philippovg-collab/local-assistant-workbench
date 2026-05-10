import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetClientEventThrottleForTests, setClientEventReporter } from "@/utils/clientEvents";
import { ErrorBoundary } from "./ErrorBoundary";

const BrokenChild = () => {
  throw new Error("render failed");
};

describe("ErrorBoundary", () => {
  let reporter: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetClientEventThrottleForTests();
    vi.restoreAllMocks();
    reporter = vi.fn(() => Promise.resolve());
    setClientEventReporter(reporter);
    vi.spyOn(console, "error").mockImplementation(() => undefined);
  });

  it("renders fallback UI and logs sanitized React errors", async () => {
    render(
      <ErrorBoundary>
        <BrokenChild />
      </ErrorBoundary>,
    );

    expect(screen.getByText("Не удалось отобразить рабочую область")).toBeTruthy();
    await waitFor(() => {
      expect(reporter).toHaveBeenCalledWith(expect.objectContaining({
        message: "render failed",
        severity: "fatal",
        type: "react.error_boundary",
      }));
    });
  });
});
