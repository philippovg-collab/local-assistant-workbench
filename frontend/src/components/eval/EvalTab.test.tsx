import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import { EvalTab } from "./EvalTab";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      fetchEvalDatasets: vi.fn(async () => datasets()),
      fetchEvalDataset: vi.fn(async () => datasetDetail()),
      fetchEvalDatasetVersions: vi.fn(async () => [{ id: "version-1", version: "v1", caseCount: 2 }]),
      fetchEvalRuns: vi.fn(async () => runs()),
      fetchEvalSnapshots: vi.fn(async () => [{ id: "snapshot-1", snapshotKey: "snap-main", totalItemCount: 5, activeItemCount: 5, readyItemCount: 5, supersededItemCount: 0 }]),
      fetchEvalRun: vi.fn(async () => runDetail()),
      fetchEvalRunItemArtifacts: vi.fn(async () => [
        { artifactType: "CHAT_RUN_RESULT", payload: { answer: "Structured answer" }, runItemId: "item-1" },
      ]),
      createRetrievalEvalRun: vi.fn(async () => ({
        ...runs()[0],
        id: "run-started",
        status: "QUEUED",
      })),
      resolveEvalExecutionConfig: vi.fn(async () => ({
        configHash: "cfg-resolved",
        executionConfig: { configHash: "cfg-resolved" },
      })),
      reviewEvalCase: vi.fn(async () => ({
        ...datasetDetail().cases[1],
        reviewStatus: "APPROVED",
      })),
      createEvalCompare: vi.fn(async () => ({
        id: "compare-1",
        baselineRunId: "run-1",
        candidateRunId: "run-2",
        status: "INCOMPATIBLE",
        compatibilityStatus: "BLOCKED",
        compatibilityReasons: [
          { code: "dataset_version_mismatch", message: "Dataset version differs", status: "BLOCKED" },
        ],
        summary: {},
      })),
    },
  };
});

describe("EvalTab", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders datasets, run detail, start dialog, and backend compare incompatibility", async () => {
    const user = userEvent.setup();
    render(<EvalTab />);

    await waitFor(() => expect(screen.getAllByText("Golden QA").length).toBeGreaterThan(0));
    expect(screen.getByText("активные кейсы")).toBeTruthy();
    expect(await screen.findByText("case-exact")).toBeTruthy();
    await user.click(screen.getAllByRole("button", { name: "Одобрить" })[0]);
    await waitFor(() => expect(evalClient.reviewEvalCase).toHaveBeenCalledWith("case-1", {
      note: "Approved from Eval UI",
      status: "APPROVED",
    }));

    await user.click(screen.getByRole("button", { name: "Запуски" }));
    expect(await screen.findByText("OUTPUT_FORMAT_ERROR")).toBeTruthy();
    expect(screen.getAllByText("ошибок").length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Запустить" }));
    expect(await screen.findByText("Запустить оценку")).toBeTruthy();
    const startButtons = screen.getAllByRole("button", { name: "Запустить" });
    await user.click(startButtons[startButtons.length - 1]);
    await waitFor(() => expect(evalClient.createRetrievalEvalRun).toHaveBeenCalledWith(expect.objectContaining({
      corpusSnapshotId: "snapshot-1",
      datasetId: "dataset-1",
      datasetVersion: "v1",
    })));

    await user.click(screen.getAllByRole("button", { name: "Открыть" })[0]);
    expect(await screen.findByText("Structured answer")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Закрыть" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Элемент оценки" })).toBeNull());

    await user.click(screen.getByRole("button", { name: "Сравнение" }));
    await user.click(screen.getByRole("button", { name: "Сравнить" }));

    await waitFor(() => expect(evalClient.createEvalCompare).toHaveBeenCalledWith({
      baselineRunId: "run-1",
      candidateRunId: "run-2",
    }));
    expect(await screen.findByText("dataset_version_mismatch")).toBeTruthy();
  });

  it("renders compare verdict and metric delta rows", async () => {
    vi.mocked(evalClient.createEvalCompare).mockResolvedValueOnce({
      id: "compare-2",
      baselineRunId: "run-1",
      candidateRunId: "run-2",
      status: "COMPATIBLE",
      compatibilityStatus: "COMPATIBLE",
      compatibilityReasons: [],
      summary: {
        overallVerdict: "FAIL",
        metricSummary: {
          mrr: {
            metric: "mrr",
            baselineValue: 1,
            candidateValue: 0.25,
            delta: -0.75,
            direction: "HIGHER_IS_BETTER",
            verdict: "REGRESSED",
            sampleSize: 1,
            nonScorableCount: 0,
          },
          retrieval_no_results_rate: {
            metric: "retrieval_no_results_rate",
            baselineValue: 0,
            candidateValue: 0,
            delta: 0,
            direction: "LOWER_IS_BETTER",
            verdict: "UNCHANGED",
            sampleSize: 1,
            nonScorableCount: 0,
          },
        },
      },
    });
    const user = userEvent.setup();
    render(<EvalTab focus={{ view: "compare", nonce: 1 }} />);

    await user.click(await screen.findByRole("button", { name: "Сравнить" }));

    expect(await screen.findByText("FAIL")).toBeTruthy();
    expect(await screen.findByText("mrr")).toBeTruthy();
    expect(await screen.findByText("REGRESSED")).toBeTruthy();
    expect(await screen.findByText("-0.75")).toBeTruthy();
    expect(await screen.findByText("retrieval_no_results_rate")).toBeTruthy();
  });

  it("renders compatible compare with empty metricSummary as a release-proof error", async () => {
    vi.mocked(evalClient.createEvalCompare).mockResolvedValueOnce({
      id: "compare-empty",
      baselineRunId: "run-1",
      candidateRunId: "run-2",
      status: "COMPATIBLE",
      compatibilityStatus: "COMPATIBLE",
      compatibilityReasons: [],
      summary: { overallVerdict: "PASS", metricSummary: {} },
    });
    const user = userEvent.setup();
    render(<EvalTab focus={{ view: "compare", nonce: 2 }} />);

    await user.click(await screen.findByRole("button", { name: "Сравнить" }));

    expect(await screen.findByText("В совместимом сравнении нет metricSummary")).toBeTruthy();
    expect(await screen.findByText(/доказательство релиза неполное/i)).toBeTruthy();
  });
});

