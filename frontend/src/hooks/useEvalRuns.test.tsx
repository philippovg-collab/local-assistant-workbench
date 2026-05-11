import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { evalClient } from "@/api/evalClient";
import type { EvalRun } from "@/types";
import { useEvalRuns } from "./useEvalRuns";

vi.mock("@/api/evalClient", async () => {
  const actual = await vi.importActual<typeof import("@/api/evalClient")>("@/api/evalClient");
  return {
    ...actual,
    evalClient: {
      ...actual.evalClient,
      fetchEvalRuns: vi.fn(async () => []),
      fetchEvalSnapshots: vi.fn(async () => []),
      createRetrievalEvalRun: vi.fn(async () => run({ id: "run-started", status: "QUEUED" })),
      createE2EEvalRun: vi.fn(async () => run({ id: "run-e2e", status: "QUEUED" })),
      reconcileEvalRun: vi.fn(async () => run({ id: "run-started", status: "COMPLETED" })),
      resolveEvalExecutionConfig: vi.fn(async () => ({ configHash: "cfg-1", executionConfig: { configHash: "cfg-1" } })),
    },
  };
});

function Harness({
  enabled = true,
  pollingEnabled = true,
}: {
  enabled?: boolean;
  pollingEnabled?: boolean;
}) {
  const evalRuns = useEvalRuns({ enabled, pollingEnabled });

  return (
    <section>
      <output data-testid="count">{evalRuns.runs.length}</output>
      <output data-testid="selected">{evalRuns.selectedRunId ?? "none"}</output>
      <output data-testid="config">{evalRuns.resolvedConfigHash ?? "none"}</output>
      <output data-testid="mutation-error">{evalRuns.mutationError ?? "none"}</output>
      <button type="button" onClick={() => void evalRuns.loadRuns()}>load</button>
      <button type="button" onClick={() => void evalRuns.startRetrievalRun({ datasetId: "dataset-1" })}>start</button>
      <button type="button" onClick={() => void evalRuns.startE2ERun({ datasetId: "dataset-1", judgeMode: "LLM_JUDGE" })}>start-e2e</button>
      <button type="button" onClick={() => void evalRuns.resolveExecutionConfig({ datasetId: "dataset-1" })}>resolve</button>
    </section>
  );
}

describe("useEvalRuns", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("does not load eval runs while disabled", async () => {
    const user = userEvent.setup();
    render(<Harness enabled={false} />);

    await user.click(screen.getByRole("button", { name: "load" }));
    await user.click(screen.getByRole("button", { name: "start" }));
    await user.click(screen.getByRole("button", { name: "start-e2e" }));

    expect(evalClient.fetchEvalRuns).not.toHaveBeenCalled();
    expect(evalClient.createRetrievalEvalRun).not.toHaveBeenCalled();
    expect(evalClient.createE2EEvalRun).not.toHaveBeenCalled();
    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("loads runs and exposes start/resolve mutations", async () => {
    const user = userEvent.setup();
    vi.mocked(evalClient.fetchEvalRuns).mockResolvedValueOnce([run({ id: "run-1", status: "COMPLETED" })]);
    render(<Harness />);

    await waitFor(() => expect(evalClient.fetchEvalRuns).toHaveBeenCalledWith(expect.any(AbortSignal)));
    expect(screen.getByTestId("count").textContent).toBe("1");
    expect(screen.getByTestId("selected").textContent).toBe("run-1");

    await user.click(screen.getByRole("button", { name: "resolve" }));
    await waitFor(() => expect(screen.getByTestId("config").textContent).toBe("cfg-1"));

    await user.click(screen.getByRole("button", { name: "start" }));
    await waitFor(() => expect(evalClient.createRetrievalEvalRun).toHaveBeenCalledWith({ datasetId: "dataset-1" }));

    await user.click(screen.getByRole("button", { name: "start-e2e" }));
    await waitFor(() => expect(evalClient.createE2EEvalRun).toHaveBeenCalledWith({
      datasetId: "dataset-1",
      judgeMode: "LLM_JUDGE",
    }));
  });

  it("polls active eval runs until list refreshes", async () => {
    vi.useFakeTimers();
    vi.mocked(evalClient.fetchEvalRuns)
      .mockResolvedValueOnce([run({ id: "run-active", status: "RUNNING" })])
      .mockResolvedValueOnce([run({ id: "run-active", status: "COMPLETED" })]);

    render(<Harness />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId("selected").textContent).toBe("run-active");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(evalClient.fetchEvalRuns).toHaveBeenCalledTimes(2);
  });

  it("does not poll active runs while polling is disabled for inactive views", async () => {
    vi.useFakeTimers();
    vi.mocked(evalClient.fetchEvalRuns).mockResolvedValue([run({ id: "run-active", status: "RUNNING" })]);

    render(<Harness pollingEnabled={false} />);

    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });

    expect(evalClient.fetchEvalRuns).toHaveBeenCalledTimes(1);
  });

  it("exposes mutation errors separately from load errors", async () => {
    const user = userEvent.setup();
    vi.mocked(evalClient.createRetrievalEvalRun).mockRejectedValueOnce(new Error("start rejected"));
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "start" }));

    await waitFor(() => expect(screen.getByTestId("mutation-error").textContent).toBe("start rejected"));
  });
});

function run(overrides: Partial<EvalRun> = {}): EvalRun {
  return {
    completedAt: null,
    configHash: null,
    createdAt: "2026-05-11T00:00:00Z",
    datasetId: "dataset-1",
    executionConfig: { datasetId: "dataset-1", datasetVersion: "v1" },
    id: "run-1",
    items: [],
    runKind: "RETRIEVAL_ONLY",
    snapshotId: "snapshot-1",
    startedAt: "2026-05-11T00:00:00Z",
    status: "COMPLETED",
    summary: {},
    updatedAt: "2026-05-11T00:00:00Z",
    ...overrides,
  };
}
