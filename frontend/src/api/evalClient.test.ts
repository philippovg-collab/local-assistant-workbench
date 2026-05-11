import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "./evalClient";

describe("evalClient", () => {
  beforeEach(() => {
    stubWindowLocation({
      origin: "http://127.0.0.1:5173",
      port: "5173",
      protocol: "http:",
      hostname: "127.0.0.1",
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("uses committed eval routes, methods, query params, and JSON bodies", async () => {
    const responses = [
      jsonResponse([]),
      jsonResponse({ dataset: { id: "dataset-1" }, cases: [], reviews: [] }),
      jsonResponse([]),
      jsonResponse({ id: "case-1", active: true, revision: 1 }),
      jsonResponse({ id: "case-1", active: true, revision: 2 }),
      jsonResponse({ id: "case-1", active: true, revision: 2 }),
      jsonResponse({ id: "promotion-1", sourceCaseRevision: 2, targetCaseRevision: 1 }),
      jsonResponse([]),
      jsonResponse({ id: "run-1", items: [] }),
      jsonResponse([{ runItemId: "item-1", artifactType: "SCORER_OUTPUT", payload: {} }]),
      jsonResponse([]),
      jsonResponse({ configHash: "cfg-1", executionConfig: { configHash: "cfg-1" } }),
      jsonResponse({ id: "compare-1", status: "COMPATIBLE" }),
      jsonResponse({ id: "case-2", active: true, revision: 1 }),
      jsonResponse({ id: "run-2", status: "QUEUED" }),
      jsonResponse({ id: "run-3", status: "QUEUED" }, 202),
      jsonResponse({ id: "run-3", status: "COMPLETED" }),
    ];
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() =>
      Promise.resolve(responses.shift() ?? jsonResponse({})),
    );

    await evalClient.fetchEvalDatasets();
    await evalClient.fetchEvalDataset("dataset 1");
    await evalClient.fetchEvalDatasetVersions("dataset 1");
    await evalClient.createEvalCase("dataset 1", { caseKey: "case-a", question: "Question?" });
    await evalClient.updateEvalCase("case 1", { question: "Updated?" });
    await evalClient.reviewEvalCase("case 1", { status: "APPROVED", note: "ok" });
    await evalClient.promoteEvalCase("case 1", { targetDatasetId: "golden-1", note: "ship" });
    await evalClient.fetchEvalRuns();
    await evalClient.fetchEvalRun("run 1");
    await evalClient.fetchEvalRunItemArtifacts("run 1", "item 1");
    await evalClient.fetchEvalSnapshots();
    await evalClient.resolveEvalExecutionConfig({ datasetId: "dataset-1", corpusSnapshotId: "snapshot-1" });
    await evalClient.createEvalCompare({ baselineRunId: "run-1", candidateRunId: "run-2" });
    await evalClient.createEvalCandidateFromChatRun({ chatRunId: "chat-1", question: "Question?" });
    await evalClient.createRetrievalEvalRun({ datasetId: "dataset-1", corpusSnapshotId: "snapshot-1" });
    await evalClient.createE2EEvalRun({ datasetId: "dataset-1", corpusSnapshotId: "snapshot-1", model: "qwen2.5:7b" });
    await evalClient.reconcileEvalRun("run 3");

    const calls = fetchSpy.mock.calls;
    expect(calls.map(([url]) => String(url))).toEqual([
      "http://127.0.0.1:8080/api/evals/datasets",
      "http://127.0.0.1:8080/api/evals/datasets/dataset%201",
      "http://127.0.0.1:8080/api/evals/datasets/dataset%201/versions",
      "http://127.0.0.1:8080/api/evals/datasets/dataset%201/cases",
      "http://127.0.0.1:8080/api/evals/cases/case%201",
      "http://127.0.0.1:8080/api/evals/cases/case%201/reviews",
      "http://127.0.0.1:8080/api/evals/cases/case%201/promote",
      "http://127.0.0.1:8080/api/evals/runs",
      "http://127.0.0.1:8080/api/evals/runs/run%201",
      "http://127.0.0.1:8080/api/evals/runs/run%201/items/item%201/artifacts",
      "http://127.0.0.1:8080/api/evals/snapshots",
      "http://127.0.0.1:8080/api/evals/execution-configs/resolve",
      "http://127.0.0.1:8080/api/evals/compares",
      "http://127.0.0.1:8080/api/evals/candidates/from-chat-run",
      "http://127.0.0.1:8080/api/evals/runs/retrieval",
      "http://127.0.0.1:8080/api/evals/runs/e2e",
      "http://127.0.0.1:8080/api/evals/runs/run%203/reconcile",
    ]);
    expect(calls.map(([, init]) => (init as RequestInit).method ?? "GET")).toEqual([
      "GET",
      "GET",
      "GET",
      "POST",
      "PATCH",
      "POST",
      "POST",
      "GET",
      "GET",
      "GET",
      "GET",
      "POST",
      "POST",
      "POST",
      "POST",
      "POST",
      "POST",
    ]);
    expect(JSON.parse(String((calls[3]?.[1] as RequestInit).body))).toEqual({ caseKey: "case-a", question: "Question?" });
    expect(JSON.parse(String((calls[11]?.[1] as RequestInit).body))).toEqual({
      datasetId: "dataset-1",
      corpusSnapshotId: "snapshot-1",
    });
    expect(JSON.parse(String((calls[12]?.[1] as RequestInit).body))).toEqual({
      baselineRunId: "run-1",
      candidateRunId: "run-2",
    });
    for (const [, init] of calls) {
      expect((init as RequestInit).credentials).toBe("include");
    }
  });
});

function stubWindowLocation(location: Pick<Location, "origin" | "port" | "protocol" | "hostname">) {
  vi.stubGlobal(
    "window",
    Object.create(window, {
      location: {
        value: location,
      },
    }),
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
    },
  });
}
