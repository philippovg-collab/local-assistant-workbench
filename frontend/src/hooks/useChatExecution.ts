import { useEffect, useRef, useState } from "react";
import { apiClient } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { ChatExecutionRequest, ChatExecutionResponse, ChatMode } from "../types";

type UseChatExecutionOptions = {
  mode: ChatMode;
  initialModel: string;
  initialPrompt: string;
  initialSystemPrompt?: string;
  selectedInstructionIds: string[];
};

export const useChatExecution = ({
  mode,
  initialModel,
  initialPrompt,
  initialSystemPrompt = "",
  selectedInstructionIds,
}: UseChatExecutionOptions) => {
  const [model, setModel] = useState(initialModel);
  const [prompt, setPrompt] = useState(initialPrompt);
  const [systemPrompt, setSystemPrompt] = useState(initialSystemPrompt);
  const [response, setResponse] = useState<ChatExecutionResponse | null>(null);
  const [lastSubmittedRequest, setLastSubmittedRequest] = useState<ChatExecutionRequest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const submitSequenceRef = useRef(0);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const submit = async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const submitSequence = submitSequenceRef.current + 1;
    submitSequenceRef.current = submitSequence;
    setIsSubmitting(true);
    setError(null);

    const request: ChatExecutionRequest = {
      mode,
      model,
      prompt,
      instructionIds: selectedInstructionIds,
      ...(systemPrompt.trim() ? { systemPrompt: systemPrompt.trim() } : {}),
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
    systemPrompt,
    setSystemPrompt,
    response,
    lastSubmittedRequest,
    error,
    isSubmitting,
    submit,
  };
};
