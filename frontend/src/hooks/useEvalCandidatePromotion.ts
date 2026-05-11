import { useState } from "react";
import { evalClient } from "@/api/evalClient";
import { translateCommonApiError } from "@/api/errorMessages";
import type { ChatAuditRunDetail, CreateEvalCandidateFromChatRunRequest, EvalCase } from "@/types";

type CreateCandidateOptions = {
  datasetId?: string | null;
  failureReason?: string | null;
  tags?: string[];
};

const compactTags = (values: Array<string | null | undefined>) =>
  Array.from(new Set(values.map((value) => value?.trim()).filter(Boolean) as string[]));

const buildCandidateRequest = (
  run: ChatAuditRunDetail,
  options: CreateCandidateOptions = {},
): CreateEvalCandidateFromChatRunRequest => ({
  artifacts: {
    answer: run.answer,
    answerMode: run.answerMode,
    contextStatus: run.contextStatus,
    knowledgeScopeResolved: run.knowledgeScopeResolved,
    model: run.model,
    mode: run.mode,
    retrievalTrace: run.retrievalTrace,
  },
  chatRunId: run.id,
  datasetId: options.datasetId?.trim() || null,
  failureReason: options.failureReason ?? run.failureMessage ?? run.failureCode ?? run.contextStatus ?? "audit_review",
  originalSources: run.sources as unknown as Record<string, unknown>[],
  question: run.prompt,
  tags: compactTags(["audit", run.mode, ...(options.tags ?? [])]),
});

export const useEvalCandidatePromotion = () => {
  const [candidate, setCandidate] = useState<EvalCase | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const error = mutationError;

  const createCandidate = async (run: ChatAuditRunDetail, options: CreateCandidateOptions = {}) => {
    setIsCreating(true);
    setMutationError(null);
    try {
      const payload = await evalClient.createEvalCandidateFromChatRun(buildCandidateRequest(run, options));
      setCandidate(payload);
      return payload;
    } catch (createError) {
      setMutationError(translateCommonApiError(createError, "Не удалось создать CANDIDATE из аудиторского запуска"));
      return null;
    } finally {
      setIsCreating(false);
    }
  };

  return {
    candidate,
    error,
    isCreating,
    mutationError,
    createCandidate,
  };
};
