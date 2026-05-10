import { useEffect, useMemo, type ReactNode } from "react";
import { buildKnowledgeScopeControls } from "@/components/KnowledgeScopeControls";
import { RagChatFormSection } from "@/components/RagChatFormSection";
import { RagResponsePanel } from "@/components/RagResponsePanel";
import { RagSourceDialog } from "@/components/RagSourceDialog";
import { StudioScaffold } from "@/components/app/StudioScaffold";
import type {
  AnswerMode,
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatExecutionResponse,
  InstructionSummary,
  KnowledgePresetSummary,
  KnowledgeScope,
  ModelInfo,
  RetrievalFilters,
  RetrievalQueryHints,
} from "@/types";
import type { MaterialSourceDialogState } from "@/hooks/useMaterialSourceDialog";
import type { RetrievalFilterKey } from "@/utils/retrievalHints";
import {
  buildScopeSummary,
} from "@/components/ragChatPresentation";

type RagChatPanelProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  answerMode: AnswerMode;
  onAnswerModeChange: (value: AnswerMode) => void;
  prompt: string;
  onPromptChange: (value: string) => void;
  temporaryInstruction: string;
  onTemporaryInstructionChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  knowledgePresets: KnowledgePresetSummary[];
  knowledgeFacets: KnowledgePresetSummary[];
  activeRagProjectKey?: string | null;
  activeRagProjectName?: string | null;
  knowledgeScope: KnowledgeScope;
  onKnowledgeScopeChange: (nextScope: KnowledgeScope) => void;
  retrievalFilters: RetrievalFilters;
  effectiveRetrievalFilters: RetrievalFilters;
  queryHints: RetrievalQueryHints;
  metadataFiltersEnabled: boolean;
  queryHintsEnabled: boolean;
  hintOwnedFields: RetrievalFilterKey[];
  manualOwnedFields: RetrievalFilterKey[];
  dismissedHintKeys: RetrievalFilterKey[];
  onRetrievalFilterChange: (key: RetrievalFilterKey, value: RetrievalFilters[RetrievalFilterKey]) => void;
  onClearRetrievalFilter: (key: RetrievalFilterKey) => void;
  onDismissHint: (key: RetrievalFilterKey) => void;
  onResetDismissedHints: () => void;
  helperText: string;
  isBlocked: boolean;
  isSubmitting: boolean;
  isCancelling?: boolean;
  currentRunId?: string | null;
  currentRunStatus?: string | null;
  useLongTermMemory?: boolean;
  error: string | null;
  response: ChatExecutionResponse | null;
  chatRuns: ChatAuditRunSummary[];
  selectedChatRun: ChatAuditRunDetail | null;
  chatRunsError: string | null;
  sourceDialog: MaterialSourceDialogState;
  onLoadChatRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
  onSubmit: () => Promise<unknown>;
  onCancelCurrentRun?: () => Promise<unknown>;
  onUseLongTermMemoryChange?: (value: boolean) => void;
  conversationPanel?: ReactNode;
};

