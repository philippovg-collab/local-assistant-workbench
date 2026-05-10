import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  AnswerMode,
  ChatExecutionRequest,
  ChatExecutionResponse,
  ChatMode,
  ChatRunSubmissionResponse,
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

export type ChatSubmitOverrides = {
  conversationId?: string | null;
  parentRunId?: string | null;
  clientTurnId?: string | null;
  persistConversation?: boolean;
  requestTransform?: (request: ChatExecutionRequest) => ChatExecutionRequest;
  onSubmitted?: (submission: ChatRunSubmissionResponse) => void | Promise<void>;
  onCompleted?: (response: ChatExecutionResponse) => void | Promise<void>;
  onSettled?: () => void | Promise<void>;
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
  const [isCancelling, setIsCancelling] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const currentRunIdRef = useRef<string | null>(null);
  const submitSequenceRef = useRef(0);

  useEffect(() => () => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    currentRunIdRef.current = null;
  }, []);

  const abortPolling = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    currentRunIdRef.current = null;
    setCurrentRunId(null);
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

  const cancelCurrentRun = useCallback(async () => {
    const runId = currentRunIdRef.current;
    if (!runId) {
      abortPolling();
      setIsSubmitting(false);
      return null;
    }

    const controller = controllerRef.current;
    controllerRef.current = null;
    controller?.abort();
    setIsCancelling(true);
    setError(null);

    try {
      const cancelledTrace = await apiClient.cancelChatRun(runId);
      if (currentRunIdRef.current === runId) {
        currentRunIdRef.current = null;
        setCurrentRunId(null);
        setCurrentRunStatus("CANCELLED");
        setIsSubmitting(false);
      }
      return cancelledTrace;
    } catch (cancelError) {
      if (currentRunIdRef.current === runId) {
        setError(translateCommonApiError(cancelError, "Не удалось отменить текущий запуск"));
      }
      return null;
    } finally {
      if (currentRunIdRef.current === runId) {
        currentRunIdRef.current = null;
        setCurrentRunId(null);
        setIsSubmitting(false);
      }
      setIsCancelling(false);
    }
  }, [abortPolling]);

  const submit = async (overrides: ChatSubmitOverrides = {}) => {
    abortPolling();
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

    const baseRequest: ChatExecutionRequest = {
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
      ...(overrides.persistConversation ? { persistConversation: true } : {}),
      ...(overrides.conversationId?.trim() ? { conversationId: overrides.conversationId.trim() } : {}),
      ...(overrides.parentRunId?.trim() ? { parentRunId: overrides.parentRunId.trim() } : {}),
      ...(overrides.clientTurnId?.trim() ? { clientTurnId: overrides.clientTurnId.trim() } : {}),
    };
    const request = overrides.requestTransform ? overrides.requestTransform(baseRequest) : baseRequest;

    try {
      setResponse(null);
      setLastSubmittedRequest(request);
      setCurrentRunId(null);
      setCurrentRunStatus("RECEIVED");
      const submittedRun = await apiClient.submitChatRun(request, controller.signal);
      await overrides.onSubmitted?.(submittedRun);
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
      await overrides.onCompleted?.(payload);
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
      await overrides.onSettled?.();
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
    isCancelling,
    submit,
    cancelCurrentRun,
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
