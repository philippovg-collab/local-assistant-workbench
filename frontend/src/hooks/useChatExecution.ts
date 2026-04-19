import { useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  AnswerMode,
  ChatExecutionRequest,
  ChatExecutionResponse,
  ChatMode,
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

type UseChatExecutionOptions = {
  mode: ChatMode;
  initialModel: string;
  initialPrompt: string;
  initialTemporaryInstruction?: string;
  initialAnswerMode?: AnswerMode;
  initialKnowledgeScope?: KnowledgeScope;
  rolloutFlags?: QualityLayerFlags | null;
  selectedInstructionIds: string[];
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
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const submitSequenceRef = useRef(0);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const metadataFiltersEnabled = rolloutFlags?.metadataFiltersV1 === true;
  const queryHintsEnabled = metadataFiltersEnabled && rolloutFlags?.queryHintsV1 === true;
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
    setDismissedHintKeys((current) => (current.includes(key) ? current : [...current, key]));
    setRetrievalFilters((current) => ({
      ...current,
      [key]: key === "tags" ? [] : null,
    }));
  };

  const resetDismissedHints = () => {
    setDismissedHintKeys([]);
  };

  const submit = async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const submitSequence = submitSequenceRef.current + 1;
    submitSequenceRef.current = submitSequence;
    setIsSubmitting(true);
    setError(null);

    const hasKnowledgeScope =
      knowledgeScope.presetIds.length > 0 ||
      knowledgeScope.documentClasses.length > 0 ||
      knowledgeScope.tags.length > 0 ||
      Boolean(knowledgeScope.workspaceKey?.trim()) ||
      knowledgeScope.uploadedTodayOnly;

    const request: ChatExecutionRequest = {
      mode,
      model,
      prompt,
      instructionIds: selectedInstructionIds,
      answerMode,
      ...(mode === "rag" || hasKnowledgeScope ? { knowledgeScope } : {}),
      ...(knowledgeScope.workspaceKey?.trim() ? { instructionWorkspaceKey: knowledgeScope.workspaceKey.trim() } : {}),
      ...(mode === "rag" && metadataFiltersEnabled && (hintOwnedFields.length > 0 || manualOwnedFields.length > 0)
        ? { retrievalFilters: effectiveRetrievalFilters }
        : {}),
      ...(temporaryInstruction.trim() ? { temporaryInstruction: temporaryInstruction.trim() } : {}),
    };

    try {
      setResponse(null);
      setLastSubmittedRequest(request);
      const payload = await apiClient.executeChat(request, controller.signal);
      if (
        controller.signal.aborted ||
        controllerRef.current !== controller ||
        submitSequenceRef.current !== submitSequence
      ) {
        return null;
      }

      setResponse(payload);
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
