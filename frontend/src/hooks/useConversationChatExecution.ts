import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from "react";
import type {
  ChatExecutionRequest,
  ChatExecutionResponse,
  ChatRunSubmissionResponse,
  ConversationDetail,
  KnowledgeScope,
  RetrievalFilters,
} from "../types";
import type { ChatSubmitOverrides, useChatExecution } from "./useChatExecution";
import { emptyRetrievalFilters, normalizeRetrievalFilters } from "@/utils/retrievalHints";

type UseConversationChatExecutionOptions = {
  chat: ReturnType<typeof useChatExecution>;
  conversationsEnabled?: boolean;
  conversationId?: string | null;
  conversationDetail?: ConversationDetail | null;
  setSelectedInstructionIds: Dispatch<SetStateAction<string[]>>;
  onConversationSelected?: (conversationId: string) => void;
  onSubmitted?: (submission: ChatRunSubmissionResponse) => void | Promise<void>;
  onCompleted?: (response: ChatExecutionResponse) => void | Promise<void>;
};

type DirtyField =
  | "model"
  | "answerMode"
  | "knowledgeScope"
  | "retrievalFilters"
  | "instructionIds";

export const useConversationChatExecution = ({
  chat,
  conversationsEnabled = true,
  conversationId = null,
  conversationDetail = null,
  setSelectedInstructionIds,
  onConversationSelected,
  onSubmitted,
  onCompleted,
}: UseConversationChatExecutionOptions) => {
  const [dirtyFields, setDirtyFields] = useState<Set<DirtyField>>(() => new Set());
  const [useLongTermMemory, setUseLongTermMemory] = useState(false);
  const seededConversationRef = useRef<string | null>(null);

  const markDirty = useCallback((field: DirtyField) => {
    if (!conversationsEnabled || !conversationId) {
      return;
    }
    setDirtyFields((current) => {
      if (current.has(field)) {
        return current;
      }
      const next = new Set(current);
      next.add(field);
      return next;
    });
  }, [conversationId, conversationsEnabled]);

  useEffect(() => {
    if (
      !conversationsEnabled
      || !conversationId
      || !conversationDetail
    ) {
      return;
    }
    const seedKey = conversationSeedKey(conversationDetail);
    if (seededConversationRef.current === seedKey) {
      return;
    }

    const stickyState = conversationDetail.stickyState;
    if (stickyState?.model || conversationDetail.defaultModel) {
      chat.setModel(stickyState?.model ?? conversationDetail.defaultModel ?? chat.model);
    }
    if (stickyState?.answerMode || conversationDetail.defaultAnswerMode) {
      chat.setAnswerMode(stickyState?.answerMode ?? conversationDetail.defaultAnswerMode ?? chat.answerMode);
    }
    if (stickyState?.knowledgeScope) {
      chat.setKnowledgeScope(stickyState.knowledgeScope);
    }
    if (stickyState?.retrievalFilters) {
      chat.setRetrievalFilters(stickyState.retrievalFilters);
    } else {
      chat.setRetrievalFilters(emptyRetrievalFilters());
    }
    setSelectedInstructionIds(stickyInstructionIds(stickyState));
    seededConversationRef.current = seedKey;
    setDirtyFields(new Set());
  }, [chat, conversationDetail, conversationId, conversationsEnabled, setSelectedInstructionIds]);

  useEffect(() => {
    if (!conversationsEnabled || !conversationId) {
      seededConversationRef.current = null;
      setDirtyFields(new Set());
    }
  }, [conversationId, conversationsEnabled]);

  const requestTransform = useCallback((request: ChatExecutionRequest): ChatExecutionRequest => {
    if (!conversationsEnabled) {
      return request;
    }
    const selectedConversation = Boolean(conversationId);
    const next: ChatExecutionRequest = {
      ...request,
      contextOptions: {
        ...(request.contextOptions ?? {}),
        resolveRetrievalQuery: true,
        useStickyState: true,
        useSummary: true,
        useLongTermMemory,
      },
    };
    const submitCreatesConversation = !selectedConversation && request.persistConversation === true;
    const shouldSendAllStickyFields = submitCreatesConversation;

    if (request.mode === "rag") {
      const manualFilters = normalizeRetrievalFilters(chat.retrievalFilters);
      const hasManualFilters = chat.manualOwnedFields.length > 0;
      if (shouldSendAllStickyFields) {
        if (hasManualFilters) {
          next.retrievalFilters = manualFilters;
        } else {
          delete next.retrievalFilters;
        }
      } else if (dirtyFields.has("retrievalFilters")) {
        next.retrievalFilters = manualFilters;
      } else {
        delete next.retrievalFilters;
      }
    }

    if (shouldSendAllStickyFields) {
      return next;
    }

    if (!dirtyFields.has("model")) {
      delete next.model;
    }
    if (!dirtyFields.has("answerMode")) {
      delete next.answerMode;
    }
    if (!dirtyFields.has("knowledgeScope")) {
      delete next.knowledgeScope;
      delete next.instructionWorkspaceKey;
    }
    if (!dirtyFields.has("instructionIds")) {
      delete next.instructionIds;
    }
    return next;
  }, [chat.manualOwnedFields.length, chat.retrievalFilters, conversationId, conversationsEnabled, dirtyFields, useLongTermMemory]);

  const submit = useCallback(async () => {
    if (!conversationsEnabled) {
      return chat.submit();
    }
    const overrides: ChatSubmitOverrides = {
      persistConversation: true,
      conversationId,
      clientTurnId: createClientTurnId(),
      requestTransform,
      onSubmitted: async (submission) => {
        if (submission.conversationId) {
          onConversationSelected?.(submission.conversationId);
        }
        await onSubmitted?.(submission);
      },
      onCompleted: async (response) => {
        setDirtyFields(new Set());
        await onCompleted?.(response);
      },
    };
    return chat.submit(overrides);
  }, [chat, conversationId, conversationsEnabled, onCompleted, onConversationSelected, onSubmitted, requestTransform]);

  const setModel = useCallback((value: string) => {
    markDirty("model");
    chat.setModel(value);
  }, [chat, markDirty]);

  const setAnswerMode = useCallback((value: Parameters<typeof chat.setAnswerMode>[0]) => {
    markDirty("answerMode");
    chat.setAnswerMode(value);
  }, [chat, markDirty]);

  const setKnowledgeScope = useCallback((value: SetStateAction<KnowledgeScope>) => {
    markDirty("knowledgeScope");
    chat.setKnowledgeScope(value);
  }, [chat, markDirty]);

  const setRetrievalFilters = useCallback((value: SetStateAction<RetrievalFilters>) => {
    markDirty("retrievalFilters");
    chat.setRetrievalFilters(value);
  }, [chat, markDirty]);

  const updateRetrievalFilter = useCallback((
    key: Parameters<typeof chat.updateRetrievalFilter>[0],
    value: Parameters<typeof chat.updateRetrievalFilter>[1],
  ) => {
    markDirty("retrievalFilters");
    chat.updateRetrievalFilter(key, value);
  }, [chat, markDirty]);

  const clearRetrievalFilter = useCallback((key: Parameters<typeof chat.clearRetrievalFilter>[0]) => {
    markDirty("retrievalFilters");
    chat.clearRetrievalFilter(key);
  }, [chat, markDirty]);

  const markInstructionIdsDirty = useCallback(() => {
    markDirty("instructionIds");
  }, [markDirty]);

  const stickyDirtyFields = useMemo(() => Array.from(dirtyFields), [dirtyFields]);

  return {
    ...chat,
    setModel,
    setAnswerMode,
    setKnowledgeScope,
    setRetrievalFilters,
    updateRetrievalFilter,
    clearRetrievalFilter,
    markInstructionIdsDirty,
    stickyDirtyFields,
    useLongTermMemory,
    setUseLongTermMemory,
    submit,
  };
};

const createClientTurnId = () => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const conversationSeedKey = (conversationDetail: ConversationDetail) => {
  const stickyState = conversationDetail.stickyState;
  return [
    conversationDetail.id,
    stickyState?.version ?? "no-version",
    stickyState?.updatedFromRunId ?? "no-run",
    stickyState?.updatedAt ?? conversationDetail.updatedAt,
  ].join(":");
};

const stickyInstructionIds = (stickyState: ConversationDetail["stickyState"]) => {
  if (!stickyState) {
    return [];
  }
  return Array.from(new Set([
    ...(stickyState.instructionIds ?? []),
    ...(stickyState.scenarioInstructionIds ?? []),
  ]));
};
