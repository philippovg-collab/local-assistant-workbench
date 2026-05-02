import { useEffect, useRef, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  CreateInstructionRequest,
  InstructionDetail,
  InstructionRevisionDiff,
  InstructionRevisionDetail,
  InstructionSummary,
} from "../types";

const translateInstructionError = (error: unknown, fallback: string) => {
  if (isApiClientError(error)) {
    switch (error.code) {
      case "instruction.invalid_title":
        return "Укажи название инструкции перед сохранением.";
      case "instruction.invalid_category":
        return "Выбери корректный тип инструкции.";
      case "instruction.invalid_content":
        return "Добавь текст инструкции перед сохранением.";
      case "instruction.not_found":
        return "Инструкция не найдена или уже была удалена.";
      default:
        if (error.code?.startsWith("instructions.storage_") || error.code?.startsWith("instruction.storage_")) {
          return "Не удалось выполнить операцию с локальной библиотекой инструкций backend. Повтори попытку ещё раз.";
        }
        return translateCommonApiError(error, fallback);
    }
  }

  return translateCommonApiError(error, fallback);
};

type UseInstructionsOptions = {
  enabled?: boolean;
};

export const useInstructions = (options: UseInstructionsOptions = {}) => {
  const { enabled = true } = options;
  const [instructions, setInstructions] = useState<InstructionSummary[]>([]);
  const [selectedInstruction, setSelectedInstruction] = useState<InstructionDetail | null>(null);
  const [revisions, setRevisions] = useState<InstructionRevisionDetail[]>([]);
  const [revisionDiff, setRevisionDiff] = useState<InstructionRevisionDiff | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(enabled);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingInstructionId, setDeletingInstructionId] = useState<string | null>(null);
  const hasLoadedRef = useRef(false);

  const loadInstructions = async (signal?: AbortSignal) => {
    if (!enabled) {
      setIsLoading(false);
      return null;
    }

    setIsLoading(true);
    try {
      const payload = await apiClient.fetchInstructions(signal);
      setInstructions(payload);
      setSelectedInstruction((current) =>
        current && payload.some((instruction) => instruction.id === current.id) ? current : null,
      );
      setRevisions((current) =>
        selectedInstruction && payload.some((instruction) => instruction.id === selectedInstruction.id)
          ? current
          : [],
      );
      setRevisionDiff((current) =>
        selectedInstruction && payload.some((instruction) => instruction.id === selectedInstruction.id)
          ? current
          : null,
      );
      setError(null);
      hasLoadedRef.current = true;
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateInstructionError(loadError, "Не удалось загрузить инструкции");
      setError(nextError);
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }
    if (hasLoadedRef.current) {
      return;
    }

    const controller = new AbortController();
    void loadInstructions(controller.signal);
    return () => controller.abort();
  }, [enabled]);

  const createInstruction = async (input: CreateInstructionRequest) => {
    setActionError(null);
    setMessage(null);

    try {
      const created = await apiClient.createInstruction(input);
      await loadInstructions();
      setSelectedInstruction(created);
      setRevisionDiff(null);
      setDetailError(null);
      setMessage("Инструкция сохранена и теперь реально участвует в execution flow через явный выбор.");
    } catch (submissionError) {
      setActionError(translateInstructionError(submissionError, "Не удалось сохранить инструкцию"));
      throw submissionError;
    }
  };

  const updateInstruction = async (instructionId: string, input: CreateInstructionRequest) => {
    setActionError(null);
    setMessage(null);

    try {
      const updated = await apiClient.updateInstruction(instructionId, input);
      await loadInstructions();
      setSelectedInstruction(updated);
      setRevisionDiff(null);
      setDetailError(null);
      setMessage("Инструкция обновлена без смены id и продолжает участвовать в execution flow через явный выбор.");
      return updated;
    } catch (submissionError) {
      setActionError(translateInstructionError(submissionError, "Не удалось обновить инструкцию"));
      throw submissionError;
    }
  };

  const deleteInstruction = async (instructionId: string) => {
    setDeletingInstructionId(instructionId);
    setActionError(null);
    setMessage(null);

    try {
      await apiClient.deleteInstruction(instructionId);
      await loadInstructions();
      setSelectedInstruction((current) => (current?.id === instructionId ? null : current));
      setRevisionDiff((current) => (selectedInstruction?.id === instructionId ? null : current));
      setDetailError(null);
      setMessage("Инструкция удалена из локальной библиотеки.");
    } catch (deleteError) {
      setActionError(translateInstructionError(deleteError, "Не удалось удалить инструкцию"));
      throw deleteError;
    } finally {
      setDeletingInstructionId(null);
    }
  };

  const loadInstruction = async (instructionId: string, signal?: AbortSignal) => {
    setIsLoadingDetail(true);
    setDetailError(null);

    try {
      const payload = await apiClient.fetchInstruction(instructionId, signal);
      setSelectedInstruction(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateInstructionError(loadError, "Не удалось загрузить текст инструкции");
      setDetailError(nextError);
      return null;
    } finally {
      setIsLoadingDetail(false);
    }
  };

  const loadInstructionRevisions = async (instructionId: string, signal?: AbortSignal) => {
    setDetailError(null);

    try {
      const payload = await apiClient.fetchInstructionRevisions(instructionId, signal);
      setRevisions(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateInstructionError(loadError, "Не удалось загрузить историю инструкции");
      setDetailError(nextError);
      return null;
    }
  };

  const restoreInstructionRevision = async (instructionId: string, revision: number) => {
    setActionError(null);
    setMessage(null);

    try {
      const restored = await apiClient.restoreInstructionRevision(instructionId, revision);
      await Promise.all([loadInstructions(), loadInstructionRevisions(instructionId)]);
      setSelectedInstruction(restored);
      setRevisionDiff(null);
      setDetailError(null);
      setMessage(`Инструкция восстановлена из ревизии ${revision}.`);
      return restored;
    } catch (submissionError) {
      setActionError(translateInstructionError(submissionError, "Не удалось восстановить инструкцию"));
      throw submissionError;
    }
  };

  const loadInstructionDiff = async (
    instructionId: string,
    fromRevision: number,
    toRevision: number,
    signal?: AbortSignal,
  ) => {
    setDetailError(null);

    try {
      const payload = await apiClient.fetchInstructionDiff(instructionId, fromRevision, toRevision, signal);
      setRevisionDiff(payload);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateInstructionError(loadError, "Не удалось загрузить diff инструкции");
      setDetailError(nextError);
      return null;
    }
  };

  return {
    instructions,
    selectedInstruction,
    revisions,
    revisionDiff,
    detailError,
    isLoadingDetail,
    error,
    isLoading,
    message,
    actionError,
    deletingInstructionId,
    createInstruction,
    updateInstruction,
    deleteInstruction,
    loadInstruction,
    loadInstructionRevisions,
    loadInstructionDiff,
    restoreInstructionRevision,
  };
};