function datasets() {
  return [
    {
      id: "dataset-1",
      datasetKey: "golden-qa",
      name: "Golden QA",
      description: "Regression cases",
      kind: "GOLDEN",
      status: "ACTIVE",
      tags: ["golden"],
      version: "v1",
      caseCount: 2,
      createdAt: "2026-05-11T00:00:00Z",
      updatedAt: "2026-05-11T00:00:00Z",
    },
    {
      id: "dataset-2",
      datasetKey: "candidate-failures",
      name: "Candidates",
      description: "Audit failures",
      kind: "CANDIDATE",
      status: "ACTIVE",
      tags: ["candidate"],
      version: "draft",
      caseCount: 1,
      createdAt: "2026-05-11T00:00:00Z",
      updatedAt: "2026-05-11T00:00:00Z",
    },
  ] as const;
}

function datasetDetail() {
  return {
    dataset: datasets()[0],
    cases: [
      {
        id: "case-1",
        datasetId: "dataset-1",
        caseKey: "case-exact",
        caseType: "EXACT_FACT",
        expectedMode: "ANSWER",
        severity: "BLOCKER",
        question: "What is the tariff?",
        active: true,
        revision: 1,
        reviewStatus: "APPROVED",
        tags: ["golden"],
      },
      {
        id: "case-2",
        datasetId: "dataset-1",
        caseKey: "case-table",
        caseType: "TABLE_QUESTION",
        expectedMode: "ANSWER",
        severity: "MEDIUM",
        question: "Which table row applies?",
        active: true,
        revision: 1,
        reviewStatus: "READY_FOR_REVIEW",
        tags: ["table"],
      },
    ],
    reviews: [],
  };
}

function runs() {
  return [
    {
      id: "run-1",
      datasetId: "dataset-1",
      executionConfig: { datasetId: "dataset-1", datasetVersion: "v1" },
      items: [],
      runKind: "E2E",
      snapshotId: "snapshot-1",
      status: "COMPLETED",
      summary: { verdict: "failed" },
      createdAt: "2026-05-11T00:00:00Z",
      startedAt: "2026-05-11T00:00:00Z",
      completedAt: "2026-05-11T00:00:03Z",
    },
    {
      id: "run-2",
      datasetId: "dataset-1",
      executionConfig: { datasetId: "dataset-1", datasetVersion: "v2" },
      items: [],
      runKind: "E2E",
      snapshotId: "snapshot-1",
      status: "COMPLETED",
      summary: { verdict: "passed" },
      createdAt: "2026-05-11T00:05:00Z",
      startedAt: "2026-05-11T00:05:00Z",
      completedAt: "2026-05-11T00:05:02Z",
    },
  ] as const;
}

function runDetail() {
  return {
    ...runs()[0],
    items: [
      {
        id: "item-1",
        runId: "run-1",
        caseId: "case-1",
        caseRevision: 1,
        status: "FAILED",
        failureCode: "OUTPUT_FORMAT_ERROR",
        failureMessage: "structured output missing",
        scoreSummary: { passed: false },
        artifact: { caseContentHash: "hash-case-1", datasetVersion: "v1" },
        scorer: {},
        createdAt: "2026-05-11T00:00:01Z",
        updatedAt: "2026-05-11T00:00:02Z",
      },
      {
        id: "item-2",
        runId: "run-1",
        caseId: "case-2",
        caseRevision: 1,
        status: "PASSED",
        scoreSummary: { passed: true },
        artifact: {},
        scorer: {},
        createdAt: "2026-05-11T00:00:01Z",
        updatedAt: "2026-05-11T00:00:02Z",
      },
    ],
  };
}
