import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import type { ChatAuditRunDetail } from "@/types";
import { EMPTY_KNOWLEDGE_SCOPE_RESOLVED, EMPTY_RETRIEVAL_TRACE } from "@/utils/workbenchPresentation";
import { useEvalCandidatePromotion } from "./useEvalCandidatePromotion";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      createEvalCandidateFromChatRun: vi.fn(async () => ({
        id: "case-1",
        datasetId: "candidate-dataset",
        active: true,
        revision: 1,
      })),
    },
  };
});

function Harness() {
  const promotion = useEvalCandidatePromotion();

  return (
    <section>
      <output data-testid="candidate">{promotion.candidate?.id ?? "none"}</output>
      <button type="button" onClick={() => void promotion.createCandidate(runDetail())}>create</button>
    </section>
  );
}

describe("useEvalCandidatePromotion", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("creates a candidate from chat audit detail through the eval API", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "create" }));

    await waitFor(() => expect(screen.getByTestId("candidate").textContent).toBe("case-1"));
    expect(evalClient.createEvalCandidateFromChatRun).toHaveBeenCalledWith(expect.objectContaining({
      chatRunId: "chat-run-1",
      failureReason: "retrieval weak",
      question: "Why did retrieval fail?",
      tags: ["audit", "rag"],
    }));
    expect(vi.mocked(evalClient.createEvalCandidateFromChatRun).mock.calls[0]?.[0]).toEqual(expect.objectContaining({
      artifacts: expect.objectContaining({
        answer: "Weak answer",
        model: "qwen2.5:7b",
      }),
      originalSources: expect.arrayContaining([
        expect.objectContaining({ materialId: "material-1" }),
      ]),
    }));
  });
});

function runDetail(): ChatAuditRunDetail {
  return {
    answer: "Weak answer",
    answerMode: "brief",
    contextStatus: "ready",
    createdAt: "2026-05-11T00:00:00Z",
    failureCode: null,
    failureMessage: "retrieval weak",
    id: "chat-run-1",
    instructionTrace: [],
    knowledgeScopeResolved: EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
    mode: "rag",
    model: "qwen2.5:7b",
    prompt: "Why did retrieval fail?",
    retrievalTrace: EMPTY_RETRIEVAL_TRACE,
    sources: [
      {
        chunkId: "transient-chunk",
        chunkIndex: 0,
        confidence: 0.4,
        excerpt: "Weak evidence",
        matchedTerms: [],
        materialId: "material-1",
        openSourceUrl: null,
        score: 42,
        title: "Material",
      },
    ],
  };
}
