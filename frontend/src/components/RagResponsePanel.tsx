import { Files, SearchCheck, Sparkles } from "lucide-react";
import { AppliedInstructionList } from "@/components/AppliedInstructionList";
import { ChatAuditPanel } from "@/components/ChatAuditPanel";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { RagSourcesList } from "@/components/RagSourcesList";
import { RetrievalDebugPanel } from "@/components/RetrievalDebugPanel";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import type {
  AnswerMode,
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatExecutionResponse,
  RetrievalFilters,
  RetrievalQueryHints,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  answerModeLabels,
  knowledgeScopeResolvedWithDefaults,
  retrievalTraceWithDefaults,
  supportVerdictLabels,
} from "@/utils/workbenchPresentation";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";
import {
  buildQueryHintLines,
  buildRetrievalFilterLines,
} from "@/components/ragChatPresentation";

type RagResponsePanelProps = {
  response: ChatExecutionResponse | null;
  answerMode: AnswerMode;
  effectiveRetrievalFilters: RetrievalFilters;
  queryHints: RetrievalQueryHints;
  activeRagProjectKey: string;
  activeRagProjectName: string;
  chatRuns: ChatAuditRunSummary[];
  selectedChatRun: ChatAuditRunDetail | null;
  chatRunsError: string | null;
  onLoadChatRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
  onOpenSource: (materialId: string, openSourceUrl?: string | null) => void;
};

export function RagResponsePanel({
  response,
  answerMode,
  effectiveRetrievalFilters,
  queryHints,
  activeRagProjectKey,
  activeRagProjectName,
  chatRuns,
  selectedChatRun,
  chatRunsError,
  onLoadChatRun,
  onOpenSource,
}: RagResponsePanelProps) {
  const resolvedScope = knowledgeScopeResolvedWithDefaults(response?.knowledgeScopeResolved);
  const retrievalTrace = retrievalTraceWithDefaults(response?.retrievalTrace);
  const retrievalDebug = response?.retrievalDebug ?? null;
  const effectiveFilterLines = buildRetrievalFilterLines(effectiveRetrievalFilters);
  const queryHintLines = buildQueryHintLines(queryHints);

  return (
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
              <p>Пресеты: {resolvedScope.presets.length > 0 ? resolvedScope.presets.map((preset) => preset.name).join(", ") : "не выбраны"}</p>
              <p>
                Фасеты: {resolvedScope.facets.length > 0
                  ? resolvedScope.facets.map((facet) => facet.name).join(", ")
                  : "не выбраны"}
              </p>
              <p>
                Типы документов: {resolvedScope.documentTypes.length > 0
                  ? resolvedScope.documentTypes.map((item) => documentTypeLabels[item] ?? item).join(", ")
                  : "не ограничены"}
              </p>
              <p>
                Статусы документов: {resolvedScope.documentStatuses.length > 0
                  ? resolvedScope.documentStatuses.map((item) => documentStatusLabels[item] ?? item).join(", ")
                  : "по умолчанию действующие"}
              </p>
              <p>Проекты: {resolvedScope.projectKeys.length > 0 ? resolvedScope.projectKeys.join(", ") : "не ограничены"}</p>
              <p>
                Языки: {resolvedScope.languageCodes.length > 0
                  ? resolvedScope.languageCodes.map((item) => materialLanguageCodeLabels[item] ?? item).join(", ")
                  : "не ограничены"}
              </p>
              <p>Номер документа: {resolvedScope.documentNumber || "не задан"}</p>
              <p>Период “действует с”: {resolvedScope.periodStartFrom || "любая дата"} - {resolvedScope.periodStartTo || "любая дата"}</p>
              <p>Период “действует по”: {resolvedScope.periodEndFrom || "любая дата"} - {resolvedScope.periodEndTo || "любая дата"}</p>
              <p>Теги: {resolvedScope.tags.length > 0 ? resolvedScope.tags.join(", ") : "не заданы"}</p>
              <p>RAG-проект: {activeRagProjectName || resolvedScope.workspaceKey || "не задан"}</p>
              <p>workspaceKey: {(resolvedScope.workspaceKey ?? activeRagProjectKey) || "не задан"}</p>
              <p>Только сегодняшние загрузки: {resolvedScope.uploadedTodayOnly ? "да" : "нет"}</p>
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

            <RetrievalDebugPanel
              effectiveFilterLines={effectiveFilterLines}
              queryHintLines={queryHintLines}
              retrievalDebug={retrievalDebug}
            />

            <RagSourcesList sources={response.sources} onOpenSource={onOpenSource} />
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
  );
}
