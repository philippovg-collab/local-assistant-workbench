import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import type { ConversationDetail, ConversationSummary } from "../types";
import { useConversations } from "./useConversations";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchConversations: vi.fn(async () => [summary()]),
      fetchConversation: vi.fn(async () => detail()),
      createConversation: vi.fn(async () => detail("created-conversation")),
      patchConversation: vi.fn(async () => detail("archived-conversation")),
    },
  };
});

function Harness({ enabled = true }: { enabled?: boolean }) {
  const conversations = useConversations({
    enabled,
    mode: "rag",
    workspaceKey: "grid",
  });

  return (
    <section>
      <output data-testid="count">{conversations.conversations.length}</output>
      <output data-testid="selected">{conversations.selectedConversationId ?? "none"}</output>
      <button type="button" onClick={() => void conversations.loadConversations()}>load</button>
      <button type="button" onClick={() => void conversations.createConversation({ title: "Created" })}>create</button>
      <button type="button" onClick={() => void conversations.archiveConversation("conversation-1")}>archive</button>
    </section>
  );
}

describe("useConversations", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not call conversation APIs while disabled", async () => {
    const user = userEvent.setup();
    render(<Harness enabled={false} />);

    await user.click(screen.getByRole("button", { name: "load" }));
    await user.click(screen.getByRole("button", { name: "create" }));
    await user.click(screen.getByRole("button", { name: "archive" }));

    expect(screen.getByTestId("count").textContent).toBe("0");
    expect(screen.getByTestId("selected").textContent).toBe("none");
    expect(apiClient.fetchConversations).not.toHaveBeenCalled();
    expect(apiClient.fetchConversation).not.toHaveBeenCalled();
    expect(apiClient.createConversation).not.toHaveBeenCalled();
    expect(apiClient.patchConversation).not.toHaveBeenCalled();
  });

  it("loads list and selected detail when enabled", async () => {
    render(<Harness />);

    await waitFor(() => expect(apiClient.fetchConversations).toHaveBeenCalledWith(
      { workspaceKey: "grid", mode: "rag" },
      expect.any(AbortSignal),
    ));
    await waitFor(() => expect(apiClient.fetchConversation).toHaveBeenCalledWith(
      "conversation-1",
      expect.any(AbortSignal),
    ));
    expect(screen.getByTestId("count").textContent).toBe("1");
    expect(screen.getByTestId("selected").textContent).toBe("conversation-1");
  });
});

function summary(id = "conversation-1"): ConversationSummary {
  return {
    id,
    workspaceKey: "grid",
    title: "Dispatch",
    mode: "rag",
    status: "ACTIVE",
    defaultModel: "qwen2.5:7b",
    defaultAnswerMode: null,
    createdAt: "2026-05-10T00:00:00Z",
    updatedAt: "2026-05-10T00:00:00Z",
    lastRunAt: "2026-05-10T00:00:00Z",
    turnCount: 1,
  };
}

function detail(id = "conversation-1"): ConversationDetail {
  return {
    ...summary(id),
    stickyState: null,
  };
}
