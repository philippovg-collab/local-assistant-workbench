import { RagChatPanel } from "@/components/RagChatPanel";
import type { useChatExecution } from "@/hooks/useChatExecution";
import type { useChatRuns } from "@/hooks/useChatRuns";
import type { useKnowledgeFacets } from "@/hooks/useKnowledgeFacets";
import type { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import type { ModelInfo, InstructionSummary } from "@/types";

type RagStudioTabProps = {
  activeRagProjectKey: string;
  activeRagProjectName: string;
  chatRuns: ReturnType<typeof useChatRuns>;
  instructions: InstructionSummary[];
  knowledgeFacets: ReturnType<typeof useKnowledgeFacets>;
  knowledgePresets: ReturnType<typeof useKnowledgePresets>;
  models: ModelInfo[];
  modelsError: string | null;
  ragChat: ReturnType<typeof useChatExecution>;
  selectedInstructionIds: string[];
  helperText: string;
  isBlocked: boolean;
  onToggleInstruction: (instructionId: string) => void;
};

export function RagStudioTab({
  activeRagProjectKey,
  activeRagProjectName,
  chatRuns,
  helperText,
  instructions,
  isBlocked,
  knowledgeFacets,
  knowledgePresets,
  models,
  modelsError,
  ragChat,
  selectedInstructionIds,
  onToggleInstruction,
}: RagStudioTabProps) {
  return (
    <RagChatPanel
      activeRagProjectKey={activeRagProjectKey}
      activeRagProjectName={activeRagProjectName}
      answerMode={ragChat.answerMode}
      chatRuns={chatRuns.runs}
      chatRunsError={chatRuns.error}
      currentRunStatus={ragChat.currentRunStatus}
      dismissedHintKeys={ragChat.dismissedHintKeys}
      effectiveRetrievalFilters={ragChat.effectiveRetrievalFilters}
      error={ragChat.error}
      helperText={helperText}
      hintOwnedFields={ragChat.hintOwnedFields}
      instructions={instructions}
      isBlocked={isBlocked}
      isSubmitting={ragChat.isSubmitting}
      knowledgeFacets={knowledgeFacets.presets}
      knowledgePresets={knowledgePresets.presets}
      knowledgeScope={ragChat.knowledgeScope}
      manualOwnedFields={ragChat.manualOwnedFields}
      metadataFiltersEnabled={ragChat.metadataFiltersEnabled}
      models={models}
      modelsError={modelsError}
      prompt={ragChat.prompt}
      queryHints={ragChat.queryHints}
      queryHintsEnabled={ragChat.queryHintsEnabled}
      response={ragChat.response}
      retrievalFilters={ragChat.retrievalFilters}
      selectedChatRun={chatRuns.selectedRun}
      selectedInstructionIds={selectedInstructionIds}
      selectedModel={ragChat.model}
      temporaryInstruction={ragChat.temporaryInstruction}
      onAnswerModeChange={ragChat.setAnswerMode}
      onClearRetrievalFilter={ragChat.clearRetrievalFilter}
      onDismissHint={ragChat.dismissHint}
      onKnowledgeScopeChange={ragChat.setKnowledgeScope}
      onLoadChatRun={(runId) => chatRuns.loadRun(runId)}
      onModelChange={ragChat.setModel}
      onPromptChange={ragChat.setPrompt}
      onResetDismissedHints={ragChat.resetDismissedHints}
      onRetrievalFilterChange={ragChat.updateRetrievalFilter}
      onSubmit={ragChat.submit}
      onTemporaryInstructionChange={ragChat.setTemporaryInstruction}
      onToggleInstruction={onToggleInstruction}
    />
  );
}
