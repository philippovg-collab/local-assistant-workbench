import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import type { ConversationRunDetail } from "../types";
import { useConversationRuns } from "./useConversationRuns";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchConversationRuns: vi.fn(async () => [run()]),
    },
  };
});

function Harness({
  conversationId = "conversation-1",
  enabled = true,
}: {
  conversationId?: string | null;
  enabled?: boolean;
}) {
  const conversationRuns = useConversationRuns(conversationId, { enabled });

  return (
    <section>
      <output data-testid="count">{conversationRuns.runs.length}</output>
      <button type="button" onClick={() => void conversationRuns.loadRuns()}>load</button>
      <button type="button" onClick={() => void conversationRuns.loadRunsFor("conversation-2")}>load-other</button>
    </section>
  );
}

describe("useConversationRuns", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not call runs API while disabled", async () => {
    const user = userEvent.setup();
    render(<Harness enabled={false} />);

    await user.click(screen.getByRole("button", { name: "load" }));
    await user.click(screen.getByRole("button", { name: "load-other" }));

    expect(screen.getByTestId("count").textContent).toBe("0");
    expect(apiClient.fetchConversationRuns).not.toHaveBeenCalled();
  });

  it("loads runs for the selected conversation when enabled", async () => {
    render(<Harness />);

    await waitFor(() => expect(apiClient.fetchConversationRuns).toHaveBeenCalledWith(
      "conversation-1",
      expect.any(AbortSignal),
    ));
    expect(screen.getByTestId("count").textContent).toBe("1");
  });
});

function run(): ConversationRunDetail {
  return {
    conversationId: "conversation-1",
    runId: "run-1",
    turnNo: 1,
    userPrompt: "Prompt",
    contextAssemblyStatus: "NONE",
    createdAt: "2026-05-10T00:00:00Z",
    status: "COMPLETED",
    completedAt: "2026-05-10T00:00:01Z",
    statusUrl: "/api/chat-runs/run-1/status",
    traceUrl: "/api/chat-runs/run-1/trace",
    resultUrl: "/api/chat-runs/run-1/result",
    cancelUrl: "/api/chat-runs/run-1/cancel",
  };
}
