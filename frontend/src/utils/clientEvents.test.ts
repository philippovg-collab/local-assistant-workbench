import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/api/errors";
import {
  installGlobalClientEventHandlers,
  logClientEvent,
  payloadFromUnknown,
  resetClientEventThrottleForTests,
  sanitizeClientEvent,
  setClientEventReporter,
} from "./clientEvents";

describe("clientEvents", () => {
  let reporter: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetClientEventThrottleForTests();
    vi.restoreAllMocks();
    reporter = vi.fn(() => Promise.resolve());
    setClientEventReporter(reporter);
  });

  it("sanitizes secrets and clips noisy fields before sending", () => {
    const event = sanitizeClientEvent({
      severity: "error",
      type: "window.error",
      message: `Authorization: Bearer secret-token ${"x".repeat(3_000)}`,
      metadata: {
        password: "top-secret",
      },
    });

    expect(event.message).toContain("Authorization: Bearer [REDACTED]");
    expect(event.message).toContain("[truncated]");
    expect(event.message).not.toContain("secret-token");
    expect(event.metadata).toEqual({
      password: "[REDACTED]",
    });
  });

  it("extracts backend requestId from ApiClientError metadata", () => {
    const payload = payloadFromUnknown(new ApiClientError("Backend failed", {
      code: "internal.unexpected_error",
      requestId: "req-500",
      status: 500,
      timestamp: "2026-05-10T00:00:00Z",
    }));

    expect(payload.requestId).toBe("req-500");
    expect(payload.metadata).toMatchObject({
      apiCode: "internal.unexpected_error",
      apiRequestId: "req-500",
      apiStatus: 500,
    });
  });

  it("throttles client events to ten per minute", () => {
    for (let index = 0; index < 11; index += 1) {
      logClientEvent({
        severity: "error",
        type: "test.event",
        message: `event-${index}`,
      });
    }

    expect(reporter).toHaveBeenCalledTimes(10);
  });

  it("logs unhandled rejections with ApiClientError requestId", () => {
    const cleanup = installGlobalClientEventHandlers();
    const event = new Event("unhandledrejection") as PromiseRejectionEvent;
    Object.defineProperty(event, "reason", {
      value: new ApiClientError("Request failed", {
        code: "internal.unexpected_error",
        requestId: "req-777",
        status: 500,
      }),
    });

    window.dispatchEvent(event);

    expect(reporter).toHaveBeenCalledWith(expect.objectContaining({
      requestId: "req-777",
      type: "window.unhandledrejection",
      metadata: expect.objectContaining({
        apiRequestId: "req-777",
      }),
    }));

    cleanup();
  });
});
