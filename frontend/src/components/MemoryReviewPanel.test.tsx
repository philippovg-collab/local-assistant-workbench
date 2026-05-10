import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/api/client";
import { MemoryReviewPanel } from "./MemoryReviewPanel";
import type { MemoryEntryResponse } from "@/types";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      fetchMemoryEntries: vi.fn(),
      createMemoryEntry: vi.fn(),
      updateMemoryEntry: vi.fn(),
      approveMemoryEntry: vi.fn(),
      rejectMemoryEntry: vi.fn(),
      pinMemoryEntry: vi.fn(),
      unpinMemoryEntry: vi.fn(),
      deleteMemoryEntry: vi.fn(),
    },
  };
});

const pendingEntry: MemoryEntryResponse = {
  id: "memory-1",
  status: "pending_review",
  entryType: "user_preference",
  contentText: "Пользователь предпочитает короткие ответы.",
  normalizedKey: "short-answer",
  workspaceKey: "workspace-a",
  projectKey: "project-a",
  pinned: false,
  confidence: 0.7,
  provenance: { extractor: "explicit-marker-v1" },
  sourceConversationId: "conversation-1",
  sourceRunId: "run-1",
  sourceTurnNo: 3,
  sourceTextPreview: "Запомни: предпочитаю короткие ответы",
  createdAt: "2026-05-10T00:00:00Z",
  updatedAt: "2026-05-10T00:00:00Z",
};

describe("MemoryReviewPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders pending provenance links and calls review actions", async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.fetchMemoryEntries).mockResolvedValue([pendingEntry]);
    vi.mocked(apiClient.approveMemoryEntry).mockResolvedValue({ ...pendingEntry, status: "approved" });
    vi.mocked(apiClient.rejectMemoryEntry).mockResolvedValue({ ...pendingEntry, status: "rejected" });
    vi.mocked(apiClient.updateMemoryEntry).mockResolvedValue({ ...pendingEntry, contentText: "Обновлённая память" });
    vi.mocked(apiClient.deleteMemoryEntry).mockResolvedValue({ ...pendingEntry, status: "deleted", contentText: null });
    vi.stubGlobal("confirm", vi.fn(() => true));

    render(<MemoryReviewPanel enabled workspaceKey="workspace-a" workspaceName="Workspace A" />);

    expect(await screen.findByText("Пользователь предпочитает короткие ответы.")).not.toBeNull();
    expect(screen.getByRole("link", { name: "Context" }).getAttribute("href")).toBe("/api/chat-runs/run-1/context");
    expect(screen.getByRole("link", { name: "Trace" }).getAttribute("href")).toBe("/api/chat-runs/run-1/trace");
    expect(screen.getByRole("link", { name: "Conversation" }).getAttribute("href")).toBe("/api/conversations/conversation-1/runs");

    await user.click(screen.getByRole("button", { name: /Approve/i }));
    await waitFor(() => expect(apiClient.approveMemoryEntry).toHaveBeenCalledWith("memory-1", {}));

    await user.click(screen.getByRole("button", { name: /Reject/i }));
    await waitFor(() => expect(apiClient.rejectMemoryEntry).toHaveBeenCalledWith("memory-1", {}));

    await user.click(screen.getByRole("button", { name: /Edit/i }));
    const editTextarea = screen.getByDisplayValue("Пользователь предпочитает короткие ответы.");
    await user.clear(editTextarea);
    await user.type(editTextarea, "Обновлённая память");
    await user.click(screen.getByRole("button", { name: /Save/i }));
    await waitFor(() => expect(apiClient.updateMemoryEntry).toHaveBeenCalledWith("memory-1", {
      contentText: "Обновлённая память",
    }));

    await user.click(screen.getByRole("button", { name: /Delete/i }));
    await waitFor(() => expect(apiClient.deleteMemoryEntry).toHaveBeenCalledWith("memory-1", {}));
  });

  it("creates manual entries and pins approved entries through the API", async () => {
    const user = userEvent.setup();
    const approvedEntry = { ...pendingEntry, status: "approved" as const, pinned: false };
    vi.mocked(apiClient.fetchMemoryEntries).mockResolvedValue([approvedEntry]);
    vi.mocked(apiClient.createMemoryEntry).mockResolvedValue({ ...pendingEntry, id: "memory-2" });
    vi.mocked(apiClient.pinMemoryEntry).mockResolvedValue({ ...approvedEntry, pinned: true });

    render(<MemoryReviewPanel enabled workspaceKey="workspace-a" workspaceName="Workspace A" />);

    expect(await screen.findByText("Пользователь предпочитает короткие ответы.")).not.toBeNull();
    await user.type(screen.getByLabelText("Content"), "Новая ручная память");
    await user.type(screen.getByLabelText("Project key", { selector: "#memory-draft-project" }), "project-a");
    await user.click(screen.getByRole("button", { name: /Добавить/i }));

    await waitFor(() => expect(apiClient.createMemoryEntry).toHaveBeenCalledWith({
      entryType: "user_preference",
      contentText: "Новая ручная память",
      workspaceKey: "workspace-a",
      projectKey: "project-a",
      provenance: { source: "manual-ui" },
    }));

    await user.click(screen.getByRole("button", { name: /Pin/i }));
    await waitFor(() => expect(apiClient.pinMemoryEntry).toHaveBeenCalledWith("memory-1", {}));
  });
});
