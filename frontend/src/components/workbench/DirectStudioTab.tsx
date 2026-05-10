import { ConversationThreadPanel } from "@/components/ConversationThreadPanel";
import { DirectChatPanel } from "@/components/DirectChatPanel";
import type { useConversationChatExecution } from "@/hooks/useConversationChatExecution";
import type { useConversationRuns } from "@/hooks/useConversationRuns";
import type { useConversations } from "@/hooks/useConversations";
import type { useChatExecution } from "@/hooks/useChatExecution";
import type { useChatRuns } from "@/hooks/useChatRuns";
import type { InstructionSummary, ModelInfo } from "@/types";

type DirectStudioTabProps = {
  chatRuns: ReturnType<typeof useChatRuns>;
  conversationsEnabled: boolean;
  conversationChat: ReturnType<typeof useConversationChatExecution>;
  conversationRuns: ReturnType<typeof useConversationRuns>;
  conversations: ReturnType<typeof useConversations>;
  directChat: ReturnType<typeof useChatExecution>;
  helperText: string;
  instructions: InstructionSummary[];
  isBlocked: boolean;
  longTermMemoryEnabled: boolean;
  models: ModelInfo[];
  modelsError: string | null;
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
};

export function DirectStudioTab({
  chatRuns,
  conversationsEnabled,
  conversationChat,
  conversationRuns,
  conversations,
  directChat,
  helperText,
  instructions,
  isBlocked,
  longTermMemoryEnabled,
  models,
  modelsError,
  selectedInstructionIds,
  onToggleInstruction,
}: DirectStudioTabProps) {
  const conversational = conversationsEnabled && Boolean(conversations.selectedConversationId);
  const activeChat = conversational ? conversationChat : directChat;
  const submit = conversational ? conversationChat.submit : directChat.submit;
  const toggleInstruction = (instructionId: string) => {
    if (conversational) {
      conversationChat.markInstructionIdsDirty();
    }
    onToggleInstruction(instructionId);
  };

  return (
    <DirectChatPanel
      answerMode={activeChat.answerMode}
      chatRuns={chatRuns.runs}
      chatRunsError={chatRuns.error}
      currentRunId={activeChat.currentRunId}
      currentRunStatus={activeChat.currentRunStatus}
      useLongTermMemory={conversational && longTermMemoryEnabled ? conversationChat.useLongTermMemory : undefined}
      error={activeChat.error}
      helperText={helperText}
      instructions={instructions}
      isBlocked={isBlocked}
      isCancelling={activeChat.isCancelling}
      isSubmitting={activeChat.isSubmitting}
      models={models}
      modelsError={modelsError}
      prompt={activeChat.prompt}
      requestPreview={activeChat.lastSubmittedRequest && (activeChat.isSubmitting || activeChat.response)
        ? activeChat.lastSubmittedRequest
        : {
            mode: "direct",
            model: activeChat.model,
            prompt: activeChat.prompt,
            instructionIds: selectedInstructionIds,
            answerMode: activeChat.answerMode,
            ...(activeChat.temporaryInstruction.trim()
              ? { temporaryInstruction: activeChat.temporaryInstruction.trim() }
              : {}),
          }}
      response={activeChat.response}
      selectedChatRun={chatRuns.selectedRun}
      selectedInstructionIds={selectedInstructionIds}
      selectedModel={activeChat.model}
      temporaryInstruction={activeChat.temporaryInstruction}
      conversationPanel={conversationsEnabled ? (
        <ConversationThreadPanel
          conversations={conversations.conversations}
          currentRunId={activeChat.currentRunId}
          error={conversations.error}
          isLoading={conversationRuns.isLoading || conversations.isLoadingDetail}
          isMutating={conversations.isMutating}
          runs={conversationRuns.runs}
          runsError={conversationRuns.error}
          selectedConversationId={conversations.selectedConversationId}
          onArchiveConversation={conversations.archiveConversation}
          onCreateConversation={() => conversations.createConversation({
            title: directChat.prompt,
            defaultModel: directChat.model,
            defaultAnswerMode: directChat.answerMode,
          })}
          onRefreshRuns={conversationRuns.loadRuns}
          onSelectConversation={conversations.setSelectedConversationId}
        />
      ) : null}
      onAnswerModeChange={activeChat.setAnswerMode}
      onCancelCurrentRun={activeChat.cancelCurrentRun}
      onLoadChatRun={(runId) => chatRuns.loadRun(runId)}
      onModelChange={activeChat.setModel}
      onPromptChange={activeChat.setPrompt}
      onSubmit={submit}
      onTemporaryInstructionChange={activeChat.setTemporaryInstruction}
      onToggleInstruction={toggleInstruction}
      onUseLongTermMemoryChange={conversational && longTermMemoryEnabled ? conversationChat.setUseLongTermMemory : undefined}
    />
  );
}
