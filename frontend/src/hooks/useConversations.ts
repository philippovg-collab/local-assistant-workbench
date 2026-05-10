import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  AnswerMode,
  ChatMode,
  ConversationCreateRequest,
  ConversationDetail,
  ConversationSummary,
} from "../types";

type UseConversationsOptions = {
  enabled?: boolean;
  mode: ChatMode;
  workspaceKey?: string | null;
};

export const useConversations = ({
  enabled = true,
  mode,
  workspaceKey = null,
}: UseConversationsOptions) => {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedConversationDetail, setSelectedConversationDetail] = useState<ConversationDetail | null>(null);
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const loadedParamsRef = useRef<string | null>(null);
  const loadedDetailRef = useRef<string | null>(null);
  const paramsKey = `${mode}:${workspaceKey?.trim() ?? ""}`;

  const activeConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedConversationId) ?? null,
    [conversations, selectedConversationId],
  );

  const loadConversationDetail = useCallback(async (
    conversationId: string | null,
    signal?: AbortSignal,
    options: { force?: boolean } = {},
  ) => {
    if (!conversationId) {
      setSelectedConversationDetail(null);
      loadedDetailRef.current = null;
      return null;
    }
    if (!enabled && !options.force) {
      return null;
    }

    setIsLoadingDetail(true);
    try {
      const payload = await apiClient.fetchConversation(conversationId, signal);
      setSelectedConversationDetail(payload);
      loadedDetailRef.current = conversationId;
      setError(null);
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setError(translateCommonApiError(loadError, "Не удалось загрузить состояние беседы"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoadingDetail(false);
      }
    }
  }, [enabled]);

  const loadConversations = useCallback(async (signal?: AbortSignal, options: { force?: boolean } = {}) => {
    if (!enabled && !options.force) {
      return null;
    }

    setIsLoading(true);
    try {
      const payload = await apiClient.fetchConversations({ workspaceKey, mode }, signal);
      setConversations(payload);
      setSelectedConversationId((current) => {
        if (current && payload.some((conversation) => conversation.id === current)) {
          return current;
        }
        return payload.find((conversation) => conversation.status === "ACTIVE")?.id ?? payload[0]?.id ?? null;
      });
      setSelectedConversationDetail((current) =>
        current && payload.some((conversation) => conversation.id === current.id) ? current : null,
      );
      setError(null);
      loadedParamsRef.current = paramsKey;
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setError(translateCommonApiError(loadError, "Не удалось загрузить беседы"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, [enabled, mode, paramsKey, workspaceKey]);

  useEffect(() => {
    if (!enabled || loadedParamsRef.current === paramsKey) {
      return;
    }

    const controller = new AbortController();
    void loadConversations(controller.signal);
    return () => controller.abort();
  }, [enabled, loadConversations, paramsKey]);

  useEffect(() => {
    if (enabled) {
      return;
    }
    setConversations([]);
    setSelectedConversationId(null);
    setSelectedConversationDetail(null);
    setError(null);
    setIsLoading(false);
    setIsLoadingDetail(false);
    setIsMutating(false);
    loadedParamsRef.current = null;
    loadedDetailRef.current = null;
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    if (!selectedConversationId) {
      setSelectedConversationDetail(null);
      loadedDetailRef.current = null;
      return;
    }
    if (loadedDetailRef.current === selectedConversationId) {
      return;
    }

    const controller = new AbortController();
    void loadConversationDetail(selectedConversationId, controller.signal);
    return () => controller.abort();
  }, [enabled, loadConversationDetail, selectedConversationId]);

  const createConversation = useCallback(async (input: {
    title?: string | null;
    defaultModel?: string | null;
    defaultAnswerMode?: AnswerMode | null;
  } = {}) => {
    if (!enabled) {
      return null;
    }
    setIsMutating(true);
    try {
      const payload = await apiClient.createConversation({
        workspaceKey,
        mode,
        ...input,
      } satisfies ConversationCreateRequest);
      setSelectedConversationId(payload.id);
      setSelectedConversationDetail(payload);
      loadedDetailRef.current = payload.id;
      await loadConversations(undefined, { force: true });
      setError(null);
      return payload;
    } catch (mutationError) {
      setError(translateCommonApiError(mutationError, "Не удалось создать беседу"));
      return null;
    } finally {
      setIsMutating(false);
    }
  }, [enabled, loadConversations, mode, workspaceKey]);

  const archiveConversation = useCallback(async (conversationId: string) => {
    if (!enabled) {
      return null;
    }
    setIsMutating(true);
    try {
      const payload = await apiClient.patchConversation(conversationId, { status: "ARCHIVED" });
      await loadConversations(undefined, { force: true });
      setSelectedConversationId((current) => current === conversationId ? null : current);
      setSelectedConversationDetail((current) => current?.id === conversationId ? null : current);
      setError(null);
      return payload;
    } catch (mutationError) {
      setError(translateCommonApiError(mutationError, "Не удалось архивировать беседу"));
      return null;
    } finally {
      setIsMutating(false);
    }
  }, [enabled, loadConversations]);

  return {
    conversations,
    activeConversation,
    selectedConversationDetail,
    selectedConversationId,
    setSelectedConversationId,
    error,
    isLoading,
    isLoadingDetail,
    isMutating,
    loadConversations: (signal?: AbortSignal) => loadConversations(signal, { force: enabled }),
    loadConversationDetail: (conversationId?: string | null, signal?: AbortSignal) =>
      loadConversationDetail(conversationId ?? selectedConversationId, signal, { force: enabled }),
    createConversation,
    archiveConversation,
  };
};
