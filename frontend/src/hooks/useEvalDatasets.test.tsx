import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import { useEvalDatasets } from "./useEvalDatasets";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      fetchEvalDatasets: vi.fn(async () => datasets()),
      fetchEvalDataset: vi.fn(async () => datasetDetail()),
      fetchEvalDatasetVersions: vi.fn(async () => versions()),
      createEvalCase: vi.fn(async () => caseDetail({ id: "case-new", revision: 1 })),
      updateEvalCase: vi.fn(async () => caseDetail({ id: "case-1", question: "Updated?", revision: 2 })),
      archiveEvalCase: vi.fn(async () => caseDetail({ id: "case-1", active: false, reviewStatus: "ARCHIVED" })),
      submitEvalCaseReview: vi.fn(async () => caseDetail({ id: "case-1", reviewStatus: "READY_FOR_REVIEW" })),
      reviewEvalCase: vi.fn(async () => caseDetail({ id: "case-1", reviewStatus: "APPROVED" })),
      promoteEvalCase: vi.fn(async () => ({ id: "promotion-1", sourceCaseId: "case-1", targetDatasetId: "dataset-2" })),
      createEvalDatasetVersion: vi.fn(async () => versions()[0]),
    },
  };
});

describe("useEvalDatasets", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads datasets and refreshes selected detail after case lifecycle mutations", async () => {
    const { result } = renderHook(() => useEvalDatasets());

    await waitFor(() => expect(result.current.selectedDatasetId).toBe("dataset-1"));
    expect(result.current.casesById.get("case-1")?.caseKey).toBe("case-exact");

    await act(async () => {
      await result.current.createCase("dataset-1", { caseKey: "case-new", question: "New question?" });
      await result.current.updateCase("case-1", { question: "Updated?" });
      await result.current.submitReview("case-1", { note: "ready" });
      await result.current.reviewCase("case-1", { status: "APPROVED", note: "approved" });
      await result.current.archiveCase("case-1");
      await result.current.promoteCase("case-1", { targetDatasetId: "dataset-2", note: "promote" });
      await result.current.createDatasetVersion("dataset-1", { note: "ship" });
    });

    expect(evalClient.createEvalCase).toHaveBeenCalledWith("dataset-1", { caseKey: "case-new", question: "New question?" });
    expect(evalClient.updateEvalCase).toHaveBeenCalledWith("case-1", { question: "Updated?" });
    expect(evalClient.submitEvalCaseReview).toHaveBeenCalledWith("case-1", { note: "ready" });
    expect(evalClient.reviewEvalCase).toHaveBeenCalledWith("case-1", { status: "APPROVED", note: "approved" });
    expect(evalClient.archiveEvalCase).toHaveBeenCalledWith("case-1");
    expect(evalClient.promoteEvalCase).toHaveBeenCalledWith("case-1", { targetDatasetId: "dataset-2", note: "promote" });
    expect(evalClient.createEvalDatasetVersion).toHaveBeenCalledWith("dataset-1", { note: "ship" });
    expect(evalClient.fetchEvalDatasets).toHaveBeenCalledTimes(8);
    expect(evalClient.fetchEvalDataset).toHaveBeenLastCalledWith("dataset-1", undefined);
  });

  it("keeps mutation errors separate from load errors", async () => {
    vi.mocked(evalClient.createEvalCase).mockRejectedValueOnce(new Error("validation failed"));
    const { result } = renderHook(() => useEvalDatasets());

    await waitFor(() => expect(result.current.loadError).toBeNull());

    await act(async () => {
      await result.current.createCase("dataset-1", { caseKey: "bad", question: "Bad?" });
    });

    expect(result.current.loadError).toBeNull();
    expect(result.current.mutationError).toBe("validation failed");
    expect(result.current.error).toBe(result.current.mutationError);
  });
});

function datasets() {
  return [
    { id: "dataset-1", datasetKey: "golden", kind: "GOLDEN", status: "ACTIVE", version: "v1", caseCount: 1 },
    { id: "dataset-2", datasetKey: "smoke", kind: "SMOKE", status: "ACTIVE", version: "v1", caseCount: 0 },
  ] as const;
}

function versions() {
  return [{ id: "version-1", version: "v1", caseCount: 1 }] as const;
}

function datasetDetail() {
  return {
    dataset: datasets()[0],
    cases: [caseDetail({ id: "case-1" })],
    reviews: [],
  };
}

function caseDetail(overrides: Record<string, unknown> = {}) {
  return {
    active: true,
    caseKey: "case-exact",
    expectedMode: "ANSWER",
    id: "case-1",
    question: "What is the answer?",
    revision: 1,
    reviewStatus: "DRAFT",
    severity: "BLOCKER",
    ...overrides,
  };
}
