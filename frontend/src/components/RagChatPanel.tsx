import { useEffect, useMemo, useRef, useState } from "react";
import { apiClient } from "@/api/client";
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
  MaterialDetail,
  ModelInfo,
  RetrievalFilters,
  RetrievalQueryHints,
} from "@/types";
import type { RetrievalFilterKey } from "@/utils/retrievalHints";
import {
  buildScopeSummary,
  parseSourceTarget,
  type SourceTarget,
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
  currentRunStatus?: string | null;
  error: string | null;
  response: ChatExecutionResponse | null;
  chatRuns: ChatAuditRunSummary[];
  selectedChatRun: ChatAuditRunDetail | null;
  chatRunsError: string | null;
  onLoadChatRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
  onSubmit: () => Promise<unknown>;
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
  currentRunStatus,
  error,
  response,
  chatRuns,
  selectedChatRun,
  chatRunsError,
  onLoadChatRun,
  onSubmit,
}: RagChatPanelProps) {
  const [openedMaterial, setOpenedMaterial] = useState<MaterialDetail | null>(null);
  const [isLoadingMaterial, setIsLoadingMaterial] = useState(false);
  const [materialError, setMaterialError] = useState<string | null>(null);
  const [openedSourceTarget, setOpenedSourceTarget] = useState<SourceTarget | null>(null);
  const highlightedChunkRef = useRef<HTMLDivElement | null>(null);
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

  useEffect(() => {
    if (!openedMaterial || !openedSourceTarget || !highlightedChunkRef.current) {
      return;
    }

    highlightedChunkRef.current.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }, [openedMaterial, openedSourceTarget]);

  const openSource = async (materialId: string, openSourceUrl?: string | null) => {
    const target = parseSourceTarget(materialId, openSourceUrl);
    setIsLoadingMaterial(true);
    setMaterialError(null);
    setOpenedSourceTarget(target);

    try {
      const detail = normalizedActiveRagProjectKey
        ? await apiClient.fetchMaterial(target.materialId, { workspaceKey: normalizedActiveRagProjectKey })
        : await apiClient.fetchMaterial(target.materialId);
      setOpenedMaterial(detail);
    } catch {
      setMaterialError("Не удалось загрузить источник. Проверь доступность backend и попробуй ещё раз.");
    } finally {
      setIsLoadingMaterial(false);
    }
  };

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

  const closeSourceDialog = () => {
    setOpenedMaterial(null);
    setOpenedSourceTarget(null);
    setMaterialError(null);
  };

  return (
    <>
      <StudioScaffold
        badge="POST /api/chat-runs mode=rag"
        layout="stacked"
        controls={
          <RagChatFormSection
            activeProjectLabel={normalizedActiveRagProjectName || normalizedActiveRagProjectKey || "general"}
            answerMode={answerMode}
            currentRunStatus={currentRunStatus}
            dismissedHintKeys={dismissedHintKeys}
            effectiveRetrievalFilters={effectiveRetrievalFilters}
            error={error}
            helperText={helperText}
            hintOwnedFields={hintOwnedFields}
            instructions={instructions}
            isBlocked={isBlocked}
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
            onAnswerModeChange={onAnswerModeChange}
            onClearRetrievalFilter={onClearRetrievalFilter}
            onDismissHint={onDismissHint}
            onModelChange={onModelChange}
            onPromptChange={onPromptChange}
            onResetDismissedHints={onResetDismissedHints}
            onSubmit={onSubmit}
            onTemporaryInstructionChange={onTemporaryInstructionChange}
            onToggleInstruction={onToggleInstruction}
          />
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
            onOpenSource={(materialId, openSourceUrl) => void openSource(materialId, openSourceUrl)}
          />
        }
        title="Запрос по материалам"
      />

      <RagSourceDialog
        highlightedChunkRef={highlightedChunkRef}
        isLoadingMaterial={isLoadingMaterial}
        materialError={materialError}
        openedMaterial={openedMaterial}
        openedSourceTarget={openedSourceTarget}
        onClose={closeSourceDialog}
      />
    </>
  );
}
