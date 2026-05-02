import { useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  AnswerMode,
  ChatExecutionRequest,
  ChatExecutionResponse,
  ChatMode,
  ChatRunStatusResponse,
  KnowledgeScope,
  QualityLayerFlags,
  RetrievalFilters,
} from "../types";
import { DEFAULT_KNOWLEDGE_SCOPE } from "../utils/workbenchPresentation";
import {
  emptyRetrievalFilters,
  extractRetrievalQueryHints,
  hasRetrievalFilterValue,
  HINT_OWNED_FILTER_KEYS,
  mergeHintFilters,
  normalizeRetrievalFilters,
  type RetrievalFilterKey,
} from "../utils/retrievalHints";

const CHAT_RUN_POLL_INTERVAL_MS = 1000;

type UseChatExecutionOptions = {
  mode: ChatMode;
  initialModel: string;
  initialPrompt: string;
  initialTemporaryInstruction?: string;
  initialAnswerMode?: AnswerMode;
  initialKnowledgeScope?: KnowledgeScope;
  rolloutFlags?: QualityLayerFlags | null;
  selectedInstructionIds: string[];
  workspaceKey?: string | null;
};

export const useChatExecution = ({
  mode,
  initialModel,
  initialPrompt,
  initialTemporaryInstruction = "",
  initialAnswerMode = "brief",
  initialKnowledgeScope = DEFAULT_KNOWLEDGE_SCOPE,
  rolloutFlags = null,
  selectedInstructionIds,
  workspaceKey = null,
}: UseChatExecutionOptions) => {
  const [model, setModel] = useState(initialModel);
  const [prompt, setPrompt] = useState(initialPrompt);
  const [temporaryInstruction, setTemporaryInstruction] = useState(initialTemporaryInstruction);
  const [answerMode, setAnswerMode] = useState<AnswerMode>(initialAnswerMode);
  const [knowledgeScope, setKnowledgeScope] = useState<KnowledgeScope>(initialKnowledgeScope);
  const [retrievalFilters, setRetrievalFilters] = useState<RetrievalFilters>(emptyRetrievalFilters());
  const [dismissedHintKeys, setDismissedHintKeys] = useState<RetrievalFilterKey[]>([]);
  const [response, setResponse] = useState<ChatExecutionResponse | null>(null);
  const [lastSubmittedRequest, setLastSubmittedRequest] = useState<ChatExecutionRequest | null>(null);
  const [currentRunId, setCurrentRunId] = useState<string | null>(null);
  const [currentRunStatus, setCurrentRunStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const currentRunIdRef = useRef<string | null>(null);
  const submitSequenceRef = useRef(0);

  useEffect(() => () => {
    controllerRef.current?.abort();
    if (currentRunIdRef.current) {
      void apiClient.cancelChatRun(currentRunIdRef.current);
    }
  }, []);

  const metadataFiltersEnabled = rolloutFlags?.metadataFiltersV1 === true;
  const queryHintsEnabled = metadataFiltersEnabled && rolloutFlags?.queryHintsV1 === true;
  useEffect(() => {
    if (mode !== "rag") {
      return;
    }
    const normalizedWorkspaceKey = workspaceKey?.trim() || null;
    setKnowledgeScope((current) => (
      current.workspaceKey === normalizedWorkspaceKey
        ? current
        : { ...current, workspaceKey: normalizedWorkspaceKey }
    ));
  }, [mode, workspaceKey]);
  const queryHints = useMemo(
    () => (queryHintsEnabled ? extractRetrievalQueryHints(prompt) : {}),
    [prompt, queryHintsEnabled],
  );
  const dismissedHintSet = useMemo(
    () => new Set<RetrievalFilterKey>(dismissedHintKeys),
    [dismissedHintKeys],
  );
  const normalizedRetrievalFilters = useMemo(
    () => normalizeRetrievalFilters(retrievalFilters),
    [retrievalFilters],
  );
  const effectiveRetrievalFilters = useMemo(
    () => (queryHintsEnabled
      ? mergeHintFilters(normalizedRetrievalFilters, queryHints, dismissedHintSet)
      : normalizedRetrievalFilters),
    [dismissedHintSet, normalizedRetrievalFilters, queryHints, queryHintsEnabled],
  );
  const manualOwnedFields = useMemo(
    () =>
      (Object.keys(normalizedRetrievalFilters) as RetrievalFilterKey[]).filter((key) =>
        hasRetrievalFilterValue(normalizedRetrievalFilters[key]),
      ),
    [normalizedRetrievalFilters],
  );
  const hintOwnedFields = useMemo(
    () =>
      HINT_OWNED_FILTER_KEYS.filter(
        (key) =>
          queryHintsEnabled
          && metadataFiltersEnabled
          && !manualOwnedFields.includes(key)
          && !dismissedHintSet.has(key)
          && hasRetrievalFilterValue(effectiveRetrievalFilters[key]),
      ),
    [dismissedHintSet, effectiveRetrievalFilters, manualOwnedFields, metadataFiltersEnabled, queryHintsEnabled],
  );

  const updateRetrievalFilter = (key: RetrievalFilterKey, value: RetrievalFilters[RetrievalFilterKey]) => {
    setRetrievalFilters((current) => ({
      ...current,
      [key]: value,
    }));
    setDismissedHintKeys((current) => current.filter((item) => item !== key));
  };

  const clearRetrievalFilter = (key: RetrievalFilterKey) => {
    setRetrievalFilters((current) => ({
      ...current,
      [key]: key === "tags" ? [] : null,
    }));
  };

  const dismissHint = (key: RetrievalFilterKey) => {
    const keysToDismiss: RetrievalFilterKey[] =
      key === "documentDateFrom" || key === "documentDateTo"
        ? ["documentDateFrom", "documentDateTo"]
        : [key];
    setDismissedHintKeys((current) => {
      const next = new Set<RetrievalFilterKey>(current);
      keysToDismiss.forEach((item) => next.add(item));
      return Array.from(next);
    });
    setRetrievalFilters((current) => ({
      ...current,
      ...Object.fromEntries(keysToDismiss.map((item) => [item, item === "tags" ? [] : null])),
    }));
  };

  const resetDismissedHints = () => {
    setDismissedHintKeys([]);
  };

  const submit = async () => {
    if (currentRunIdRef.current) {
      void apiClient.cancelChatRun(currentRunIdRef.current);
      currentRunIdRef.current = null;
    }
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const submitSequence = submitSequenceRef.current + 1;
    submitSequenceRef.current = submitSequence;
    setIsSubmitting(true);
    setError(null);

    const effectiveKnowledgeScope = mode === "rag"
      ? { ...knowledgeScope, workspaceKey: workspaceKey?.trim() || knowledgeScope.workspaceKey || null }
      : knowledgeScope;
    const hasKnowledgeScope =
      (effectiveKnowledgeScope.presetIds ?? []).length > 0 ||
      (effectiveKnowledgeScope.facetIds ?? []).length > 0 ||
      (effectiveKnowledgeScope.documentClasses ?? []).length > 0 ||
      (effectiveKnowledgeScope.documentTypes ?? []).length > 0 ||
      (effectiveKnowledgeScope.documentStatuses ?? []).length > 0 ||
      (effectiveKnowledgeScope.projectKeys ?? []).length > 0 ||
      Boolean(effectiveKnowledgeScope.documentNumber?.trim()) ||
      (effectiveKnowledgeScope.languageCodes ?? []).length > 0 ||
      (effectiveKnowledgeScope.tags ?? []).length > 0 ||
      Boolean(effectiveKnowledgeScope.workspaceKey?.trim()) ||
      Boolean(effectiveKnowledgeScope.periodStartFrom) ||
      Boolean(effectiveKnowledgeScope.periodStartTo) ||
      Boolean(effectiveKnowledgeScope.periodEndFrom) ||
      Boolean(effectiveKnowledgeScope.periodEndTo) ||
      effectiveKnowledgeScope.uploadedTodayOnly;

    const request: ChatExecutionRequest = {
      mode,
      model,
      prompt,
      instructionIds: selectedInstructionIds,
      answerMode,
      ...(mode === "rag" || hasKnowledgeScope ? { knowledgeScope: effectiveKnowledgeScope } : {}),
      ...(effectiveKnowledgeScope.workspaceKey?.trim() ? { instructionWorkspaceKey: effectiveKnowledgeScope.workspaceKey.trim() } : {}),
      ...(mode === "rag" && metadataFiltersEnabled && (hintOwnedFields.length > 0 || manualOwnedFields.length > 0)
        ? { retrievalFilters: effectiveRetrievalFilters }
        : {}),
      ...(mode === "rag" && queryHintsEnabled && dismissedHintKeys.length > 0
        ? { dismissedRetrievalHintKeys: dismissedHintKeys }
        : {}),
      ...(temporaryInstruction.trim() ? { temporaryInstruction: temporaryInstruction.trim() } : {}),
    };

    try {
      setResponse(null);
      setLastSubmittedRequest(request);
      setCurrentRunId(null);
      setCurrentRunStatus("RECEIVED");
      const submittedRun = await apiClient.submitChatRun(request, controller.signal);
      currentRunIdRef.current = submittedRun.id;
      setCurrentRunId(submittedRun.id);
      setCurrentRunStatus(submittedRun.status);
      const payload = await waitForRunResult(submittedRun.id, controller.signal, (status) => {
        setCurrentRunStatus(status.status);
      });
      if (
        controller.signal.aborted ||
        controllerRef.current !== controller ||
        submitSequenceRef.current !== submitSequence
      ) {
        return null;
      }

      setResponse(payload);
      setCurrentRunStatus("COMPLETED");
      return payload;
    } catch (submissionError) {
      if (
        controller.signal.aborted ||
        controllerRef.current !== controller ||
        submitSequenceRef.current !== submitSequence
      ) {
        return null;
      }

      setError(translateCommonApiError(submissionError, "Не удалось выполнить запрос к модели"));
      return null;
    } finally {
      if (controllerRef.current === controller && submitSequenceRef.current === submitSequence) {
        controllerRef.current = null;
        currentRunIdRef.current = null;
        setIsSubmitting(false);
      }
    }
  };

  return {
    mode,
    model,
    setModel,
    prompt,
    setPrompt,
    temporaryInstruction,
    setTemporaryInstruction,
    systemPrompt: temporaryInstruction,
    setSystemPrompt: setTemporaryInstruction,
    answerMode,
    setAnswerMode,
    knowledgeScope,
    setKnowledgeScope,
    retrievalFilters: normalizedRetrievalFilters,
    setRetrievalFilters,
    queryHints,
    effectiveRetrievalFilters,
    metadataFiltersEnabled,
    queryHintsEnabled,
    hintOwnedFields,
    manualOwnedFields,
    dismissedHintKeys,
    currentRunId,
    currentRunStatus,
    updateRetrievalFilter,
    clearRetrievalFilter,
    dismissHint,
    resetDismissedHints,
    response,
    lastSubmittedRequest,
    error,
    isSubmitting,
    submit,
  };
};

const waitForRunResult = async (
  runId: string,
  signal: AbortSignal,
  onStatus: (status: ChatRunStatusResponse) => void,
): Promise<ChatExecutionResponse> => {
  while (!signal.aborted) {
    const status = await apiClient.fetchChatRunStatus(runId, signal);
    onStatus(status);
    if (status.status === "COMPLETED") {
      return apiClient.fetchChatRunResult(runId, signal);
    }
    if (status.status === "FAILED") {
      throw new Error(status.failureMessage ?? "Запуск RAG запроса завершился ошибкой.");
    }
    if (status.status === "CANCELLED") {
      throw new Error(status.failureMessage ?? "Запуск RAG запроса был отменён.");
    }
    await sleep(CHAT_RUN_POLL_INTERVAL_MS, signal);
  }
  throw new DOMException("Chat run polling aborted", "AbortError");
};

const sleep = (milliseconds: number, signal: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    const timeoutId = window.setTimeout(resolve, milliseconds);
    signal.addEventListener(
      "abort",
      () => {
        window.clearTimeout(timeoutId);
        reject(new DOMException("Chat run polling aborted", "AbortError"));
      },
      { once: true },
    );
  });
