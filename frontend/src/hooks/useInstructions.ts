import { useEffect, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { CreateInstructionRequest, Instruction } from "../types";

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
        if (error.code?.startsWith("instructions.storage_")) {
          return "Не удалось выполнить операцию с локальной библиотекой инструкций backend. Повтори попытку ещё раз.";
        }
        return translateCommonApiError(error, fallback);
    }
  }

  return translateCommonApiError(error, fallback);
};

export const useInstructions = () => {
  const [instructions, setInstructions] = useState<Instruction[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingInstructionId, setDeletingInstructionId] = useState<string | null>(null);

  const loadInstructions = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchInstructions(signal);
      setInstructions(payload);
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
      await apiClient.createInstruction(input);
      await loadInstructions();
      setMessage("Инструкция сохранена и теперь реально участвует в execution flow через явный выбор.");
    } catch (submissionError) {
      setActionError(translateInstructionError(submissionError, "Не удалось сохранить инструкцию"));
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
      setMessage("Инструкция удалена из локальной библиотеки.");
    } catch (deleteError) {
      setActionError(translateInstructionError(deleteError, "Не удалось удалить инструкцию"));
      throw deleteError;
    } finally {
      setDeletingInstructionId(null);
    }
  };

  return {
    instructions,
    error,
    isLoading,
    message,
    actionError,
    deletingInstructionId,
    createInstruction,
    deleteInstruction,
  };
};
