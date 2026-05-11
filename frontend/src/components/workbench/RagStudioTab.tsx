import { ConversationThreadPanel } from "@/components/ConversationThreadPanel";
import type { ChatAuditCandidateAction } from "@/components/ChatAuditPanel";
import { RagChatPanel } from "@/components/RagChatPanel";
import type { useConversationChatExecution } from "@/hooks/useConversationChatExecution";
import type { useConversationRuns } from "@/hooks/useConversationRuns";
import type { useConversations } from "@/hooks/useConversations";
import type { useChatExecution } from "@/hooks/useChatExecution";
import type { useChatRuns } from "@/hooks/useChatRuns";
import type { useKnowledgeFacets } from "@/hooks/useKnowledgeFacets";
import type { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import { useMaterialSourceDialog } from "@/hooks/useMaterialSourceDialog";
import type { ModelInfo, InstructionSummary } from "@/types";

type RagStudioTabProps = {
  activeRagProjectKey: string;
  activeRagProjectName: string;
  chatRuns: ReturnType<typeof useChatRuns>;
  conversationsEnabled: boolean;
  conversationChat: ReturnType<typeof useConversationChatExecution>;
  conversationRuns: ReturnType<typeof useConversationRuns>;
  conversations: ReturnType<typeof useConversations>;
  instructions: InstructionSummary[];
  knowledgeFacets: ReturnType<typeof useKnowledgeFacets>;
  knowledgePresets: ReturnType<typeof useKnowledgePresets>;
  models: ModelInfo[];
  modelsError: string | null;
  ragChat: ReturnType<typeof useChatExecution>;
  selectedInstructionIds: string[];
  helperText: string;
  isBlocked: boolean;
  longTermMemoryEnabled: boolean;
  evalCandidateAction?: ChatAuditCandidateAction | null;
  onToggleInstruction: (instructionId: string) => void;
};

export function RagStudioTab({
  activeRagProjectKey,
  activeRagProjectName,
  chatRuns,
  conversationsEnabled,
  conversationChat,
  conversationRuns,
  conversations,
  helperText,
  instructions,
  isBlocked,
  longTermMemoryEnabled,
  knowledgeFacets,
  knowledgePresets,
  models,
  modelsError,
  ragChat,
  selectedInstructionIds,
  evalCandidateAction,
  onToggleInstruction,
}: RagStudioTabProps) {
  const sourceDialog = useMaterialSourceDialog(activeRagProjectKey);
  const conversational = conversationsEnabled && Boolean(conversations.selectedConversationId);
  const activeChat = conversational ? conversationChat : ragChat;
  const submit = conversational ? conversationChat.submit : ragChat.submit;
  const toggleInstruction = (instructionId: string) => {
    if (conversational) {
      conversationChat.markInstructionIdsDirty();
    }
    onToggleInstruction(instructionId);
  };

  return (
    <RagChatPanel
      activeRagProjectKey={activeRagProjectKey}
      activeRagProjectName={activeRagProjectName}
      answerMode={activeChat.answerMode}
      chatRuns={chatRuns.runs}
      chatRunsError={chatRuns.error}
      currentRunId={activeChat.currentRunId}
      currentRunStatus={activeChat.currentRunStatus}
      useLongTermMemory={conversational && longTermMemoryEnabled ? conversationChat.useLongTermMemory : undefined}
      dismissedHintKeys={activeChat.dismissedHintKeys}
      effectiveRetrievalFilters={activeChat.effectiveRetrievalFilters}
      error={activeChat.error}
      evalCandidateAction={evalCandidateAction}
      helperText={helperText}
      hintOwnedFields={activeChat.hintOwnedFields}
      instructions={instructions}
      isBlocked={isBlocked}
      isCancelling={activeChat.isCancelling}
      isSubmitting={activeChat.isSubmitting}
      knowledgeFacets={knowledgeFacets.presets}
      knowledgePresets={knowledgePresets.presets}
      knowledgeScope={activeChat.knowledgeScope}
      manualOwnedFields={activeChat.manualOwnedFields}
      metadataFiltersEnabled={activeChat.metadataFiltersEnabled}
      models={models}
      modelsError={modelsError}
      prompt={activeChat.prompt}
      queryHints={activeChat.queryHints}
      queryHintsEnabled={activeChat.queryHintsEnabled}
      response={activeChat.response}
      retrievalFilters={activeChat.retrievalFilters}
      selectedChatRun={chatRuns.selectedRun}
      selectedInstructionIds={selectedInstructionIds}
      selectedModel={activeChat.model}
      sourceDialog={sourceDialog}
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
            title: ragChat.prompt,
            defaultModel: ragChat.model,
            defaultAnswerMode: ragChat.answerMode,
          })}
          onRefreshRuns={conversationRuns.loadRuns}
          onSelectConversation={conversations.setSelectedConversationId}
        />
      ) : null}
      onAnswerModeChange={activeChat.setAnswerMode}
      onCancelCurrentRun={activeChat.cancelCurrentRun}
      onClearRetrievalFilter={activeChat.clearRetrievalFilter}
      onDismissHint={activeChat.dismissHint}
      onKnowledgeScopeChange={activeChat.setKnowledgeScope}
      onLoadChatRun={(runId) => chatRuns.loadRun(runId)}
      onModelChange={activeChat.setModel}
      onPromptChange={activeChat.setPrompt}
      onResetDismissedHints={activeChat.resetDismissedHints}
      onRetrievalFilterChange={activeChat.updateRetrievalFilter}
      onSubmit={submit}
      onTemporaryInstructionChange={activeChat.setTemporaryInstruction}
      onToggleInstruction={toggleInstruction}
      onUseLongTermMemoryChange={conversational && longTermMemoryEnabled ? conversationChat.setUseLongTermMemory : undefined}
    />
  );
}
