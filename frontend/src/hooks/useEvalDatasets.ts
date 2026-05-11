import { useEffect, useMemo, useRef, useState } from "react";
import { evalClient } from "@/api/evalClient";
import { translateCommonApiError } from "@/api/errorMessages";
import type {
  CreateEvalCaseRequest,
  CreateEvalCaseReviewRequest,
  CreateEvalDatasetVersionRequest,
  EvalCase,
  EvalDatasetDetail,
  EvalDatasetSummary,
  EvalDatasetVersion,
  PromoteEvalCaseRequest,
  SubmitEvalCaseReviewRequest,
  UpdateEvalCaseRequest,
} from "@/types";

type UseEvalDatasetsOptions = {
  enabled?: boolean;
  focusDatasetId?: string | null;
};

export const useEvalDatasets = ({
  enabled = true,
  focusDatasetId = null,
}: UseEvalDatasetsOptions = {}) => {
  const [datasets, setDatasets] = useState<EvalDatasetSummary[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(focusDatasetId);
  const [detail, setDetail] = useState<EvalDatasetDetail | null>(null);
  const [versions, setVersions] = useState<EvalDatasetVersion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const loadedRef = useRef(false);
  const error = mutationError ?? loadError;

  const selectedDataset = useMemo(
    () => datasets.find((dataset) => dataset.id === selectedDatasetId) ?? null,
    [datasets, selectedDatasetId],
  );

  const cases = detail?.cases ?? [];
  const reviews = detail?.reviews ?? [];

  const loadDatasets = async (signal?: AbortSignal) => {
    if (!enabled) {
      return null;
    }
    setIsLoading(true);
    try {
      const payload = await evalClient.fetchEvalDatasets(signal);
      setDatasets(payload);
      setLoadError(null);
      loadedRef.current = true;
      setSelectedDatasetId((current) => {
        if (focusDatasetId && payload.some((dataset) => dataset.id === focusDatasetId)) {
          return focusDatasetId;
        }
        if (current && payload.some((dataset) => dataset.id === current)) {
          return current;
        }
        return payload[0]?.id ?? null;
      });
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setLoadError(translateCommonApiError(loadError, "Не удалось загрузить наборы оценки"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  };

  const loadDataset = async (datasetId: string, signal?: AbortSignal) => {
    if (!enabled) {
      return null;
    }
    setIsLoadingDetail(true);
    try {
      const [detailPayload, versionsPayload] = await Promise.all([
        evalClient.fetchEvalDataset(datasetId, signal),
        evalClient.fetchEvalDatasetVersions(datasetId, signal),
      ]);
      setDetail(detailPayload);
      setVersions(versionsPayload);
      setLoadError(null);
      return detailPayload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setLoadError(translateCommonApiError(loadError, "Не удалось загрузить детали набора"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoadingDetail(false);
      }
    }
  };

  useEffect(() => {
    if (!enabled || loadedRef.current) {
      return;
    }
    const controller = new AbortController();
    void loadDatasets(controller.signal);
    return () => controller.abort();
  }, [enabled]);

  useEffect(() => {
    if (!enabled || !focusDatasetId) {
      return;
    }
    setSelectedDatasetId(focusDatasetId);
  }, [enabled, focusDatasetId]);

  useEffect(() => {
    if (!enabled || !selectedDatasetId) {
      setDetail(null);
      setVersions([]);
      return;
    }
    const controller = new AbortController();
    void loadDataset(selectedDatasetId, controller.signal);
    return () => controller.abort();
  }, [enabled, selectedDatasetId]);

  const refreshSelected = async () => {
    await loadDatasets();
    if (selectedDatasetId) {
      await loadDataset(selectedDatasetId);
    }
  };

  const mutate = async <T,>(operation: () => Promise<T>, fallbackMessage: string) => {
    setIsMutating(true);
    setMutationError(null);
    try {
      const result = await operation();
      await refreshSelected();
      return result;
    } catch (mutationError) {
      setMutationError(translateCommonApiError(mutationError, fallbackMessage));
      return null;
    } finally {
      setIsMutating(false);
    }
  };

  const createCase = (datasetId: string, input: CreateEvalCaseRequest) =>
    mutate(() => evalClient.createEvalCase(datasetId, input), "Backend отклонил создание кейса оценки");

  const updateCase = (caseId: string, input: UpdateEvalCaseRequest) =>
    mutate(() => evalClient.updateEvalCase(caseId, input), "Backend отклонил обновление кейса оценки");

  const archiveCase = (caseId: string) =>
    mutate(() => evalClient.archiveEvalCase(caseId), "Не удалось архивировать кейс оценки");

  const submitReview = (caseId: string, input: SubmitEvalCaseReviewRequest = {}) =>
    mutate(() => evalClient.submitEvalCaseReview(caseId, input), "Backend validation не пропустил кейс в review");

  const reviewCase = (caseId: string, input: CreateEvalCaseReviewRequest) =>
    mutate(() => evalClient.reviewEvalCase(caseId, input), "Backend отклонил действие review");

  const promoteCase = (caseId: string, input: PromoteEvalCaseRequest) =>
    mutate(() => evalClient.promoteEvalCase(caseId, input), "Backend отклонил продвижение");

  const createDatasetVersion = (datasetId: string, input: CreateEvalDatasetVersionRequest = {}) =>
    mutate(() => evalClient.createEvalDatasetVersion(datasetId, input), "Не удалось создать версию набора");

  const casesById = useMemo(() => {
    const map = new Map<string, EvalCase>();
    cases.forEach((evalCase) => {
      if (evalCase.id) {
        map.set(evalCase.id, evalCase);
      }
    });
    return map;
  }, [cases]);

  return {
    cases,
    casesById,
    datasets,
    detail,
    error,
    isLoading,
    isLoadingDetail,
    isMutating,
    loadError,
    mutationError,
    reviews,
    selectedDataset,
    selectedDatasetId,
    versions,
    archiveCase,
    createCase,
    createDatasetVersion,
    loadDataset,
    loadDatasets,
    promoteCase,
    reviewCase,
    setSelectedDatasetId,
    submitReview,
    updateCase,
  };
};
