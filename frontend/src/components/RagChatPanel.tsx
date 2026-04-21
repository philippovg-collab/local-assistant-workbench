import { useEffect, useMemo, useRef, useState } from "react";
import { BookOpenText, ExternalLink, Files, SearchCheck, SlidersHorizontal, Sparkles, X } from "lucide-react";
import { apiClient } from "@/api/client";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { StudioScaffold } from "@/components/app/StudioScaffold";
import { AppliedInstructionList } from "@/components/AppliedInstructionList";
import { ChatAuditPanel } from "@/components/ChatAuditPanel";
import { ChatForm } from "@/components/ChatForm";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
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
import { formatDate } from "@/utils/format";
import { parseTagsInput } from "@/utils/materialMetadata";
import {
  formatRetrievalFilterValue,
  hasRetrievalFilterValue,
  type RetrievalFilterKey,
} from "@/utils/retrievalHints";
import {
  answerModeLabels,
  knowledgeDocumentClassLabels,
  knowledgeScopeResolvedWithDefaults,
  retrievalTraceWithDefaults,
  supportVerdictLabels,
} from "@/utils/workbenchPresentation";

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

const tagTextOf = (scope: KnowledgeScope) => scope.tags.join(", ");
const normalizeTags = (value: string) =>
  value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);

const retrievalFilterLabels: Record<RetrievalFilterKey, string> = {
  documentNumber: "Номер документа",
  documentDateFrom: "Дата с",
  documentDateTo: "Дата по",
  department: "Подразделение",
  project: "Проект",
  counterparty: "Контрагент",
  businessStatus: "Статус",
  language: "Язык",
  tags: "Теги / ключевые слова",
  sourceTrustMin: "Минимальное доверие",
};

type SourceTarget = {
  materialId: string;
  chunkId?: string | null;
  chunkIndex?: number | null;
  page?: number | null;
};

