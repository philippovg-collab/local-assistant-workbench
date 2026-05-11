import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import { useEvalCompare } from "./useEvalCompare";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      createEvalCompare: vi.fn(async () => ({
        id: "compare-1",
        baselineRunId: "run-1",
        candidateRunId: "run-2",
        status: "COMPATIBLE",
        compatibilityStatus: "COMPATIBLE",
        compatibilityReasons: [],
        summary: {
          overallVerdict: "PASS",
          metricSummary: {
            answer_correctness: {
              metric: "answer_correctness",
              baselineValue: 0.5,
              candidateValue: 1,
              delta: 0.5,
              direction: "HIGHER_IS_BETTER",
              verdict: "IMPROVED",
              sampleSize: 1,
              nonScorableCount: 0,
            },
          },
        },
      })),
    },
  };
});

describe("useEvalCompare", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("uses populated backend metricSummary and overall verdict", async () => {
    const { result } = renderHook(() => useEvalCompare({
      enabled: true,
      runs: [
        { id: "run-1", status: "COMPLETED", items: [], summary: {} },
        { id: "run-2", status: "COMPLETED", items: [], summary: {} },
      ],
    }));

    await waitFor(() => expect(result.current.baselineRunId).toBe("run-1"));

    await act(async () => {
      await result.current.createCompare();
    });

    expect(evalClient.createEvalCompare).toHaveBeenCalledWith({
      baselineRunId: "run-1",
      candidateRunId: "run-2",
    });
    expect(result.current.overallVerdict).toBe("PASS");
    expect(result.current.metricSummary.answer_correctness).toMatchObject({
      baselineValue: 0.5,
      candidateValue: 1,
      verdict: "IMPROVED",
    });
    expect(result.current.metricSummaryError).toBeNull();
  });

  it("treats compatible compares with empty metricSummary as an error state", async () => {
    vi.mocked(evalClient.createEvalCompare).mockResolvedValueOnce({
      id: "compare-empty",
      baselineRunId: "run-1",
      candidateRunId: "run-2",
      status: "COMPATIBLE",
      compatibilityStatus: "COMPATIBLE",
      compatibilityReasons: [],
      summary: { overallVerdict: "PASS", metricSummary: {} },
    });
    const { result } = renderHook(() => useEvalCompare({
      enabled: true,
      runs: [
        { id: "run-1", status: "COMPLETED", items: [], summary: {} },
        { id: "run-2", status: "COMPLETED", items: [], summary: {} },
      ],
    }));

    await waitFor(() => expect(result.current.candidateRunId).toBe("run-2"));

    await act(async () => {
      await result.current.createCompare();
    });

    expect(result.current.metricSummaryError).toContain("missing metricSummary");
    expect(result.current.error).toBe(result.current.metricSummaryError);
  });
});
