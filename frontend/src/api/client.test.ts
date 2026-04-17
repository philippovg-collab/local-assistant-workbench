import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "./client";

describe("apiClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("builds curl examples against the backend origin instead of the vite dev server", () => {
    const curl = apiClient.buildCurlExample({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "ping",
      instructionIds: [],
    });

    expect(curl).toContain("http://127.0.0.1:8080/api/chat");
    expect(curl).not.toContain("http://127.0.0.1:5173/api/chat");
  });

  it("uses the same resolved backend origin for fetch requests", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          application: "spring-backend",
          status: "UP",
          timestamp: "2026-04-17T10:00:00Z",
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        },
      ),
    );

    await apiClient.fetchHealth();

    expect(fetchSpy).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/health",
      expect.objectContaining({ signal: undefined }),
    );
  });
});
