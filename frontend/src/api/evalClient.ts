import type {
  CorpusSnapshot,
  CorpusSnapshotDetail,
  CorpusSnapshotItemDetail,
  CreateCorpusSnapshotRequest,
  CreateE2EEvalRunRequest,
  CreateEvalCandidateFromChatRunRequest,
  CreateEvalCaseRequest,
  CreateEvalCaseReviewRequest,
  CreateEvalDatasetRequest,
  CreateEvalDatasetVersionRequest,
  CreateRetrievalEvalRunRequest,
  EvalCase,
  EvalCasePromotion,
  EvalCaseRevision,
  EvalCompareRequest,
  EvalDataset,
  EvalDatasetDetail,
  EvalDatasetSummary,
  EvalDatasetVersion,
  EvalModuleStatusResponse,
  EvalRun,
  EvalRunCompare,
  EvalRunItemArtifact,
  PromoteEvalCaseRequest,
  ResolveEvalExecutionConfigRequest,
  ResolvedEvalExecutionConfig,
  RetrievalEvalPreviewRequest,
  RetrievalEvalPreviewResponse,
  SubmitEvalCaseReviewRequest,
  UpdateEvalCaseRequest,
  UpdateEvalDatasetRequest,
} from "../types";
import { requestJson } from "./apiTransport";

export const evalClient = {
  fetchEvalStatus(signal?: AbortSignal) {
    return requestJson<EvalModuleStatusResponse>("/api/evals/status", { signal });
  },
  fetchEvalDatasets(signal?: AbortSignal) {
    return requestJson<EvalDatasetSummary[]>("/api/evals/datasets", { signal });
  },
  fetchEvalDataset(datasetId: string, signal?: AbortSignal) {
    return requestJson<EvalDatasetDetail>(`/api/evals/datasets/${encodeURIComponent(datasetId)}`, { signal });
  },
  createEvalDataset(input: CreateEvalDatasetRequest) {
    return requestJson<EvalDataset>("/api/evals/datasets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  updateEvalDataset(datasetId: string, input: UpdateEvalDatasetRequest) {
    return requestJson<EvalDataset>(`/api/evals/datasets/${encodeURIComponent(datasetId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  archiveEvalDataset(datasetId: string) {
    return requestJson<EvalDataset>(`/api/evals/datasets/${encodeURIComponent(datasetId)}/archive`, {
      method: "POST",
    });
  },
  fetchEvalDatasetVersions(datasetId: string, signal?: AbortSignal) {
    return requestJson<EvalDatasetVersion[]>(`/api/evals/datasets/${encodeURIComponent(datasetId)}/versions`, { signal });
  },
  createEvalDatasetVersion(datasetId: string, input: CreateEvalDatasetVersionRequest = {}) {
    return requestJson<EvalDatasetVersion>(`/api/evals/datasets/${encodeURIComponent(datasetId)}/versions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  createEvalCase(datasetId: string, input: CreateEvalCaseRequest) {
    return requestJson<EvalCase>(`/api/evals/datasets/${encodeURIComponent(datasetId)}/cases`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  updateEvalCase(caseId: string, input: UpdateEvalCaseRequest) {
    return requestJson<EvalCase>(`/api/evals/cases/${encodeURIComponent(caseId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  fetchEvalCaseRevisions(caseId: string, signal?: AbortSignal) {
    return requestJson<EvalCaseRevision[]>(`/api/evals/cases/${encodeURIComponent(caseId)}/revisions`, { signal });
  },
  submitEvalCaseReview(caseId: string, input: SubmitEvalCaseReviewRequest = {}) {
    return requestJson<EvalCase>(`/api/evals/cases/${encodeURIComponent(caseId)}/submit-review`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  reviewEvalCase(caseId: string, input: CreateEvalCaseReviewRequest) {
    return requestJson<EvalCase>(`/api/evals/cases/${encodeURIComponent(caseId)}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  archiveEvalCase(caseId: string) {
    return requestJson<EvalCase>(`/api/evals/cases/${encodeURIComponent(caseId)}/archive`, {
      method: "POST",
    });
  },
  promoteEvalCase(caseId: string, input: PromoteEvalCaseRequest) {
    return requestJson<EvalCasePromotion>(`/api/evals/cases/${encodeURIComponent(caseId)}/promote`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  createEvalCandidateFromChatRun(input: CreateEvalCandidateFromChatRunRequest) {
    return requestJson<EvalCase>("/api/evals/candidates/from-chat-run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  fetchEvalRuns(signal?: AbortSignal) {
    return requestJson<EvalRun[]>("/api/evals/runs", { signal });
  },
  fetchEvalRun(runId: string, signal?: AbortSignal) {
    return requestJson<EvalRun>(`/api/evals/runs/${encodeURIComponent(runId)}`, { signal });
  },
  fetchEvalRunItemArtifacts(runId: string, itemId: string, signal?: AbortSignal) {
    return requestJson<EvalRunItemArtifact[]>(
      `/api/evals/runs/${encodeURIComponent(runId)}/items/${encodeURIComponent(itemId)}/artifacts`,
      { signal },
    );
  },
  fetchEvalSnapshots(signal?: AbortSignal) {
    return requestJson<CorpusSnapshot[]>("/api/evals/snapshots", { signal });
  },
  createEvalSnapshot(input: CreateCorpusSnapshotRequest = {}) {
    return requestJson<CorpusSnapshotDetail>("/api/evals/snapshots", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  fetchEvalSnapshot(snapshotId: string, signal?: AbortSignal) {
    return requestJson<CorpusSnapshotDetail>(`/api/evals/snapshots/${encodeURIComponent(snapshotId)}`, { signal });
  },
  fetchEvalSnapshotItems(snapshotId: string, input: { offset?: number; limit?: number } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.offset !== undefined) {
      params.set("offset", String(input.offset));
    }
    if (input.limit !== undefined) {
      params.set("limit", String(input.limit));
    }
    const query = params.toString();
    return requestJson<CorpusSnapshotItemDetail[]>(
      `/api/evals/snapshots/${encodeURIComponent(snapshotId)}/items${query ? `?${query}` : ""}`,
      { signal },
    );
  },
  resolveEvalExecutionConfig(input: ResolveEvalExecutionConfigRequest = {}) {
    return requestJson<ResolvedEvalExecutionConfig>("/api/evals/execution-configs/resolve", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  createEvalCompare(input: EvalCompareRequest) {
    return requestJson<EvalRunCompare>("/api/evals/compares", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  previewRetrievalEval(input: RetrievalEvalPreviewRequest = {}) {
    return requestJson<RetrievalEvalPreviewResponse>("/api/evals/preview/retrieval", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  createRetrievalEvalRun(input: CreateRetrievalEvalRunRequest) {
    return requestJson<EvalRun>("/api/evals/runs/retrieval", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  createE2EEvalRun(input: CreateE2EEvalRunRequest) {
    return requestJson<EvalRun>("/api/evals/runs/e2e", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
  },
  reconcileEvalRun(runId: string) {
    return requestJson<EvalRun>(`/api/evals/runs/${encodeURIComponent(runId)}/reconcile`, {
      method: "POST",
    });
  },
};
