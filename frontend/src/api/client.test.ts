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

  it("requests paginated materials instead of treating the catalog as an unbounded array", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          total: 101,
          offset: 100,
          limit: 100,
          hasMore: false,
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        },
      ),
    );

    const response = await apiClient.fetchMaterials({ offset: 100, limit: 100 });

    expect(response.total).toBe(101);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/materials?offset=100&limit=100",
      expect.objectContaining({ signal: undefined }),
    );
  });

  it("fetches P0 chat run trace details from the trace endpoint", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          id: "run-1",
          mode: "direct",
          status: "COMPLETED",
          createdAt: "2026-04-19T00:00:00Z",
          llmCalls: [],
          events: [],
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        },
      ),
    );

    const response = await apiClient.fetchChatRunTrace("run-1");

    expect(response.status).toBe("COMPLETED");
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/chat-runs/run-1/trace",
      expect.objectContaining({ signal: undefined }),
    );
  });
});
