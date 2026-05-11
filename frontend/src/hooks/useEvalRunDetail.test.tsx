import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import { useEvalRunDetail } from "./useEvalRunDetail";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      fetchEvalRun: vi.fn(async () => runDetail()),
      fetchEvalRunItemArtifacts: vi.fn(async () => artifacts()),
    },
  };
});

describe("useEvalRunDetail", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads run detail, selects the highest-priority item, and loads artifacts", async () => {
    const { result } = renderHook(() => useEvalRunDetail("run-1"));

    await waitFor(() => expect(result.current.run?.id).toBe("run-1"));
    expect(result.current.selectedItemId).toBe("item-failed");
    expect(result.current.selectedItem?.failureCode).toBe("OUTPUT_FORMAT_ERROR");

    await waitFor(() => expect(result.current.artifacts).toHaveLength(1));
    expect(evalClient.fetchEvalRun).toHaveBeenCalledWith("run-1", expect.any(AbortSignal));
    expect(evalClient.fetchEvalRunItemArtifacts).toHaveBeenCalledWith("run-1", "item-failed", expect.any(AbortSignal));
  });

  it("reports artifact errors separately from run load errors", async () => {
    vi.mocked(evalClient.fetchEvalRunItemArtifacts).mockRejectedValueOnce(new Error("artifact unavailable"));
    const { result } = renderHook(() => useEvalRunDetail("run-1"));

    await waitFor(() => expect(result.current.artifactError).toBe("artifact unavailable"));

    expect(result.current.loadError).toBeNull();
    expect(result.current.error).toBe(result.current.artifactError);

    await act(async () => {
      result.current.setSelectedItemId("item-passed");
    });

    await waitFor(() => expect(evalClient.fetchEvalRunItemArtifacts).toHaveBeenLastCalledWith(
      "run-1",
      "item-passed",
      expect.any(AbortSignal),
    ));
  });
});

function runDetail() {
  return {
    id: "run-1",
    datasetId: "dataset-1",
    runKind: "E2E",
    snapshotId: "snapshot-1",
    status: "COMPLETED",
    executionConfig: { datasetId: "dataset-1", datasetVersion: "v1" },
    summary: {},
    items: [
      {
        id: "item-passed",
        caseId: "case-2",
        caseRevision: 1,
        status: "PASSED",
        createdAt: "2026-05-11T00:00:01Z",
      },
      {
        id: "item-failed",
        caseId: "case-1",
        caseRevision: 2,
        failureCode: "OUTPUT_FORMAT_ERROR",
        status: "FAILED",
        createdAt: "2026-05-11T00:00:02Z",
      },
    ],
  } as const;
}

function artifacts() {
  return [
    {
      artifactType: "CHAT_RUN_RESULT",
      payload: { answer: "Final answer" },
      runItemId: "item-failed",
    },
  ] as const;
}
