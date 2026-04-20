import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient, isApiClientError } from "./client";

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
      expect.objectContaining({ credentials: "include", signal: undefined }),
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
      expect.objectContaining({ credentials: "include", signal: undefined }),
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
      expect.objectContaining({ credentials: "include", signal: undefined }),
    );
  });

  it("remembers CSRF details from the session endpoint and sends them on unsafe JSON requests", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            authenticated: true,
            username: "admin",
            roles: ["ROLE_ADMIN"],
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfToken: "csrf-123",
          }),
          {
            status: 200,
            headers: {
              "Content-Type": "application/json",
            },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: "run-1",
            status: "RECEIVED",
            createdAt: "2026-04-19T00:00:00Z",
            traceUrl: "/api/chat-runs/run-1/trace",
            resultUrl: "/api/chat-runs/run-1/result",
          }),
          {
            status: 202,
            headers: {
              "Content-Type": "application/json",
            },
          },
        ),
      );

    await apiClient.fetchSession();
    await apiClient.submitChatRun({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "ping",
      instructionIds: [],
    });

    const submitInit = fetchSpy.mock.calls[1]?.[1] as RequestInit;
    const headers = submitInit.headers as Headers;
    expect(submitInit.credentials).toBe("include");
    expect(headers.get("X-CSRF-TOKEN")).toBe("csrf-123");
  });

  it("supports JSON login and logout session calls", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            authenticated: true,
            username: "admin",
            roles: ["ROLE_ADMIN"],
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfToken: "csrf-login",
          }),
          {
            status: 200,
            headers: {
              "Content-Type": "application/json",
            },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            authenticated: false,
            username: null,
            roles: [],
            csrfHeaderName: "X-CSRF-TOKEN",
            csrfToken: "csrf-logout",
          }),
          {
            status: 200,
            headers: {
              "Content-Type": "application/json",
            },
          },
        ),
      );

    const loginSession = await apiClient.login({ username: "admin", password: "secret" });
    const logoutSession = await apiClient.logout();

    expect(loginSession.authenticated).toBe(true);
    expect(logoutSession.authenticated).toBe(false);
    expect(fetchSpy).toHaveBeenNthCalledWith(
      1,
      "http://127.0.0.1:8080/api/auth/login",
      expect.objectContaining({ credentials: "include", method: "POST" }),
    );
    expect(fetchSpy).toHaveBeenNthCalledWith(
      2,
      "http://127.0.0.1:8080/api/auth/logout",
      expect.objectContaining({ credentials: "include", method: "POST" }),
    );
  });

  it("surfaces 401 responses as ApiClientError values", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "auth.unauthenticated",
          message: "Authentication is required",
        }),
        {
          status: 401,
          headers: {
            "Content-Type": "application/json",
          },
        },
      ),
    );

    try {
      await apiClient.fetchHealth();
      throw new Error("Expected fetchHealth to reject");
    } catch (error) {
      expect(isApiClientError(error)).toBe(true);
      if (isApiClientError(error)) {
        expect(error.status).toBe(401);
        expect(error.code).toBe("auth.unauthenticated");
      }
    }
  });

  it("builds curl examples with a quoted here-doc instead of single-quoted JSON", () => {
    const curl = apiClient.buildCurlExample({
      mode: "direct",
      model: "qwen2.5:7b",
      prompt: "quote ' and command ; echo nope",
      instructionIds: [],
    });

    expect(curl).toContain("--data-binary @- <<'JSON'");
    expect(curl).toContain("quote ' and command ; echo nope");
    expect(curl).not.toContain("-d '{");
  });
});