export function RagChatPanel({
  models,
  modelsError,
  selectedModel,
  onModelChange,
  answerMode,
  onAnswerModeChange,
  prompt,
  onPromptChange,
  temporaryInstruction,
  onTemporaryInstructionChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  knowledgePresets,
  knowledgeFacets,
  activeRagProjectKey,
  activeRagProjectName,
  knowledgeScope,
  onKnowledgeScopeChange,
  retrievalFilters,
  effectiveRetrievalFilters,
  queryHints,
  metadataFiltersEnabled,
  queryHintsEnabled,
  hintOwnedFields,
  manualOwnedFields,
  dismissedHintKeys,
  onClearRetrievalFilter,
  onDismissHint,
  onResetDismissedHints,
  helperText,
  isBlocked,
  isSubmitting,
  isCancelling,
  currentRunId,
  currentRunStatus,
  useLongTermMemory,
  error,
  response,
  chatRuns,
  selectedChatRun,
  chatRunsError,
  sourceDialog,
  onLoadChatRun,
  onSubmit,
  onCancelCurrentRun,
  onUseLongTermMemoryChange,
  conversationPanel,
}: RagChatPanelProps) {
  const normalizedActiveRagProjectKey = (activeRagProjectKey ?? "").trim();
  const normalizedActiveRagProjectName = activeRagProjectName ?? "";

  const visibleKnowledgePresets = useMemo(
    () =>
      knowledgePresets.filter((preset) =>
        preset.active
        && (!preset.workspaceKey || preset.workspaceKey === normalizedActiveRagProjectKey)),
    [knowledgePresets, normalizedActiveRagProjectKey],
  );
  const visibleKnowledgeFacets = useMemo(
    () =>
      knowledgeFacets.filter((facet) =>
        facet.active
        && (!facet.workspaceKey || facet.workspaceKey === normalizedActiveRagProjectKey)),
    [knowledgeFacets, normalizedActiveRagProjectKey],
  );

  useEffect(() => {
    const visiblePresetIds = new Set(visibleKnowledgePresets.map((preset) => preset.id));
    const visibleFacetIds = new Set(visibleKnowledgeFacets.map((facet) => facet.id));
    const nextPresetIds = knowledgeScope.presetIds.filter((presetId) => visiblePresetIds.has(presetId));
    const nextFacetIds = (knowledgeScope.facetIds ?? []).filter((facetId) => visibleFacetIds.has(facetId));
    const nextWorkspaceKey = normalizedActiveRagProjectKey || null;
    if (
      nextPresetIds.length !== knowledgeScope.presetIds.length
      || nextFacetIds.length !== (knowledgeScope.facetIds ?? []).length
      || knowledgeScope.workspaceKey !== nextWorkspaceKey
    ) {
      onKnowledgeScopeChange({
        ...knowledgeScope,
        presetIds: nextPresetIds,
        facetIds: nextFacetIds,
        workspaceKey: nextWorkspaceKey,
      });
    }
  }, [knowledgeScope, normalizedActiveRagProjectKey, onKnowledgeScopeChange, visibleKnowledgeFacets, visibleKnowledgePresets]);

  const scopeSummary = useMemo(
    () => buildScopeSummary({
      knowledgeScope,
      activeRagProjectKey: normalizedActiveRagProjectKey,
      activeRagProjectName: normalizedActiveRagProjectName,
      visibleKnowledgePresets,
      visibleKnowledgeFacets,
    }),
    [knowledgeScope, normalizedActiveRagProjectKey, normalizedActiveRagProjectName, visibleKnowledgeFacets, visibleKnowledgePresets],
  );

  const knowledgeControls = useMemo(
    () => buildKnowledgeScopeControls({
      knowledgeScope,
      onKnowledgeScopeChange,
      visibleKnowledgePresets,
      visibleKnowledgeFacets,
      workspaceKey: normalizedActiveRagProjectKey,
      workspaceName: normalizedActiveRagProjectName,
      activeScopeSummary: scopeSummary,
    }),
    [knowledgeScope, normalizedActiveRagProjectKey, normalizedActiveRagProjectName, onKnowledgeScopeChange, scopeSummary, visibleKnowledgeFacets, visibleKnowledgePresets],
  );

  return (
    <>
      <StudioScaffold
        badge="POST /api/chat-runs mode=rag"
        layout="stacked"
        controls={
          <div className="space-y-5">
            {conversationPanel}
            <RagChatFormSection
              activeProjectLabel={normalizedActiveRagProjectName || normalizedActiveRagProjectKey || "general"}
              answerMode={answerMode}
              currentRunStatus={currentRunStatus}
              currentRunId={currentRunId}
              dismissedHintKeys={dismissedHintKeys}
              effectiveRetrievalFilters={effectiveRetrievalFilters}
              error={error}
              helperText={helperText}
              hintOwnedFields={hintOwnedFields}
              instructions={instructions}
              isBlocked={isBlocked}
              isCancelling={isCancelling}
              isSubmitting={isSubmitting}
              knowledgeControls={knowledgeControls}
              manualOwnedFields={manualOwnedFields}
              metadataFiltersEnabled={metadataFiltersEnabled}
              models={models}
              modelsError={modelsError}
              prompt={prompt}
              queryHintsEnabled={queryHintsEnabled}
              retrievalFilters={retrievalFilters}
              selectedInstructionIds={selectedInstructionIds}
              selectedModel={selectedModel}
              temporaryInstruction={temporaryInstruction}
              useLongTermMemory={useLongTermMemory}
              onAnswerModeChange={onAnswerModeChange}
              onCancelCurrentRun={onCancelCurrentRun}
              onClearRetrievalFilter={onClearRetrievalFilter}
              onDismissHint={onDismissHint}
              onModelChange={onModelChange}
              onPromptChange={onPromptChange}
              onResetDismissedHints={onResetDismissedHints}
              onSubmit={onSubmit}
              onTemporaryInstructionChange={onTemporaryInstructionChange}
              onToggleInstruction={onToggleInstruction}
              onUseLongTermMemoryChange={onUseLongTermMemoryChange}
            />
          </div>
        }
        description="RAG-режим теперь показывает не только ответ, но и instruction trace, knowledge scope, retrieval trace и конкретные чанки, из которых он был собран."
        eyebrow="RAG Studio"
        results={
          <RagResponsePanel
            activeRagProjectKey={normalizedActiveRagProjectKey}
            activeRagProjectName={normalizedActiveRagProjectName}
            answerMode={answerMode}
            chatRuns={chatRuns}
            chatRunsError={chatRunsError}
            effectiveRetrievalFilters={effectiveRetrievalFilters}
            queryHints={queryHints}
            response={response}
            selectedChatRun={selectedChatRun}
            onLoadChatRun={onLoadChatRun}
            onOpenSource={(materialId, openSourceUrl) => void sourceDialog.openSource(materialId, openSourceUrl)}
          />
        }
        title="Запрос по материалам"
      />

      <RagSourceDialog
        highlightedChunkRef={sourceDialog.highlightedChunkRef}
        isLoadingMaterial={sourceDialog.isLoadingMaterial}
        materialError={sourceDialog.materialError}
        openedMaterial={sourceDialog.openedMaterial}
        openedSourceTarget={sourceDialog.openedSourceTarget}
        onClose={sourceDialog.closeSourceDialog}
      />
    </>
  );
}
