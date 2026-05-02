import { DirectChatPanel } from "@/components/DirectChatPanel";
import type { useChatExecution } from "@/hooks/useChatExecution";
import type { useChatRuns } from "@/hooks/useChatRuns";
import type { InstructionSummary, ModelInfo } from "@/types";

type DirectStudioTabProps = {
  chatRuns: ReturnType<typeof useChatRuns>;
  directChat: ReturnType<typeof useChatExecution>;
  helperText: string;
  instructions: InstructionSummary[];
  isBlocked: boolean;
  models: ModelInfo[];
  modelsError: string | null;
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
};

export function DirectStudioTab({
  chatRuns,
  directChat,
  helperText,
  instructions,
  isBlocked,
  models,
  modelsError,
  selectedInstructionIds,
  onToggleInstruction,
}: DirectStudioTabProps) {
  return (
    <DirectChatPanel
      answerMode={directChat.answerMode}
      chatRuns={chatRuns.runs}
      chatRunsError={chatRuns.error}
      currentRunStatus={directChat.currentRunStatus}
      error={directChat.error}
      helperText={helperText}
      instructions={instructions}
      isBlocked={isBlocked}
      isSubmitting={directChat.isSubmitting}
      models={models}
      modelsError={modelsError}
      prompt={directChat.prompt}
      requestPreview={directChat.lastSubmittedRequest && (directChat.isSubmitting || directChat.response)
        ? directChat.lastSubmittedRequest
        : {
            mode: "direct",
            model: directChat.model,
            prompt: directChat.prompt,
            instructionIds: selectedInstructionIds,
            answerMode: directChat.answerMode,
            ...(directChat.temporaryInstruction.trim()
              ? { temporaryInstruction: directChat.temporaryInstruction.trim() }
              : {}),
          }}
      response={directChat.response}
      selectedChatRun={chatRuns.selectedRun}
      selectedInstructionIds={selectedInstructionIds}
      selectedModel={directChat.model}
      temporaryInstruction={directChat.temporaryInstruction}
      onAnswerModeChange={directChat.setAnswerMode}
      onLoadChatRun={(runId) => chatRuns.loadRun(runId)}
      onModelChange={directChat.setModel}
      onPromptChange={directChat.setPrompt}
      onSubmit={directChat.submit}
      onTemporaryInstructionChange={directChat.setTemporaryInstruction}
      onToggleInstruction={onToggleInstruction}
    />
  );
}