const parseSourceTarget = (materialId: string, openSourceUrl?: string | null): SourceTarget => {
  if (!openSourceUrl) {
    return {
      materialId,
      chunkId: null,
      chunkIndex: null,
      page: null,
    };
  }

  try {
    const parsed = new URL(openSourceUrl, window.location.origin);
    const pathParts = parsed.pathname.split("/").filter(Boolean);
    const resolvedMaterialId = pathParts[pathParts.length - 1] ?? materialId;
    const chunkIndexRaw = parsed.searchParams.get("chunkIndex");
    const pageRaw = parsed.searchParams.get("page");

    return {
      materialId: resolvedMaterialId,
      chunkId: parsed.searchParams.get("chunkId"),
      chunkIndex: chunkIndexRaw ? Number.parseInt(chunkIndexRaw, 10) : null,
      page: pageRaw ? Number.parseInt(pageRaw, 10) : null,
    };
  } catch {
    return {
      materialId,
      chunkId: null,
      chunkIndex: null,
      page: null,
    };
  }
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
  onRetrievalFilterChange,
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
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);
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

  useEffect(() => {
    const visiblePresetIds = new Set(visibleKnowledgePresets.map((preset) => preset.id));
    const nextPresetIds = knowledgeScope.presetIds.filter((presetId) => visiblePresetIds.has(presetId));
    const nextWorkspaceKey = normalizedActiveRagProjectKey || null;
    if (
      nextPresetIds.length !== knowledgeScope.presetIds.length
      || knowledgeScope.workspaceKey !== nextWorkspaceKey
    ) {
      onKnowledgeScopeChange({
        ...knowledgeScope,
        presetIds: nextPresetIds,
        workspaceKey: nextWorkspaceKey,
      });
    }
  }, [knowledgeScope, normalizedActiveRagProjectKey, onKnowledgeScopeChange, visibleKnowledgePresets]);

  const scopeSummary = useMemo(() => {
    const projectLabel = normalizedActiveRagProjectName
      ? `${normalizedActiveRagProjectName} (${normalizedActiveRagProjectKey || "general"})`
      : normalizedActiveRagProjectKey || "general";
    const parts = [`RAG-проект: ${projectLabel}`];
    const selectedPresets = visibleKnowledgePresets
      .filter((preset) => knowledgeScope.presetIds.includes(preset.id))
      .map((preset) => preset.name);

    if (selectedPresets.length > 0) {
      parts.push(`presets: ${selectedPresets.join(", ")}`);
    }
    if (knowledgeScope.documentClasses.length > 0) {
      parts.push(
        `классы: ${knowledgeScope.documentClasses.map((item) => knowledgeDocumentClassLabels[item]).join(", ")}`,
      );
    }
    if (knowledgeScope.tags.length > 0) {
      parts.push(`теги: ${knowledgeScope.tags.join(", ")}`);
    }
    if (knowledgeScope.uploadedTodayOnly) {
      parts.push("только загруженные сегодня");
    }

    return `Запрос будет искать внутри активного корпуса: ${parts.join(" · ")}.`;
  }, [knowledgeScope, normalizedActiveRagProjectKey, normalizedActiveRagProjectName, visibleKnowledgePresets]);

  const resolvedScope = knowledgeScopeResolvedWithDefaults(response?.knowledgeScopeResolved);
  const retrievalTrace = retrievalTraceWithDefaults(response?.retrievalTrace);
  const retrievalDebug = response?.retrievalDebug ?? null;
  const activeHintChips = hintOwnedFields
    .filter((key) => hasRetrievalFilterValue(effectiveRetrievalFilters[key]))
    .map((key) => ({
      key,
      label: retrievalFilterLabels[key],
      value: formatRetrievalFilterValue(key, effectiveRetrievalFilters[key]),
    }));
  const activeManualChips = manualOwnedFields
    .filter((key) => hasRetrievalFilterValue(retrievalFilters[key]))
    .map((key) => ({
      key,
      label: retrievalFilterLabels[key],
      value: formatRetrievalFilterValue(key, retrievalFilters[key]),
    }));
  const effectiveFilterLines = Object.entries(effectiveRetrievalFilters)
    .filter(([, value]) => hasRetrievalFilterValue(value))
    .map(([key, value]) => `${retrievalFilterLabels[key as RetrievalFilterKey] ?? key}: ${Array.isArray(value) ? value.join(", ") : value}`);
  const queryHintLines = Object.entries(queryHints)
    .filter(([, value]) => hasRetrievalFilterValue(value))
    .map(([key, value]) => `${key}: ${value}`);

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

  const knowledgeControls = {
    presets: visibleKnowledgePresets,
    selectedPresetIds: knowledgeScope.presetIds,
    onTogglePreset: (presetId: string) =>
      onKnowledgeScopeChange({
        ...knowledgeScope,
        presetIds: knowledgeScope.presetIds.includes(presetId)
          ? knowledgeScope.presetIds.filter((id) => id !== presetId)
          : [...knowledgeScope.presetIds, presetId],
      }),
    selectedDocumentClasses: knowledgeScope.documentClasses,
    onToggleDocumentClass: (documentClass: KnowledgeScope["documentClasses"][number]) =>
      onKnowledgeScopeChange({
        ...knowledgeScope,
        documentClasses: knowledgeScope.documentClasses.includes(documentClass)
          ? knowledgeScope.documentClasses.filter((item) => item !== documentClass)
          : [...knowledgeScope.documentClasses, documentClass],
      }),
    knowledgeTagsText: tagTextOf(knowledgeScope),
    onKnowledgeTagsTextChange: (value: string) =>
      onKnowledgeScopeChange({
        ...knowledgeScope,
        tags: normalizeTags(value),
      }),
    workspaceKey: normalizedActiveRagProjectKey,
    workspaceName: normalizedActiveRagProjectName,
    uploadedTodayOnly: knowledgeScope.uploadedTodayOnly,
    onUploadedTodayOnlyChange: (value: boolean) =>
      onKnowledgeScopeChange({
        ...knowledgeScope,
        uploadedTodayOnly: value,
      }),
    activeScopeSummary: scopeSummary,
  };

  return (
    <>
      <StudioScaffold
        badge="POST /api/chat mode=rag"
        layout="stacked"
        controls={
          <>
            <div className="mb-4 space-y-3 rounded-[24px] border border-border bg-surface-subtle/80 p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-foreground">Фильтры и подсказки поиска</p>
                  <p className="text-xs leading-5 text-muted-foreground">
                    Активный RAG-проект уже зафиксирован: {normalizedActiveRagProjectName || normalizedActiveRagProjectKey || "general"}.
                    Подсказки из вопроса и ручные фильтры работают внутри него.
                  </p>
                </div>
                <Button type="button" variant="outline" onClick={() => setIsFilterDrawerOpen(true)}>
                  <SlidersHorizontal className="h-4 w-4" />
                  Фильтры поиска
                </Button>
              </div>

              {activeHintChips.length > 0 || activeManualChips.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {activeHintChips.map((chip) => (
                    <div
                      className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs text-foreground"
                      key={`hint-${chip.key}`}
                    >
                      <Badge variant="default">hint</Badge>
                      <span>{chip.label}: {chip.value}</span>
                      <button
                        aria-label={`Remove hint ${chip.label}`}
                        className="rounded-full p-1 text-muted-foreground transition hover:bg-black/5 hover:text-foreground"
                        type="button"
                        onClick={() => onDismissHint(chip.key)}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                  {activeManualChips.map((chip) => (
                    <div
                      className="inline-flex items-center gap-2 rounded-full border border-border bg-field px-3 py-1 text-xs text-foreground"
                      key={`manual-${chip.key}`}
                    >
                      <Badge variant="secondary">manual</Badge>
                      <span>{chip.label}: {chip.value}</span>
                      <button
                        aria-label={`Clear filter ${chip.label}`}
                        className="rounded-full p-1 text-muted-foreground transition hover:bg-black/5 hover:text-foreground"
                        type="button"
                        onClick={() => onClearRetrievalFilter(chip.key)}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm leading-6 text-muted-foreground">
                  {metadataFiltersEnabled
                    ? queryHintsEnabled
                      ? "Пока фильтры не заданы. После ввода запроса здесь появятся auto hints и ручные фасеты."
                      : "Пока фильтры не заданы. Auto hints отключены rollout-флагом, но ручные фасеты доступны."
                    : "Metadata filters отключены rollout-флагом; поля в drawer недоступны и запрос уйдёт без retrievalFilters."}
                </p>
              )}

              {queryHintsEnabled && dismissedHintKeys.length > 0 ? (
                <div className="flex items-center justify-between gap-3 rounded-[18px] border border-border bg-field px-4 py-3">
                  <p className="text-xs text-muted-foreground">
                    Скрытые подсказки не вернутся, пока ты не восстановишь их вручную.
                  </p>
                  <Button size="sm" type="button" variant="outline" onClick={onResetDismissedHints}>
                    Вернуть подсказки
                  </Button>
                </div>
              ) : null}
            </div>

            <ChatForm
              answerMode={answerMode}
              error={error}
              helperText={isSubmitting && currentRunStatus ? `${helperText} Статус запуска: ${currentRunStatus}.` : helperText}
              instructionEmptyStateMessage="Сначала создай chat/scenario инструкцию во вкладке библиотеки."
              instructions={instructions}
              isSubmitDisabled={isSubmitting || !prompt.trim() || isBlocked}
              isSubmitting={isSubmitting}
              knowledgeControls={knowledgeControls}
              models={models}
              modelsError={modelsError}
              onAnswerModeChange={onAnswerModeChange}
              onModelChange={onModelChange}
              onPromptChange={onPromptChange}
              onSubmit={onSubmit}
              onTemporaryInstructionChange={onTemporaryInstructionChange}
              onToggleInstruction={onToggleInstruction}
              prompt={prompt}
              promptLabel="Вопрос"
              promptPlaceholder="Например: Какие условия тарифа Премиум?"
              promptRows={6}
              selectedInstructionIds={selectedInstructionIds}
              selectedModel={selectedModel}
              submitBusyLabel="Ищем контекст..."
              submitIdleLabel="Спросить по материалам"
              temporaryInstruction={temporaryInstruction}
              temporaryInstructionLabel="Временная инструкция на этот запрос"
              temporaryInstructionPlaceholder="Например: если в источниках нет подтверждения, скажи об этом прямо."
              temporaryInstructionRows={4}
            />

            <Sheet open={isFilterDrawerOpen} onOpenChange={setIsFilterDrawerOpen}>
              <SheetContent side="right" className="overflow-y-auto">
                <SheetHeader>
                  <SheetTitle>Фильтры и подсказки поиска</SheetTitle>
                  <SheetDescription>
                    {metadataFiltersEnabled
                      ? queryHintsEnabled
                        ? "Ручные фильтры важнее подсказок из вопроса. Поля совпадают с атрибутами материала при загрузке."
                        : "Ручные фильтры доступны. Автоподсказки из вопроса отключены rollout-флагом."
                      : "Metadata filters отключены rollout-флагом; поля ниже недоступны и не будут отправлены в /api/chat."}
                  </SheetDescription>
                </SheetHeader>

                <div className="mt-6 space-y-5">
                  {!metadataFiltersEnabled ? (
                    <div className="rounded-[18px] border border-border bg-field px-4 py-3 text-sm leading-6 text-muted-foreground">
                      metadata-filters-v1 выключен, поэтому /api/chat получит запрос без retrievalFilters.
                    </div>
                  ) : !queryHintsEnabled ? (
                    <div className="rounded-[18px] border border-border bg-field px-4 py-3 text-sm leading-6 text-muted-foreground">
                      query-hints-v1 выключен, поэтому подсказки из текста вопроса не применяются.
                    </div>
                  ) : null}
                  <div className="space-y-2">
                    <label className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Номер документа</label>
                    <Input
                      disabled={!metadataFiltersEnabled}
                      value={retrievalFilters.documentNumber ?? ""}
                      onChange={(event) => onRetrievalFilterChange("documentNumber", event.target.value || null)}
                    />
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-2">
                      <label className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Дата с</label>
                      <Input
                        disabled={!metadataFiltersEnabled}
                        type="date"
                        value={retrievalFilters.documentDateFrom ?? ""}
                        onChange={(event) => onRetrievalFilterChange("documentDateFrom", event.target.value || null)}
                      />
                    </div>
                    <div className="space-y-2">
                      <label className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Дата по</label>
                      <Input
                        disabled={!metadataFiltersEnabled}
                        type="date"
                        value={retrievalFilters.documentDateTo ?? ""}
                        onChange={(event) => onRetrievalFilterChange("documentDateTo", event.target.value || null)}
                      />
                    </div>
                  </div>
                  {(["department", "project", "counterparty", "businessStatus", "language"] as RetrievalFilterKey[]).map((key) => (
                    <div className="space-y-2" key={key}>
                      <label className="text-xs uppercase tracking-[0.14em] text-muted-foreground">{retrievalFilterLabels[key]}</label>
                      <Input
                        disabled={!metadataFiltersEnabled}
                        value={(retrievalFilters[key] as string | null | undefined) ?? ""}
                        onChange={(event) => onRetrievalFilterChange(key, event.target.value || null)}
                      />
                    </div>
                  ))}
                  <div className="space-y-2">
                    <label className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Теги / ключевые слова</label>
                    <Input
                      disabled={!metadataFiltersEnabled}
                      value={(retrievalFilters.tags ?? []).join(", ")}
                      onChange={(event) => onRetrievalFilterChange("tags", parseTagsInput(event.target.value))}
                    />
                  </div>
                </div>
              </SheetContent>
            </Sheet>
          </>
        }
        description="RAG-режим теперь показывает не только ответ, но и instruction trace, knowledge scope, retrieval trace и конкретные чанки, из которых он был собран."
        eyebrow="RAG Studio"
        results={
          <div className="space-y-5">
            <SectionIntro
              badge={response ? response.model : "Awaiting query"}
              badgeVariant={response ? "default" : "secondary"}
              description="После запроса здесь появляются ответ модели, использованные документы, чанки, confidence и реально применённый стек инструкций."
              eyebrow="RAG Response"
              title="Ответ по материалам"
            />

            {response ? (
              <>
                <article className="surface-subtle space-y-4 rounded-[24px] p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                      <SearchCheck className="h-4 w-4 text-primary" />
                      Ответ модели
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="secondary">{answerModeLabels[response.answerModeApplied ?? answerMode]}</Badge>
                      <Badge
                        variant={
                          retrievalTrace.supportVerdict === "sufficient"
                            ? "success"
                            : retrievalTrace.supportVerdict === "weak"
                              ? "warning"
                              : "outline"
                        }
                      >
                        {supportVerdictLabels[retrievalTrace.supportVerdict]}
                      </Badge>
                      <Badge variant="outline">{formatDate(response.createdAt)}</Badge>
                    </div>
                  </div>
                  <Separator />
                  <p className="text-sm leading-7 text-foreground">{response.answer}</p>
                </article>

                <article className="surface-subtle space-y-4 rounded-[24px] p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                      <Files className="h-4 w-4 text-primary" />
                      Откуда отвечала система
                    </div>
                    <Badge variant="secondary">{response.sources.length} chunk(s)</Badge>
                  </div>

                  <div className="rounded-[22px] border border-field-border bg-field px-4 py-4 text-sm leading-6 text-foreground">
                    <p>Scope: {resolvedScope.presets.length > 0 ? resolvedScope.presets.map((preset) => preset.name).join(", ") : "без preset'ов"}</p>
                    <p>
                      Фасеты: {resolvedScope.documentClasses.length > 0
                        ? resolvedScope.documentClasses.map((item) => knowledgeDocumentClassLabels[item]).join(", ")
                        : "без ограничения по классам"}
                    </p>
                    <p>Tags: {resolvedScope.tags.length > 0 ? resolvedScope.tags.join(", ") : "не заданы"}</p>
                    <p>RAG-проект: {normalizedActiveRagProjectName || resolvedScope.workspaceKey || "не задан"}</p>
                    <p>workspaceKey: {(resolvedScope.workspaceKey ?? normalizedActiveRagProjectKey) || "не задан"}</p>
                    <p>Today uploads: {resolvedScope.uploadedTodayOnly ? "да" : "нет"}</p>
                  </div>

                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                      scoped ready: {retrievalTrace.scopedReadyMaterials}
                    </div>
                    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                      semantic candidates: {retrievalTrace.semanticCandidates}
                    </div>
                    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                      lexical candidates: {retrievalTrace.lexicalCandidates}
                    </div>
                    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                      final chunks: {retrievalTrace.finalChunks}
                    </div>
                    <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                      support: {supportVerdictLabels[retrievalTrace.supportVerdict]}
                    </div>
                  </div>

                  {retrievalDebug ? (
                    <div className="space-y-3 rounded-[22px] border border-border bg-surface-subtle/70 px-4 py-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-semibold text-foreground">Retrieval debug</p>
                        <Badge variant="outline">{retrievalDebug.relevanceProfile}</Badge>
                      </div>
                      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
                          semantic: {retrievalDebug.semanticCandidateCount}
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
                          lexical: {retrievalDebug.lexicalCandidateCount}
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
                          rerank pool: {retrievalDebug.rerankCandidateCount}
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
                          final chunks: {retrievalDebug.finalChunkCount}
                        </div>
                      </div>
                      <div className="grid gap-4 lg:grid-cols-2">
                        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
                          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Effective filters</p>
                          <p className="mt-2 text-sm leading-6 text-foreground">
                            {effectiveFilterLines.length > 0 ? effectiveFilterLines.join(" · ") : "No effective filters"}
                          </p>
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
                          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Query hints</p>
                          <p className="mt-2 text-sm leading-6 text-foreground">
                            {queryHintLines.length > 0 ? queryHintLines.join(" · ") : "No extracted hints"}
                          </p>
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
                          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Applied capabilities</p>
                          <p className="mt-2 text-sm leading-6 text-foreground">
                            {retrievalDebug.appliedCapabilities.length > 0
                              ? retrievalDebug.appliedCapabilities.join(" · ")
                              : "No rollout capabilities applied"}
                          </p>
                        </div>
                        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
                          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Active rollout flags</p>
                          <p className="mt-2 text-sm leading-6 text-foreground">
                            {[
                              `metadata=${retrievalDebug.activeRolloutFlags.metadataV1 ? "on" : "off"}`,
                              `structured=${retrievalDebug.activeRolloutFlags.structuredV1 ? "on" : "off"}`,
                              `filters=${retrievalDebug.activeRolloutFlags.metadataFiltersV1 ? "on" : "off"}`,
                              `search=${retrievalDebug.activeRolloutFlags.searchApiV1 ? "on" : "off"}`,
                              `reranker=${retrievalDebug.activeRolloutFlags.rerankerV1 ? "on" : "off"}`,
                              `hints=${retrievalDebug.activeRolloutFlags.queryHintsV1 ? "on" : "off"}`,
                            ].join(" · ")}
                          </p>
                        </div>
                      </div>
                    </div>
                  ) : null}

                  {response.sources.length === 0 ? (
                    <EmptyState
                      description="Уточни вопрос, сузь или расширь корпус и попробуй другой режим ответа."
                      icon={BookOpenText}
                      title="Контекст не найден"
                    />
                  ) : (
                    <div className="space-y-3">
                      {response.sources.map((source) => (
                        <article
                          className="space-y-3 rounded-[22px] border border-field-border bg-field p-4"
                          key={`${source.materialId}-${source.chunkId}-${source.chunkIndex ?? "na"}-${source.score}`}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <strong className="text-sm font-semibold text-foreground">{source.title}</strong>
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant="default">score {source.score}</Badge>
                              <Badge variant="secondary">confidence {source.confidence}</Badge>
                              {source.ocrUsed ? <Badge variant="warning">OCR</Badge> : null}
                            </div>
                          </div>
                          <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                            chunk {source.chunkIndex ?? "n/a"}
                            {source.page ? ` · page ${source.page}` : ""}
                            {source.extractor ? ` · ${source.extractor}` : ""}
                            {source.semanticDistance !== undefined && source.semanticDistance !== null
                              ? ` · semantic ${source.semanticDistance}`
                              : ""}
                            {source.lexicalScore !== undefined && source.lexicalScore !== null
                              ? ` · lexical ${source.lexicalScore}`
                              : ""}
                          </p>
                          <p className="text-sm leading-6 text-foreground">{source.excerpt}</p>
                          <div className="flex flex-wrap items-center justify-between gap-3">
                            <p className="text-sm leading-6 text-muted-foreground">
                              matched: {source.matchedTerms.length > 0 ? source.matchedTerms.join(", ") : "нет явных терминов"}
                            </p>
                            <Button
                              size="sm"
                              type="button"
                              variant="outline"
                              onClick={() => void openSource(source.materialId, source.openSourceUrl)}
                            >
                              <ExternalLink className="h-4 w-4" />
                              Открыть источник
                            </Button>
                          </div>
                          {source.scoreBreakdown ? (
                            <details className="rounded-[18px] border border-border bg-background/70 px-4 py-3">
                              <summary className="cursor-pointer text-sm font-medium text-foreground">
                                Score breakdown
                              </summary>
                              <div className="mt-3 grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                                <div>chunkType: {source.chunkType ?? "NARRATIVE"}</div>
                                <div>baseRrf: {source.scoreBreakdown.baseRrf}</div>
                                <div>semantic bonus: {source.scoreBreakdown.semanticRankBonus}</div>
                                <div>lexical bonus: {source.scoreBreakdown.lexicalRankBonus}</div>
                                <div>identifier bonus: {source.scoreBreakdown.identifierBonus}</div>
                                <div>heading bonus: {source.scoreBreakdown.headingBonus}</div>
                                <div>metadata bonus: {source.scoreBreakdown.metadataBonus}</div>
                                <div>appendix penalty: {source.scoreBreakdown.appendixPenalty}</div>
                                <div>boilerplate penalty: {source.scoreBreakdown.boilerplatePenalty}</div>
                                <div>low-confidence penalty: {source.scoreBreakdown.lowConfidencePenalty}</div>
                                <div>final score: {source.scoreBreakdown.finalScore}</div>
                              </div>
                            </details>
                          ) : null}
                        </article>
                      ))}
                    </div>
                  )}
                </article>

                <AppliedInstructionList
                  appliedInstructions={response.appliedInstructions}
                  instructionTrace={response.instructionTrace}
                />

                <ChatAuditPanel
                  currentAuditRunId={response.auditRunId}
                  currentInstructionTrace={response.instructionTrace}
                  currentKnowledgeScopeResolved={response.knowledgeScopeResolved}
                  currentRetrievalTrace={response.retrievalTrace}
                  error={chatRunsError}
                  runs={chatRuns}
                  selectedRun={selectedChatRun}
                  onLoadRun={onLoadChatRun}
                />
              </>
            ) : (
              <EmptyState
                description="После первого запроса здесь появятся ответ, retrieval trace, источники и история аудита."
                icon={Sparkles}
                title="Ответ по материалам пока пустой"
              />
            )}
          </div>
        }
        title="Запрос по материалам"
      />

      <Dialog open={openedMaterial !== null || isLoadingMaterial || materialError !== null} onOpenChange={(open) => {
        if (!open) {
          setOpenedMaterial(null);
          setOpenedSourceTarget(null);
          setMaterialError(null);
        }
      }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{openedMaterial?.title ?? "Источник"}</DialogTitle>
            <DialogDescription>
              {isLoadingMaterial
                ? "Загружаем материал и его чанки."
                : materialError ?? "Показываем активную версию материала и сразу прокручиваем к выбранному chunk."}
            </DialogDescription>
          </DialogHeader>

          {openedMaterial ? (
            <div className="space-y-4">
              <div className="rounded-[22px] border border-field-border bg-field px-4 py-4 text-sm leading-6 text-foreground">
                <p>Файл: {openedMaterial.originalFileName ?? openedMaterial.sourceType}</p>
                <p>Статус: {openedMaterial.status}</p>
                <p>Chunks: {openedMaterial.chunks.length}</p>
                <p>
                  Jump target: {openedSourceTarget?.chunkIndex ?? "n/a"}
                  {openedSourceTarget?.page ? ` · page ${openedSourceTarget.page}` : ""}
                </p>
              </div>

              <article className="max-h-[52vh] space-y-3 overflow-y-auto rounded-[22px] border border-field-border bg-field px-4 py-4">
                <p className="text-sm leading-7 text-foreground">{openedMaterial.content}</p>
                <Separator />
                <div className="space-y-3">
                  {openedMaterial.chunks.map((chunk) => {
                    const isHighlighted = (openedSourceTarget?.chunkId && chunk.chunkId === openedSourceTarget.chunkId)
                      || (openedSourceTarget?.chunkIndex !== null
                        && openedSourceTarget?.chunkIndex !== undefined
                        && chunk.chunkIndex === openedSourceTarget.chunkIndex);
                    return (
                    <div
                      className={isHighlighted
                        ? "rounded-[18px] border border-primary bg-primary/10 px-4 py-3 shadow-[0_0_0_1px_rgba(0,0,0,0.02)]"
                        : "rounded-[18px] border border-border bg-background px-4 py-3"}
                      key={chunk.chunkId}
                      ref={isHighlighted ? highlightedChunkRef : null}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">
                          chunk {chunk.chunkIndex}
                          {chunk.page ? ` · page ${chunk.page}` : ""}
                          {chunk.extractor ? ` · ${chunk.extractor}` : ""}
                        </p>
                        {isHighlighted ? <Badge variant="default">retrieval target</Badge> : null}
                      </div>
                      <p className="mt-2 text-sm leading-6 text-foreground">{chunk.text}</p>
                    </div>
                    );
                  })}
                </div>
              </article>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}
