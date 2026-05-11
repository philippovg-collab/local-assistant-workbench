import { useEffect, useMemo, useState } from "react";
import { evalClient } from "@/api/evalClient";
import { translateCommonApiError } from "@/api/errorMessages";
import type { EvalRun, EvalRunCompare } from "@/types";

type UseEvalCompareOptions = {
  enabled?: boolean;
  runs: EvalRun[];
};

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};

export const useEvalCompare = ({ enabled = true, runs }: UseEvalCompareOptions) => {
  const [baselineRunId, setBaselineRunId] = useState<string | null>(null);
  const [candidateRunId, setCandidateRunId] = useState<string | null>(null);
  const [compare, setCompare] = useState<EvalRunCompare | null>(null);
  const [isComparing, setIsComparing] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const baselineRun = useMemo(
    () => runs.find((run) => run.id === baselineRunId) ?? null,
    [baselineRunId, runs],
  );
  const candidateRun = useMemo(
    () => runs.find((run) => run.id === candidateRunId) ?? null,
    [candidateRunId, runs],
  );

  useEffect(() => {
    if (!enabled || runs.length === 0) {
      return;
    }
    setBaselineRunId((current) => current && runs.some((run) => run.id === current) ? current : runs[0]?.id ?? null);
    setCandidateRunId((current) => {
      if (current && runs.some((run) => run.id === current)) {
        return current;
      }
      return runs.find((run) => run.id !== (baselineRunId ?? runs[0]?.id))?.id ?? null;
    });
  }, [baselineRunId, enabled, runs]);

  const createCompare = async () => {
    if (!enabled || !baselineRunId || !candidateRunId) {
      return null;
    }
    setIsComparing(true);
    setMutationError(null);
    try {
      const payload = await evalClient.createEvalCompare({
        baselineRunId,
        candidateRunId,
      });
      setCompare(payload);
      return payload;
    } catch (compareError) {
      setMutationError(translateCommonApiError(compareError, "Не удалось создать eval compare"));
      return null;
    } finally {
      setIsComparing(false);
    }
  };

  const compatibilityReasons = compare?.compatibilityReasons ?? [];
  const isIncompatible =
    compare?.compatibilityStatus === "BLOCKED" || compare?.status === "INCOMPATIBLE";
  const summary = asRecord(compare?.summary);
  const metricSummary = asRecord(summary.metricSummary);
  const overallVerdict = typeof summary.overallVerdict === "string" ? summary.overallVerdict : null;
  const metricSummaryError = compare && !isIncompatible && Object.keys(metricSummary).length === 0
    ? "Compatible compare is missing metricSummary; release proof is incomplete."
    : null;
  const error = mutationError ?? metricSummaryError;

  return {
    baselineRun,
    baselineRunId,
    candidateRun,
    candidateRunId,
    compare,
    compatibilityReasons,
    error,
    isComparing,
    isIncompatible,
    metricSummary,
    metricSummaryError,
    mutationError,
    overallVerdict,
    summary,
    createCompare,
    setBaselineRunId,
    setCandidateRunId,
  };
};
