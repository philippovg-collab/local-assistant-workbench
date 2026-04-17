import { useEffect, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { CreateInstructionRequest, InstructionDetail, InstructionSummary } from "../types";

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

export const useInstructions = () => {
  const [instructions, setInstructions] = useState<InstructionSummary[]>([]);
  const [selectedInstruction, setSelectedInstruction] = useState<InstructionDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingInstructionId, setDeletingInstructionId] = useState<string | null>(null);

  const loadInstructions = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchInstructions(signal);
      setInstructions(payload);
      setSelectedInstruction((current) =>
        current && payload.some((instruction) => instruction.id === current.id) ? current : null,
      );
      setError(null);
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
    const controller = new AbortController();
    void loadInstructions(controller.signal);
    return () => controller.abort();
  }, []);

  const createInstruction = async (input: CreateInstructionRequest) => {
    setActionError(null);
    setMessage(null);

    try {
      const created = await apiClient.createInstruction(input);
      await loadInstructions();
      setSelectedInstruction(created);
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

  return {
    instructions,
    selectedInstruction,
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
  };
};
